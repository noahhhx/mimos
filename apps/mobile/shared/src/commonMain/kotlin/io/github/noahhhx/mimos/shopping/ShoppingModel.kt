package io.github.noahhhx.mimos.shopping

import io.github.noahhhx.mimos.api.apis.ShoppingListsApi
import io.github.noahhhx.mimos.api.models.ShoppingList
import io.github.noahhhx.mimos.api.models.ShoppingListItem
import io.github.noahhhx.mimos.api.models.ShoppingListItemPatch
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.plan.mondayOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

sealed interface ListState {
    data object Loading : ListState

    /** The week has no list yet: one is generated from its plan. */
    data object NotGenerated : ListState

    data class Loaded(val list: ShoppingList) : ListState

    data object Failed : ListState
}

/**
 * A tick the server has not confirmed: on its way, or [unsent] after it failed, until the person
 * retries it. It shows on the list meanwhile, so a shop with no signal does not undo a tap.
 */
data class Tick(val monday: LocalDate, val checked: Boolean, val unsent: Boolean)

/**
 * The Shopping tab, as the web's Shopping list page (ADR-0027): a week's list by its Monday,
 * generated from the plan, ticked off in the shop, with items added by hand. [today] is the phone's
 * date.
 */
class ShoppingModel(
    private val scope: CoroutineScope,
    private val api: () -> ShoppingListsApi,
    private val today: () -> LocalDate,
) {
    private val mutableMonday = MutableStateFlow(mondayOf(today()))
    val monday: StateFlow<LocalDate> = mutableMonday.asStateFlow()

    private val mutableState = MutableStateFlow<ListState>(ListState.Loading)
    val state: StateFlow<ListState> = mutableState.asStateFlow()

    private val mutableTicks = MutableStateFlow<Map<String, Tick>>(emptyMap())
    val ticks: StateFlow<Map<String, Tick>> = mutableTicks.asStateFlow()

    private val mutableFields = MutableStateFlow(AddItemFields())
    val fields: StateFlow<AddItemFields> = mutableFields.asStateFlow()

    private val mutableGenerating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = mutableGenerating.asStateFlow()

    private val mutableAdding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = mutableAdding.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    private var loadJob: Job? = null
    private val jobs = mutableListOf<Job>()

    /** One tick on the wire at a time, so an item's last tap is the one the server keeps. */
    private val sending = Mutex()

    fun ensureLoaded() {
        if (state.value !is ListState.Loaded && loadJob?.isActive != true) load()
    }

    fun retry() = load()

    fun previousWeek() = goToWeek(monday.value.plus(DatePeriod(days = -7)))

    fun nextWeek() = goToWeek(monday.value.plus(DatePeriod(days = 7)))

    fun thisWeek() = goToWeek(mondayOf(today()))

    private fun goToWeek(next: LocalDate) {
        if (next == monday.value) return
        mutableMonday.value = next
        mutableError.value = null
        load()
    }

    /** A newer load replaces the one before it, so a slow answer never lands over a newer week. */
    private fun load() {
        val week = monday.value
        loadJob?.cancel()
        mutableState.value = ListState.Loading
        loadJob = scope.launch {
            val next =
                try {
                    ListState.Loaded(api().getShoppingList(week).body())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (problem: ApiProblem) {
                    if (problem.status == 404) ListState.NotGenerated else ListState.Failed
                } catch (_: Exception) {
                    ListState.Failed
                }
            if (next is ListState.Loaded) forgetGone(next.list)
            mutableState.value = next
        }
    }

    /** Builds the week's list from its plan again, keeping ticks and added items (ADR-0027). */
    fun generate() {
        if (generating.value) return
        val week = monday.value
        mutableGenerating.value = true
        launch {
            try {
                val list = api().generateShoppingList(week).body()
                if (monday.value == week) {
                    forgetGone(list)
                    mutableState.value = ListState.Loaded(list)
                    mutableError.value = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = "Could not generate the shopping list."
            } finally {
                mutableGenerating.value = false
            }
        }
    }

    /** What the list shows for [item]: the person's last tap until the server has it. */
    private fun checked(item: ShoppingListItem): Boolean =
        ticks.value[item.id]?.checked ?: item.checked

    /** Ticks [item] or unticks it on screen at once, then sends it. */
    fun toggle(item: ShoppingListItem) = send(item.id, monday.value, !checked(item))

    /** Sends an unsent tick again. */
    fun retryTick(itemId: String) {
        val tick = ticks.value[itemId]?.takeIf { it.unsent } ?: return
        send(itemId, tick.monday, tick.checked)
    }

    private fun send(itemId: String, week: LocalDate, checked: Boolean) {
        val tick = Tick(week, checked, unsent = false)
        mutableTicks.update { it + (itemId to tick) }
        launch {
            val sent = sending.withLock {
                try {
                    api()
                        .updateShoppingListItem(week, itemId, ShoppingListItemPatch(checked))
                        .body()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            // A later tap of the same item owns what the list shows now.
            if (ticks.value[itemId] != tick) return@launch
            if (sent == null) {
                mutableTicks.update { it + (itemId to tick.copy(unsent = true)) }
            } else {
                mutableState.update { state -> state.withItem(week, sent) }
                mutableTicks.update { it - itemId }
            }
        }
    }

    fun edit(change: (AddItemFields) -> AddItemFields) = mutableFields.update(change)

    fun add() {
        if (adding.value) return
        val input =
            when (val parsed = addedItem(fields.value)) {
                is AddedItem.Invalid -> {
                    mutableError.value = parsed.error
                    return
                }
                is AddedItem.Valid -> parsed.input
            }
        val week = monday.value
        mutableAdding.value = true
        launch {
            try {
                api().addShoppingListItem(week, input)
                mutableFields.value = AddItemFields()
                mutableError.value = null
                // A week with no list gets one from its plan first, so the whole list loads again.
                if (monday.value == week) load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = "Could not add the item."
            } finally {
                mutableAdding.value = false
            }
        }
    }

    /** Takes an added item off the list at once; a failure brings the list back from the server. */
    fun remove(item: ShoppingListItem) {
        if (!item.added) return
        val week = monday.value
        mutableState.update { state ->
            if (state is ListState.Loaded && state.list.startDate == week)
                ListState.Loaded(state.list.copy(items = state.list.items - item))
            else state
        }
        mutableTicks.update { it - item.id }
        launch {
            try {
                api().removeShoppingListItem(week, item.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = "Could not remove the item. Refreshing."
                if (monday.value == week) load()
            }
        }
    }

    /** Drops ticks for items a week's list no longer has, such as one removed elsewhere. */
    private fun forgetGone(list: ShoppingList) {
        val ids = list.items.mapTo(HashSet()) { it.id }
        mutableTicks.update { ticks ->
            ticks.filter { (id, tick) -> tick.monday != list.startDate || id in ids }
        }
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        jobs.removeAll { !it.isActive }
        jobs += scope.launch(block = block)
    }

    /** Forgets everything, for when the person signs out or the household changes. */
    fun reset() {
        loadJob?.cancel()
        loadJob = null
        jobs.forEach { it.cancel() }
        jobs.clear()
        mutableMonday.value = mondayOf(today())
        mutableState.value = ListState.Loading
        mutableTicks.value = emptyMap()
        mutableFields.value = AddItemFields()
        mutableGenerating.value = false
        mutableAdding.value = false
        mutableError.value = null
    }
}

private fun ListState.withItem(week: LocalDate, item: ShoppingListItem): ListState =
    if (this is ListState.Loaded && list.startDate == week)
        ListState.Loaded(list.copy(items = list.items.map { if (it.id == item.id) item else it }))
    else this
