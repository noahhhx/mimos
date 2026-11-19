package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.api.apis.HouseholdApi
import io.github.noahhhx.mimos.api.apis.PlanningApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.api.models.MealPlanEntryInput
import io.github.noahhhx.mimos.api.models.MealPlanEntryPatch
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.api.models.Person
import io.github.noahhhx.mimos.api.models.PlannedDayTotal
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.ingredients.CheckedIngredient
import io.github.noahhhx.mimos.ingredients.NewIngredientDraft
import io.github.noahhhx.mimos.ingredients.SavedIngredient
import io.github.noahhhx.mimos.ingredients.checkInput
import io.github.noahhhx.mimos.ingredients.saveIngredient
import io.github.noahhhx.mimos.ingredients.withIngredient
import io.github.noahhhx.mimos.recipes.form.searchCatalog
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Keeps Mine or Everyone on this install, as the web keeps it per browser. */
interface PlanViewStore {
    fun load(): PlanView?

    fun save(view: PlanView)
}

/** Where the Plan tab is: the week, a meal's or a day's inspector, or the picker for a slot. */
sealed interface PlanRoute {
    data object Week : PlanRoute

    data class Meal(val entryId: String) : PlanRoute

    data class Day(val date: LocalDate) : PlanRoute

    data class Pick(val date: LocalDate, val mealType: MealType) : PlanRoute
}

/**
 * A loaded week: its meals, the household's members (none when they failed to load, so the week
 * reads as a household of one's), and the caller's planned totals (null when they failed).
 */
data class PlanWeek(
    val monday: LocalDate,
    val entries: List<MealPlanEntry>,
    val members: List<Person>,
    val totals: List<PlannedDayTotal>?,
) {
    val shared: Boolean
        get() = members.size > 1

    fun shown(view: PlanView): List<MealPlanEntry> =
        if (shared) visibleEntries(entries, view) else entries

    fun totalsShown(view: PlanView): Boolean = totals != null && showsPlannedTotals(shared, view)

    fun total(date: LocalDate): PlannedDayTotal? = totals?.find { it.date == date }
}

sealed interface WeekState {
    data object Loading : WeekState

    data class Loaded(val week: PlanWeek) : WeekState

    data class Failed(val message: String) : WeekState
}

/**
 * The picker's search: recipes (the household's, then the library's) and catalog foods, and the new
 * ingredient being added when the catalog lacks one.
 */
data class PickerState(
    val query: String = "",
    val recipes: List<RecipeSummary>? = null,
    val foods: List<CatalogIngredient> = emptyList(),
    val adding: NewIngredientDraft? = null,
) {
    /** "+ Add" is offered once the search has answered and found no food by that name. */
    val offersNewIngredient: Boolean
        get() = recipes != null && adding == null && offersNewIngredient(query, foods)
}

/**
 * The Plan tab, as the web's plan page (ADR-0028, ADR-0019, ADR-0026): a week by its Monday, the
 * caller's meals or everyone's, a meal's or a day's inspector, and the picker that plans a recipe
 * or a food. Every change reloads the week, as the web does. [today] is the phone's date.
 */
