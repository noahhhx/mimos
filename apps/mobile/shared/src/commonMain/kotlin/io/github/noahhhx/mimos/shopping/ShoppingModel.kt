package io.github.noahhhx.mimos.shopping

import io.github.noahhhx.mimos.api.apis.ShoppingListsApi
import io.github.noahhhx.mimos.api.models.ShoppingList
import io.github.noahhhx.mimos.api.models.ShoppingListItem
import io.github.noahhhx.mimos.api.models.ShoppingListItemPatch
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.client.NetworkWatcher
import io.github.noahhhx.mimos.plan.mondayOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

sealed interface ListState {
    data object Loading : ListState

    /** The week has no list yet: one is generated from its plan. */
    data object NotGenerated : ListState

    data class Loaded(val list: ShoppingList, val copy: ListCopy = ListCopy.Server) : ListState

    data object Failed : ListState
}

/** Where a shown list came from (ADR-0034). */
enum class ListCopy {
    Server,
    /** Kept on the phone when it last loaded, because the server could not be reached now. */
    Stored,
}

enum class TickStatus {
    /** The sending loop takes it next. */
    Queued,
    Sending,
    /** Waits for a network, a session, or "Send now". */
    Unsent,
}

/**
 * A tick the server has not confirmed. It shows on the list meanwhile, and is kept on the phone, so
 * a shop with no signal, or the app being killed there, does not undo a tap (ADR-0034).
 */
data class Tick(val monday: LocalDate, val checked: Boolean, val status: TickStatus)

private sealed interface Delivery {
    data class Sent(val item: ShoppingListItem) : Delivery

    /** The server has no such item: the list was regenerated, or the item removed. */
    data object Gone : Delivery

    data object Failed : Delivery
}

/**
 * The Shopping tab, as the web's Shopping list page (ADR-0027): a week's list by its Monday,
 * generated from the plan, ticked off in the shop, with items added by hand. [today] is the phone's
 * date. Lists and unconfirmed ticks are kept in [store], so ticking works offline (ADR-0034).
 */
