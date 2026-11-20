package io.github.noahhhx.mimos.kitchen

import io.github.noahhhx.mimos.api.apis.PlanningApi
import io.github.noahhhx.mimos.api.apis.PluginsApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.apis.ShoppingListsApi
import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.api.models.MealPlanEntryInput
import io.github.noahhhx.mimos.api.models.PlanSuggestion
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.api.models.ShoppingList
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.plan.mondayOf
import io.github.noahhhx.mimos.plan.weekdayName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** One block of the home: each loads and fails on its own, as on the web. */
sealed interface Block<out T> {
    data object Loading : Block<Nothing>

    data object Failed : Block<Nothing>

    data class Loaded<T>(val value: T) : Block<T>
}

/** The week the home reads: its plan, and the plugins' suggestions for it. */
data class KitchenWeek(
    val monday: LocalDate,
    val entries: List<MealPlanEntry>,
    val suggestions: List<PlanSuggestion>,
)

/** What adding a thought did: [added] or not, in words. */
data class ThoughtNotice(val text: String, val added: Boolean)

/**
 * The Kitchen tab, as the web's `/app` page: tonight's meal, the week's dinners, what is left to
 * buy, and a plugin's thought for an open evening. Opening it reads the time and the week again,
 * since the plan and the list change elsewhere; loading changes nothing. [now] is the phone's local
 * time, and [onPlanned] is told when the home plans a meal, so the Plan tab reloads.
 */
class KitchenModel(
    private val scope: CoroutineScope,
    private val planning: () -> PlanningApi,
    private val plugins: () -> PluginsApi,
    private val recipes: () -> RecipesApi,
    private val shopping: () -> ShoppingListsApi,
    private val now: () -> LocalDateTime,
    private val onPlanned: () -> Unit,
) {
    private val mutableTime = MutableStateFlow(now())

    /** When the home was last opened: the greeting, today, and the week follow from it. */
    val time: StateFlow<LocalDateTime> = mutableTime.asStateFlow()

    private val mutableWeek = MutableStateFlow<Block<KitchenWeek>>(Block.Loading)
    val week: StateFlow<Block<KitchenWeek>> = mutableWeek.asStateFlow()

    private val mutableRecipe = MutableStateFlow<Block<RecipeDetail>>(Block.Loading)

    /** Tonight's recipe, for its description and facts; the screen checks it is tonight's. */
    val recipe: StateFlow<Block<RecipeDetail>> = mutableRecipe.asStateFlow()

    private val mutableList = MutableStateFlow<Block<ShoppingList?>>(Block.Loading)

    /** The week's shopping list; a loaded null is a list never generated. */
    val list: StateFlow<Block<ShoppingList?>> = mutableList.asStateFlow()

    private val mutableAdding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = mutableAdding.asStateFlow()

    private val mutableNotice = MutableStateFlow<ThoughtNotice?>(null)

    /** What adding a thought did, until the home is opened again. */
    val notice: StateFlow<ThoughtNotice?> = mutableNotice.asStateFlow()

    private var loadJob: Job? = null
    private var addJob: Job? = null

    val today: LocalDate
        get() = time.value.date

    val monday: LocalDate
        get() = mondayOf(today)

    /**
     * Reads everything again. A block keeps what it showed for the same week meanwhile, so coming
     * back to the tab does not flash "Loading…".
     */
    fun open() {
        val previous = monday
        mutableTime.value = now()
        mutableNotice.value = null
        if (monday != previous) {
            mutableWeek.value = Block.Loading
            mutableList.value = Block.Loading
        }
        load()
    }

    private fun load() {
        val week = monday
        val today = today
        loadJob?.cancel()
        loadJob = scope.launch {
            coroutineScope {
                launch { loadWeek(week, today) }
                launch { mutableList.value = readList(week) }
            }
        }
    }

    private suspend fun loadWeek(week: LocalDate, today: LocalDate) {
        val read = coroutineScope {
            val plan = async { attempt { planning().getMealPlan(week).body().propertyEntries } }
            // Suggestions are an enhancement (ADR-0006): none, or a failure, hides the thought.
            val suggestions = async {
                attempt { plugins().getPlanSuggestions(week).body().suggestions } ?: emptyList()
            }
            plan.await()?.let { KitchenWeek(week, it, suggestions.await()) }
        }
        mutableWeek.value = read?.let { Block.Loaded(it) } ?: Block.Failed
        val recipeId = read?.let { pickTonight(it.entries, today) }?.entry?.recipeId ?: return
        val shown = (recipe.value as? Block.Loaded)?.value?.id
        if (shown != recipeId) mutableRecipe.value = Block.Loading
        mutableRecipe.value =
            attempt { recipes().getRecipe(recipeId).body() }?.let { Block.Loaded(it) }
                ?: Block.Failed
    }

    private suspend fun readList(week: LocalDate): Block<ShoppingList?> =
        try {
            Block.Loaded(shopping().getShoppingList(week).body())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: ApiProblem) {
            if (problem.status == 404) Block.Loaded(null) else Block.Failed
        } catch (_: Exception) {
            Block.Failed
        }

    /** Plans the thought's one dinner through the plan-entry endpoint, as the web does. */
    fun addThought(thought: Thought) {
        if (adding.value) return
        val entry = thought.entry
        val week = monday
        mutableAdding.value = true
        addJob = scope.launch {
            val added = attempt {
                planning()
                    .addMealPlanEntry(
                        week,
                        MealPlanEntryInput(
                            entry.date,
                            entry.mealType,
                            entry.servings,
                            recipeId = entry.recipeId,
                        ),
                    )
                    .body()
            }
            mutableAdding.value = false
            mutableNotice.value =
                if (added != null) {
                    ThoughtNotice("Added ${entry.recipeTitle} to ${weekdayName(entry.date)}.", true)
                } else ThoughtNotice("Could not add that suggestion.", false)
            if (added != null) onPlanned()
            if (monday == week) load()
        }
    }

    private suspend fun <T> attempt(read: suspend () -> T): T? =
        try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    /** Forgets everything, for when the person signs out or the household changes. */
    fun reset() {
        loadJob?.cancel()
        addJob?.cancel()
        loadJob = null
        addJob = null
        mutableTime.value = now()
        mutableWeek.value = Block.Loading
        mutableRecipe.value = Block.Loading
        mutableList.value = Block.Loading
        mutableAdding.value = false
        mutableNotice.value = null
    }
}
