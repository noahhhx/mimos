package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.api.apis.FoodProductsApi
import io.github.noahhhx.mimos.api.apis.HouseholdApi
import io.github.noahhhx.mimos.api.apis.LoggingApi
import io.github.noahhhx.mimos.api.apis.PlanningApi
import io.github.noahhhx.mimos.api.apis.PluginsApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.FoodProduct
import io.github.noahhhx.mimos.api.models.MealLogInput
import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.api.models.MealPlanEntryInput
import io.github.noahhhx.mimos.api.models.MealPlanEntryPatch
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.api.models.Person
import io.github.noahhhx.mimos.api.models.PlanSuggestion
import io.github.noahhhx.mimos.api.models.PlannedDayTotal
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.barcode.ScanModel
import io.github.noahhhx.mimos.barcode.existingIngredient
import io.github.noahhhx.mimos.barcode.productDraft
import io.github.noahhhx.mimos.barcode.productIngredient
import io.github.noahhhx.mimos.ingredients.CheckedIngredient
import io.github.noahhhx.mimos.ingredients.NewIngredientDraft
import io.github.noahhhx.mimos.ingredients.SavedIngredient
import io.github.noahhhx.mimos.ingredients.checkInput
import io.github.noahhhx.mimos.ingredients.saveIngredient
import io.github.noahhhx.mimos.ingredients.withIngredient
import io.github.noahhhx.mimos.recipes.form.numberText
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
 * reads as a household of one's), the caller's planned totals (null when they failed), and the
 * plugins' suggestions for it (none when they failed: they only add to the week, ADR-0006).
 */