class ShoppingModel(
    private val scope: CoroutineScope,
    private val api: () -> ShoppingListsApi,
    private val today: () -> LocalDate,
    private val store: ShoppingStore,
    network: NetworkWatcher,
) {
    val online: StateFlow<Boolean> = network.online

    private val mutableMonday = MutableStateFlow(mondayOf(today()))
    val monday: StateFlow<LocalDate> = mutableMonday.asStateFlow()

    private val mutableState = MutableStateFlow<ListState>(ListState.Loading)
    val state: StateFlow<ListState> = mutableState.asStateFlow()

    /** Insertion-ordered, one per item: an item's last tap is the one that waits. */
    private val mutableTicks = MutableStateFlow(decodeTicks(store.load(ShoppingStore.Entry.Ticks)))
    val ticks: StateFlow<Map<String, Tick>> = mutableTicks.asStateFlow()

    /** The weeks last loaded, oldest first, at most [STORED_WEEKS]. */
    private var stored: Map<LocalDate, ShoppingList> =
        decodeLists(store.load(ShoppingStore.Entry.Lists))

    private val mutableFields = MutableStateFlow(AddItemFields())
    val fields: StateFlow<AddItemFields> = mutableFields.asStateFlow()

    private val mutableGenerating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = mutableGenerating.asStateFlow()

    private val mutableAdding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = mutableAdding.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    private var loadJob: Job? = null
    /**
     * Items the server confirmed, by id, while the week loaded, which the load's answer may
     * predate.
     */
    private val confirmedDuringLoad = mutableMapOf<String, ShoppingListItem>()
    private var sendJob: Job? = null
    private val jobs = mutableListOf<Job>()

    fun ensureLoaded() {
        val shown = state.value as? ListState.Loaded
        if (shown?.copy != ListCopy.Server && loadJob?.isActive != true) load()
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

    /**
     * A newer load replaces the one before it, so a slow answer never lands over a newer week. The
     * week's list stays on screen while it loads again, so a network coming back in the aisle does
     * not blank it.
     */
    private fun load() {
        val week = monday.value
        loadJob?.cancel()
        confirmedDuringLoad.clear()
        if ((state.value as? ListState.Loaded)?.list?.startDate != week) {
            mutableState.value = ListState.Loading
        }
        loadJob = scope.launch {
            val next =
                try {
                    val list = api().getShoppingList(week).body().withConfirmed()
                    ListState.Loaded(list.also(::loadedFromServer))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (problem: ApiProblem) {
                    if (problem.status == 404) {
                        forgetStored(week)
                        ListState.NotGenerated
                    } else storedOrFailed(week)
                } catch (_: Exception) {
                    storedOrFailed(week)
                }
            mutableState.value = next
            if (next is ListState.Loaded && next.copy == ListCopy.Server && online.value) {
                queueAndSend()
            }
        }
    }

    /** Lays the ticks confirmed since this list was asked for over it, then forgets them. */
    private fun ShoppingList.withConfirmed(): ShoppingList {
        val newer = items.map { confirmedDuringLoad[it.id] ?: it }
        confirmedDuringLoad.clear()
        return copy(items = newer)
    }

    private fun storedOrFailed(week: LocalDate): ListState =
        stored[week]?.let { ListState.Loaded(it, ListCopy.Stored) } ?: ListState.Failed

    /** Builds the week's list from its plan again, keeping ticks and added items (ADR-0027). */
    fun generate() {
        if (generating.value) return
        val week = monday.value
        mutableGenerating.value = true
        launch {
            try {
                val list = api().generateShoppingList(week).body()
                loadedFromServer(list)
                if (monday.value == week) {
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

    /**
     * Ticks [item] or unticks it on screen at once, and sends it unless the phone has no network.
     */
    fun toggle(item: ShoppingListItem) {
        val status = if (online.value) TickStatus.Queued else TickStatus.Unsent
        updateTicks { it + (item.id to Tick(monday.value, !checked(item), status)) }
        if (status == TickStatus.Queued) send()
    }

    /** For when the session is renewed or the network returns: [sendNow], unless still offline. */
    fun reconnected() {
        if (online.value) sendNow()
    }

    /**
     * Tries every tick that waits, whatever the phone says of its network, and loads the week again
     * when it shows a stored copy or nothing.
     */
    fun sendNow() {
        queueAndSend()
        val shown = state.value
        if (shown == ListState.Failed || (shown as? ListState.Loaded)?.copy == ListCopy.Stored) {
            load()
        }
    }

    private fun queueAndSend() {
        updateTicks { ticks ->
            ticks.mapValues { (_, tick) ->
                if (tick.status == TickStatus.Unsent) tick.copy(status = TickStatus.Queued)
                else tick
            }
        }
        send()
    }

    /**
     * Sends queued ticks one at a time, in the order they were first tapped, so an item's last tap
     * is the one the server keeps. The first failure ends the round, and what is left waits for the
     * next. The loop runs on one thread and takes every queued tick before it ends, so a tick
     * queued while it runs is never left behind.
     */
    private fun send() {
        if (sendJob?.isActive == true) return
        sendJob = scope.launch {
            while (true) {
                val (itemId, queued) =
                    ticks.value.entries.firstOrNull { it.value.status == TickStatus.Queued }
                        ?: break
                val tick = queued.copy(status = TickStatus.Sending)
                updateTicks { it + (itemId to tick) }
                when (val delivery = deliver(itemId, tick)) {
                    is Delivery.Sent -> {
                        if (loadJob?.isActive == true && tick.monday == monday.value) {
                            confirmedDuringLoad[itemId] = delivery.item
                        }
                        change(tick.monday) { list ->
                            list.copy(
                                items =
                                    list.items.map { if (it.id == itemId) delivery.item else it }
                            )
                        }
                        dropTick(itemId, tick)
                    }
                    Delivery.Gone -> {
                        dropTick(itemId, tick)
                        if (monday.value == tick.monday && online.value) load()
                    }
                    Delivery.Failed -> {
                        updateTicks { ticks ->
                            ticks.mapValues { (id, waiting) ->
                                val failed = id == itemId && waiting == tick
                                if (failed || waiting.status == TickStatus.Queued)
                                    waiting.copy(status = TickStatus.Unsent)
                                else waiting
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    private suspend fun deliver(itemId: String, tick: Tick): Delivery =
        try {
            Delivery.Sent(
                api()
                    .updateShoppingListItem(
                        tick.monday,
                        itemId,
                        ShoppingListItemPatch(tick.checked),
                    )
                    .body()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: ApiProblem) {
            if (problem.status == 404) Delivery.Gone else Delivery.Failed
        } catch (_: Exception) {
            Delivery.Failed
        }

    /** A later tap of the same item owns what the list shows now, so only this tick goes. */
    private fun dropTick(itemId: String, tick: Tick) = updateTicks {
        if (it[itemId] == tick) it - itemId else it
    }

    private fun updateTicks(change: (Map<String, Tick>) -> Map<String, Tick>) {
        val next = mutableTicks.updateAndGet(change)
        store.save(ShoppingStore.Entry.Ticks, if (next.isEmpty()) null else encodeTicks(next))
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
        change(week) { list -> list.copy(items = list.items - item) }
        updateTicks { it - item.id }
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

    /** Changes [week]'s list where it shows and where it is kept. */
    private fun change(week: LocalDate, edit: (ShoppingList) -> ShoppingList) {
        stored[week]?.let { keepStored(stored + (week to edit(it))) }
        mutableState.update { state ->
            if (state is ListState.Loaded && state.list.startDate == week)
                state.copy(list = edit(state.list))
            else state
        }
    }

    /**
     * Keeps [list] as its week's latest, and drops ticks for items it no longer has, such as one
     * removed elsewhere.
     */
    private fun loadedFromServer(list: ShoppingList) {
        val week = list.startDate
        val latest = (stored - week + (week to list)).entries.toList().takeLast(STORED_WEEKS)
        keepStored(latest.associate { it.key to it.value })
        val ids = list.items.mapTo(HashSet()) { it.id }
        updateTicks { ticks -> ticks.filter { (id, tick) -> tick.monday != week || id in ids } }
    }

    private fun forgetStored(week: LocalDate) {
        if (week in stored) keepStored(stored - week)
    }

    private fun keepStored(lists: Map<LocalDate, ShoppingList>) {
        stored = lists
        store.save(ShoppingStore.Entry.Lists, if (lists.isEmpty()) null else encodeLists(lists))
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        jobs.removeAll { !it.isActive }
        jobs += scope.launch(block = block)
    }

    /**
     * Forgets everything, the lists and ticks kept on the phone included, for when the person signs
     * out or the household changes.
     */
    fun reset() {
        loadJob?.cancel()
        loadJob = null
        confirmedDuringLoad.clear()
        sendJob?.cancel()
        sendJob = null
        jobs.forEach { it.cancel() }
        jobs.clear()
        mutableMonday.value = mondayOf(today())
        mutableState.value = ListState.Loading
        updateTicks { emptyMap() }
        keepStored(emptyMap())
        mutableFields.value = AddItemFields()
        mutableGenerating.value = false
        mutableAdding.value = false
        mutableError.value = null
    }
}