class PlanModel(
    private val scope: CoroutineScope,
    private val viewStore: PlanViewStore,
    private val planning: () -> PlanningApi,
    private val household: () -> HouseholdApi,
    private val recipes: () -> RecipesApi,
    private val today: () -> LocalDate,
    private val searchDelayMillis: Long = SEARCH_DELAY_MILLIS,
) {
    private val mutableMonday = MutableStateFlow(mondayOf(today()))
    val monday: StateFlow<LocalDate> = mutableMonday.asStateFlow()

    private val mutableView = MutableStateFlow(viewStore.load() ?: PlanView.Mine)
    val view: StateFlow<PlanView> = mutableView.asStateFlow()

    private val mutableRoute = MutableStateFlow<PlanRoute>(PlanRoute.Week)
    val route: StateFlow<PlanRoute> = mutableRoute.asStateFlow()

    private val mutableWeek = MutableStateFlow<WeekState>(WeekState.Loading)
    val week: StateFlow<WeekState> = mutableWeek.asStateFlow()

    /** What the last change could not do, until the next one. */
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    private val mutablePicker = MutableStateFlow(PickerState())
    val picker: StateFlow<PickerState> = mutablePicker.asStateFlow()

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    private var weekJob: Job? = null
    private var searchJob: Job? = null
    private var ingredientJob: Job? = null
    private var catalog: List<CatalogIngredient>? = null

    fun ensureLoaded() {
        if (week.value !is WeekState.Loaded && weekJob?.isActive != true) load()
    }

    fun retry() = load()

    fun previousWeek() = goToWeek(monday.value.plus(DatePeriod(days = -7)))

    fun nextWeek() = goToWeek(monday.value.plus(DatePeriod(days = 7)))

    fun thisWeek() = goToWeek(mondayOf(today()))

    fun isToday(date: LocalDate): Boolean = date == today()

    private fun goToWeek(next: LocalDate) {
        mutableRoute.value = PlanRoute.Week
        mutableError.value = null
        if (next == monday.value && week.value is WeekState.Loaded) return
        mutableMonday.value = next
        mutableWeek.value = WeekState.Loading
        load()
    }

    fun chooseView(next: PlanView) {
        mutableView.value = next
        viewStore.save(next)
        dropGoneRoute()
    }

    fun openMeal(entry: MealPlanEntry) = go(PlanRoute.Meal(entry.id))

    fun openDay(date: LocalDate) = go(PlanRoute.Day(date))

    fun openPicker(date: LocalDate, mealType: MealType) {
        go(PlanRoute.Pick(date, mealType))
        mutablePicker.value = PickerState()
        startSearch(waitFirst = false)
    }

    /** Back to the week. False when already there. */
    fun back(): Boolean {
        if (route.value == PlanRoute.Week) return false
        go(PlanRoute.Week)
        return true
    }

    private fun go(to: PlanRoute) {
        mutableError.value = null
        if (to !is PlanRoute.Pick) searchJob?.cancel()
        mutableRoute.value = to
    }

    fun fewerServings(entry: MealPlanEntry) = changeServings(entry, entry.servings - 0.5)

    fun moreServings(entry: MealPlanEntry) = changeServings(entry, entry.servings + 0.5)

    private fun changeServings(entry: MealPlanEntry, servings: Double) {
        val next = (servings * 2).roundToInt() / 2.0
        if (next < MIN_SERVINGS || next > MAX_SERVINGS) return
        change("Could not change the servings.") {
            planning()
                .updateMealPlanEntry(monday.value, entry.id, MealPlanEntryPatch(servings = next))
                .body()
        }
    }

    /** Saves a typed amount of a planned food; one the API would refuse changes nothing. */
    fun changeAmount(entry: MealPlanEntry, text: String) {
        val amount = parseFoodAmount(text) ?: return
        if (amount == entry.servings) return
        change("Could not change that amount.") {
            planning()
                .updateMealPlanEntry(monday.value, entry.id, MealPlanEntryPatch(servings = amount))
                .body()
        }
    }

    fun toggleDiner(entry: MealPlanEntry, memberId: String) {
        val diners = toggleDiner(entry.diners.map { it.id }, memberId) ?: return
        change("Could not change who eats that meal.") {
            planning()
                .updateMealPlanEntry(monday.value, entry.id, MealPlanEntryPatch(diners = diners))
                .body()
        }
    }

    fun remove(entry: MealPlanEntry) {
        change("Could not remove that meal.") {
            planning().deleteMealPlanEntry(monday.value, entry.id)
            mutableRoute.value = PlanRoute.Week
        }
    }

    fun search(text: String) {
        if (text == picker.value.query) return
        mutablePicker.update { it.copy(query = text) }
        startSearch(waitFirst = true)
    }

    fun plan(recipe: RecipeSummary) {
        val slot = route.value as? PlanRoute.Pick ?: return
        val servings = defaultServings(slot.mealType, memberCount()).toDouble()
        add(slot, MealPlanEntryInput(slot.date, slot.mealType, servings, recipeId = recipe.id))
    }

    fun plan(food: CatalogIngredient) {
        val slot = route.value as? PlanRoute.Pick ?: return
        plan(slot, food)
    }

    /** Opens a new ingredient named as the search, counted per 100 g until the person says. */
    fun startNewIngredient() = mutablePicker.update {
        it.copy(adding = NewIngredientDraft(it.query.trim(), NutritionBasis.PER_100_G))
    }

    fun editNewIngredient(change: (NewIngredientDraft) -> NewIngredientDraft) =
        mutablePicker.update {
            it.copy(adding = it.adding?.let(change))
        }

    fun cancelNewIngredient() = mutablePicker.update { it.copy(adding = null) }

    /**
     * Saves the new ingredient as one of the household's own, then plans it in the picker's slot at
     * a new food's amount. Saved but not planned, it stays searchable for another try.
     */
    fun saveNewIngredient() {
        val slot = route.value as? PlanRoute.Pick ?: return
        val adding = picker.value.adding?.takeIf { !it.saving } ?: return
        if (busy.value) return
        val input =
            when (val checked = adding.checkInput()) {
                is CheckedIngredient.Invalid -> {
                    editNewIngredient { it.copy(error = checked.message) }
                    return
                }
                is CheckedIngredient.Valid -> checked.input
            }
        editNewIngredient { it.copy(saving = true, error = null) }
        ingredientJob = scope.launch {
            when (val saved = recipes().saveIngredient(input)) {
                is SavedIngredient.Failed ->
                    editNewIngredient { it.copy(saving = false, error = saved.message) }
                is SavedIngredient.Created -> {
                    catalog = catalog?.let { withIngredient(it, saved.ingredient) }
                    cancelNewIngredient()
                    if (route.value != slot) return@launch
                    startSearch(waitFirst = false)
                    plan(slot, saved.ingredient)
                }
            }
        }
    }

    private fun plan(slot: PlanRoute.Pick, food: CatalogIngredient) {
        val planned = newFoodEntry(food, slot.mealType, memberCount())
        add(
            slot,
            MealPlanEntryInput(slot.date, slot.mealType, planned.servings, food = planned.food),
        )
    }

    private fun add(slot: PlanRoute.Pick, input: MealPlanEntryInput) {
        change("Could not plan that meal.") {
            planning().addMealPlanEntry(monday.value, input).body()
            if (route.value == slot) mutableRoute.value = PlanRoute.Week
        }
    }

    private fun memberCount(): Int = (week.value as? WeekState.Loaded)?.week?.members?.size ?: 0

    /**
     * One change at a time: it runs, then the week reloads, keeping what is on screen meanwhile.
     */
    private fun change(failure: String, write: suspend () -> Unit) {
        if (busy.value) return
        mutableBusy.value = true
        mutableError.value = null
        scope.launch {
            try {
                write()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = failure
            }
            try {
                mutableWeek.value = WeekState.Loaded(fetch(monday.value))
                dropGoneRoute()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = mutableError.value ?: "Could not load the plan."
            } finally {
                mutableBusy.value = false
            }
        }
    }

    /**
     * A meal no longer planned or shown, or a day whose totals are hidden, closes its inspector, as
     * on the web.
     */
    private fun dropGoneRoute() {
        val loaded = (week.value as? WeekState.Loaded)?.week ?: return
        when (val current = route.value) {
            is PlanRoute.Meal ->
                if (loaded.shown(view.value).none { it.id == current.entryId }) {
                    mutableRoute.value = PlanRoute.Week
                }
            is PlanRoute.Day ->
                if (!loaded.totalsShown(view.value)) mutableRoute.value = PlanRoute.Week
            else -> {}
        }
    }

    /** A newer week replaces the one before it, so a slow answer never lands after a newer one. */
    private fun load() {
        weekJob?.cancel()
        val wanted = monday.value
        weekJob = scope.launch {
            mutableWeek.value =
                try {
                    WeekState.Loaded(fetch(wanted))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    WeekState.Failed("Could not load the plan.")
                }
        }
    }

    private suspend fun fetch(wanted: LocalDate): PlanWeek = coroutineScope {
        val plan = async { planning().getMealPlan(wanted).body() }
        // The household and the totals only add to the week: without them it still plans.
        val members = async { optional { household().getHousehold().body().members } }
        val totals = async { optional { planning().getMyPlannedTotals(wanted).body().days } }
        PlanWeek(
            wanted,
            plan.await().propertyEntries,
            members.await() ?: emptyList(),
            totals.await(),
        )
    }

    private suspend fun <T> optional(read: suspend () -> T): T? =
        try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    /** Recipes are searched on the server; foods come from the catalog, loaded once. */
    private fun startSearch(waitFirst: Boolean) {
        searchJob?.cancel()
        searchJob = scope.launch {
            if (waitFirst) delay(searchDelayMillis)
            val query = picker.value.query
            val q = query.trim().ifEmpty { null }
            val found = coroutineScope {
                val mine = async { optional { recipes().listMyRecipes(q).body() } }
                val library = async { optional { recipes().listLibraryRecipes(q).body() } }
                val ingredients =
                    catalog
                        ?: optional { recipes().listIngredients().body() }?.also { catalog = it }
                (mine.await().orEmpty() + library.await().orEmpty()) to ingredients.orEmpty()
            }
            mutablePicker.update {
                if (it.query != query) it
                else it.copy(recipes = found.first, foods = searchCatalog(query, found.second))
            }
        }
    }

    /** Back to the start, for when the person signs out and someone else may sign in. */
    fun reset() {
        weekJob?.cancel()
        searchJob?.cancel()
        ingredientJob?.cancel()
        catalog = null
        mutableMonday.value = mondayOf(today())
        mutableRoute.value = PlanRoute.Week
        mutableWeek.value = WeekState.Loading
        mutableError.value = null
        mutableBusy.value = false
        mutablePicker.value = PickerState()
    }

    companion object {
        const val SEARCH_DELAY_MILLIS = 300L
        const val MIN_SERVINGS = 0.5
        const val MAX_SERVINGS = 100.0
    }
}