data class PlanWeek(
    val monday: LocalDate,
    val entries: List<MealPlanEntry>,
    val members: List<Person>,
    val totals: List<PlannedDayTotal>?,
    val suggestions: List<PlanSuggestion> = emptyList(),
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

/** A shared meal's Log form, open on [entryId]: the caller's share as typed. */
data class Sharing(val entryId: String, val text: String)

/** What logging [entryId] did, shown on its inspector until the next screen. */
data class Logged(val entryId: String, val text: String)

/**
 * The picker's search: recipes (the household's, then the library's) and catalog foods, and the new
 * ingredient being added when the catalog lacks one, or [scanned] lacks a value.
 */
data class PickerState(
    val query: String = "",
    val recipes: List<RecipeSummary>? = null,
    val foods: List<CatalogIngredient> = emptyList(),
    val adding: NewIngredientDraft? = null,
    val scanned: FoodProduct? = null,
) {
    /** "+ Add" is offered once the search has answered and found no food by that name. */
    val offersNewIngredient: Boolean
        get() = recipes != null && adding == null && offersNewIngredient(query, foods)
}

/**
 * The Plan tab, as the web's plan page (ADR-0028, ADR-0019, ADR-0026): a week by its Monday, the
 * caller's meals or everyone's, a meal's or a day's inspector, the picker that plans a recipe or a
 * food, and the plugins' panels and suggestions (ADR-0006, ADR-0017). Every change reloads the
 * week, as the web does. [today] is the phone's date.
 */
class PlanModel(
    private val scope: CoroutineScope,
    private val viewStore: PlanViewStore,
    private val planning: () -> PlanningApi,
    private val household: () -> HouseholdApi,
    private val recipes: () -> RecipesApi,
    private val plugins: () -> PluginsApi,
    private val logging: () -> LoggingApi,
    foodProducts: () -> FoodProductsApi,
    private val today: () -> LocalDate,
    private val searchDelayMillis: Long = SEARCH_DELAY_MILLIS,
) {
    /** The picker's scanner: a found product is planned in the picker's slot (ADR-0026). */
    val scanner = ScanModel(scope, foodProducts, onFound = ::planProduct)

    /** The week's panels, which a button press can change the suggestions of. */
    val panels = WeekPanelsModel(scope, plugins, onPressed = ::refreshSuggestions)

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

    /** What applying a suggestion did, until the next change or the next screen. */
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = mutableNotice.asStateFlow()

    private val mutableApplying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = mutableApplying.asStateFlow()

    private val mutablePicker = MutableStateFlow(PickerState())
    val picker: StateFlow<PickerState> = mutablePicker.asStateFlow()

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    private val mutableSharing = MutableStateFlow<Sharing?>(null)
    val sharing: StateFlow<Sharing?> = mutableSharing.asStateFlow()

    private val mutableLogged = MutableStateFlow<Logged?>(null)
    val logged: StateFlow<Logged?> = mutableLogged.asStateFlow()

    private val mutableLogging = MutableStateFlow(false)
    val loggingMeal: StateFlow<Boolean> = mutableLogging.asStateFlow()

    private var weekJob: Job? = null
    private var searchJob: Job? = null
    private var ingredientJob: Job? = null
    private var logJob: Job? = null
    private var catalog: List<CatalogIngredient>? = null

    fun ensureLoaded() {
        if (week.value !is WeekState.Loaded && weekJob?.isActive != true) load()
        panels.ensureLoaded(monday.value)
    }

    /** A plugin was turned on or off: the panels and suggestions on screen are out of date. */
    fun pluginsChanged() {
        // Not opened since the app started or someone signed in: opening it loads them.
        if (weekJob == null) return
        panels.load(monday.value)
        refreshSuggestions(monday.value)
    }

    fun retry() = load()

    fun previousWeek() = goToWeek(monday.value.plus(DatePeriod(days = -7)))

    fun nextWeek() = goToWeek(monday.value.plus(DatePeriod(days = 7)))

    fun thisWeek() = goToWeek(mondayOf(today()))

    fun isToday(date: LocalDate): Boolean = date == today()

    private fun goToWeek(next: LocalDate) {
        mutableRoute.value = PlanRoute.Week
        mutableError.value = null
        mutableNotice.value = null
        if (next == monday.value && week.value is WeekState.Loaded) return
        mutableMonday.value = next
        mutableWeek.value = WeekState.Loading
        load()
        panels.load(next)
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
        mutableNotice.value = null
        mutableSharing.value = null
        mutableLogged.value = null
        if (to !is PlanRoute.Pick) {
            searchJob?.cancel()
            scanner.close()
        }
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

    /** A meal eaten alone logs in one tap; a shared one asks for the caller's share first. */
    fun startLog(entry: MealPlanEntry) {
        mutableLogged.value = null
        if (entry.diners.size > 1) {
            mutableSharing.value = Sharing(entry.id, numberText(entry.yourShare ?: entry.servings))
        } else logMeal(entry, entry.servings)
    }

    fun editShare(text: String) = mutableSharing.update { it?.copy(text = text) }

    fun cancelShare() {
        mutableSharing.value = null
    }

    /** Logs the share typed in [entry]'s form, once it is one the form takes. */
    fun logShare(entry: MealPlanEntry) {
        val typed = sharing.value?.takeIf { it.entryId == entry.id } ?: return
        logMeal(entry, parseShare(typed.text, entry.what()) ?: return)
    }

    /**
     * A recipe logs as servings of it; a food as an ad-hoc meal of its amount, with the nutrition
     * its ingredient gives it (ADR-0026). The week does not change, so it is not reloaded.
     */
    private fun logMeal(entry: MealPlanEntry, amount: Double) {
        if (loggingMeal.value) return
        mutableLogging.value = true
        mutableError.value = null
        logJob = scope.launch {
            try {
                val body =
                    when (val what = entry.what()) {
                        is PlannedWhat.Recipe ->
                            MealLogInput(
                                entry.date,
                                entry.mealType,
                                recipeId = what.recipeId,
                                servings = amount,
                            )
                        is PlannedWhat.Food ->
                            foodLogBody(
                                entry.date,
                                entry.mealType,
                                what.food,
                                loadedCatalog(),
                                amount,
                            )
                    } ?: error("${entry.name()} is not in the catalog")
                logging().createMealLog(body).body()
                mutableSharing.value = null
                mutableLogged.value = Logged(entry.id, "${entry.name()} logged.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = "Could not log that meal."
            } finally {
                mutableLogging.value = false
            }
        }
    }

    private suspend fun loadedCatalog(): List<CatalogIngredient> =
        catalog ?: recipes().listIngredients().body().also { catalog = it }

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
        it.copy(
            adding = NewIngredientDraft(it.query.trim(), NutritionBasis.PER_100_G),
            scanned = null,
        )
    }

    fun editNewIngredient(change: (NewIngredientDraft) -> NewIngredientDraft) =
        mutablePicker.update {
            it.copy(adding = it.adding?.let(change))
        }

    fun cancelNewIngredient() = mutablePicker.update { it.copy(adding = null, scanned = null) }

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
                    val created = saved.ingredient
                    catalog = catalog?.let { withIngredient(it, created) }
                    // A scanned product's serving still fits unless its basis was changed.
                    val serving =
                        picker.value.scanned?.takeIf { it.basis == created.basis }?.servingSize
                    cancelNewIngredient()
                    if (route.value != slot) return@launch
                    startSearch(waitFirst = false)
                    plan(slot, created, serving)
                }
            }
        }
    }

    /**
     * Plans a scanned product as the household's own ingredient, made once from the lookup and
     * found again on the next scan, at a serving each; one missing a value opens the new-ingredient
     * panel with what Open Food Facts knew.
     */
    private fun planProduct(product: FoodProduct) {
        val slot = route.value as? PlanRoute.Pick ?: return
        val input = productIngredient(product)
        if (input == null) {
            mutablePicker.update { it.copy(adding = productDraft(product), scanned = product) }
            return
        }
        change("Could not add that product.") {
            val food =
                existingIngredient(input, loadedCatalog())
                    ?: recipes().createIngredient(input).body().also { created ->
                        catalog = catalog?.let { withIngredient(it, created) }
                    }
            addEntry(slot, food, product.servingSize)
        }
    }

    private fun plan(slot: PlanRoute.Pick, food: CatalogIngredient, servingSize: Double? = null) {
        change("Could not plan that meal.") { addEntry(slot, food, servingSize) }
    }

    private suspend fun addEntry(
        slot: PlanRoute.Pick,
        food: CatalogIngredient,
        servingSize: Double?,
    ) {
        val planned = newFoodEntry(food.slug, food.basis, servingSize, slot.mealType, memberCount())
        planning()
            .addMealPlanEntry(
                monday.value,
                MealPlanEntryInput(slot.date, slot.mealType, planned.servings, food = planned.food),
            )
            .body()
        if (route.value == slot) mutableRoute.value = PlanRoute.Week
    }

    private fun add(slot: PlanRoute.Pick, input: MealPlanEntryInput) {
        change("Could not plan that meal.") {
            planning().addMealPlanEntry(monday.value, input).body()
            if (route.value == slot) mutableRoute.value = PlanRoute.Week
        }
    }

    /**
     * Adds a suggestion's meals through the plan-entry endpoint, the picker's own: plugins have no
     * write path of their own (ADR-0006). Each meal that fails is left out of the count.
     */
    fun applySuggestion(suggestion: PlanSuggestion) {
        if (busy.value) return
        mutableApplying.value = true
        change("Could not add that suggestion.") {
            try {
                var added = 0
                for (entry in suggestion.propertyEntries) {
                    try {
                        planning()
                            .addMealPlanEntry(
                                monday.value,
                                MealPlanEntryInput(
                                    entry.date,
                                    entry.mealType,
                                    entry.servings,
                                    recipeId = entry.recipeId,
                                ),
                            )
                            .body()
                        added++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {}
                }
                mutableNotice.value =
                    if (added > 0) {
                        "Added $added meal${if (added == 1) "" else "s"} from \"${suggestion.title}\"."
                    } else "Could not add that suggestion."
            } finally {
                mutableApplying.value = false
            }
        }
    }

    /** Asks the plugins again for [monday]'s suggestions, keeping the rest of the week. */
    private fun refreshSuggestions(monday: LocalDate) {
        scope.launch {
            val suggestions = fetchSuggestions(monday)
            mutableWeek.update {
                if (it is WeekState.Loaded && it.week.monday == monday) {
                    WeekState.Loaded(it.week.copy(suggestions = suggestions))
                } else it
            }
        }
    }

    private suspend fun fetchSuggestions(monday: LocalDate): List<PlanSuggestion> =
        optional { plugins().getPlanSuggestions(monday).body().suggestions } ?: emptyList()

    private fun memberCount(): Int = (week.value as? WeekState.Loaded)?.week?.members?.size ?: 0

    /**
     * One change at a time: it runs, then the week reloads, keeping what is on screen meanwhile.
     */
    private fun change(failure: String, write: suspend () -> Unit) {
        if (busy.value) return
        mutableBusy.value = true
        mutableError.value = null
        mutableNotice.value = null
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
        val suggestions = async { fetchSuggestions(wanted) }
        PlanWeek(
            wanted,
            plan.await().propertyEntries,
            members.await() ?: emptyList(),
            totals.await(),
            suggestions.await(),
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
        scanner.close()
        weekJob?.cancel()
        weekJob = null
        searchJob?.cancel()
        ingredientJob?.cancel()
        logJob?.cancel()
        panels.reset()
        catalog = null
        mutableMonday.value = mondayOf(today())
        mutableRoute.value = PlanRoute.Week
        mutableWeek.value = WeekState.Loading
        mutableError.value = null
        mutableNotice.value = null
        mutableApplying.value = false
        mutableBusy.value = false
        mutableSharing.value = null
        mutableLogged.value = null
        mutableLogging.value = false
        mutablePicker.value = PickerState()
    }

    companion object {
        const val SEARCH_DELAY_MILLIS = 300L
        const val MIN_SERVINGS = 0.5
        const val MAX_SERVINGS = 100.0
    }
}
