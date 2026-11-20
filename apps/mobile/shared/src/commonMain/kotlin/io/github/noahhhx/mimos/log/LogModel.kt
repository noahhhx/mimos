package io.github.noahhhx.mimos.log

import io.github.noahhhx.mimos.api.apis.FoodProductsApi
import io.github.noahhhx.mimos.api.apis.FuelApi
import io.github.noahhhx.mimos.api.apis.LoggingApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.DailyLogSummary
import io.github.noahhhx.mimos.api.models.FuelTarget
import io.github.noahhhx.mimos.api.models.MealLog
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.barcode.ScanModel
import io.github.noahhhx.mimos.plan.mondayOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A week of the person's log: every entry, each day's totals, and each day's target while their
 * targets are on (ADR-0022).
 */
data class LogWeek(
    val monday: LocalDate,
    val logs: List<MealLog>,
    val summary: List<DailyLogSummary>,
    val targets: Map<LocalDate, FuelTarget> = emptyMap(),
) {
    fun logsOn(date: LocalDate): List<MealLog> = logs.filter { it.date == date }

    fun summaryOf(date: LocalDate): DailyLogSummary? = summary.find { it.date == date }
}

sealed interface LogWeekState {
    data object Loading : LogWeekState

    data class Loaded(val week: LogWeek) : LogWeekState

    data object Failed : LogWeekState
}

/** The "Log something else" form, as typed. */
data class LogForm(
    val what: LogWhat = LogWhat.Custom(""),
    val mealType: MealType = MealType.SNACK,
    val servings: String = "1",
    val nutrition: NutritionFields = NutritionFields(),
    val amount: AmountFields = AmountFields("100", "g"),
) {
    fun body(date: LocalDate) = logBody(what, date, mealType, servings, nutrition, amount)
}

/** What the What search searches: the household's and the library's recipes, and the catalog. */
data class LogChoices(
    val recipes: List<RecipeSummary> = emptyList(),
    val ingredients: List<CatalogIngredient> = emptyList(),
) {
    fun search(query: String): List<WhatOption> = searchWhat(query, recipes, ingredients)
}

/**
 * The Log tab, as the web's Log page: a week's per-day totals by its Monday, the selected day's
 * entries, and a form that logs a recipe in servings, an ingredient by amount, or anything else by
 * hand, or a packaged product found by its barcode. Information, not judgement. [today] is the
 * phone's date.
 */
class LogModel(
    private val scope: CoroutineScope,
    private val logging: () -> LoggingApi,
    private val recipes: () -> RecipesApi,
    foodProducts: () -> FoodProductsApi,
    private val fuel: () -> FuelApi,
    private val today: () -> LocalDate,
) {
    /** A found product is picked into What, to log by amount. */
    val scanner = ScanModel(scope, foodProducts, onFound = { pick(LogWhat.Product(it)) })

    private val mutableMonday = MutableStateFlow(mondayOf(today()))
    val monday: StateFlow<LocalDate> = mutableMonday.asStateFlow()

    private val mutableSelected = MutableStateFlow(today())
    val selected: StateFlow<LocalDate> = mutableSelected.asStateFlow()

    private val mutableWeek = MutableStateFlow<LogWeekState>(LogWeekState.Loading)
    val week: StateFlow<LogWeekState> = mutableWeek.asStateFlow()

    private val mutableForm = MutableStateFlow(LogForm())
    val form: StateFlow<LogForm> = mutableForm.asStateFlow()

    private val mutableChoices = MutableStateFlow(LogChoices())
    val choices: StateFlow<LogChoices> = mutableChoices.asStateFlow()

    private val mutableAdding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = mutableAdding.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    private var weekJob: Job? = null
    private var choicesJob: Job? = null
    private var choicesLoaded = false
    private val jobs = mutableListOf<Job>()

    /**
     * The tab came on screen: its week loads again, as the web's page does on each visit, since a
     * planned meal may have been logged meanwhile. The search's recipes and ingredients load once.
     */
    fun opened() {
        load()
        if (!choicesLoaded && choicesJob?.isActive != true) loadChoices()
    }

    fun retry() = load()

    /** The weeks move with the selected day, so it stays the same weekday in the week shown. */
    fun previousWeek() = moveWeek(-7)

    fun nextWeek() = moveWeek(7)

    fun thisWeek() = goTo(today())

    private fun moveWeek(days: Int) = goTo(selected.value.plus(DatePeriod(days = days)))

    private fun goTo(day: LocalDate) {
        mutableSelected.value = day
        if (mondayOf(day) == monday.value) return
        mutableMonday.value = mondayOf(day)
        mutableError.value = null
        load()
    }

    fun selectDay(date: LocalDate) {
        if (mondayOf(date) == monday.value) mutableSelected.value = date
    }

    /** Typing makes a custom log, so typing over a picked recipe's title unpicks it. */
    fun type(text: String) = mutableForm.update { it.copy(what = typeWhat(text)) }

    /** A picked food starts at its default amount, a picked recipe at one serving. */
    fun pick(picked: LogWhat) = mutableForm.update { form ->
        val food = measuredOf(picked)
        if (food != null) form.copy(what = picked, amount = defaultAmount(food))
        else form.copy(what = picked, servings = "1")
    }

    fun chooseMeal(mealType: MealType) = mutableForm.update { it.copy(mealType = mealType) }

    fun editServings(text: String) = mutableForm.update { it.copy(servings = text) }

    fun editNutrition(change: (NutritionFields) -> NutritionFields) = mutableForm.update {
        it.copy(nutrition = change(it.nutrition))
    }

    fun editAmount(change: (AmountFields) -> AmountFields) = mutableForm.update {
        it.copy(amount = change(it.amount))
    }

    /** Logs the form on the selected day, then clears it and loads the week again. */
    fun add() {
        if (adding.value) return
        val body = form.value.body(selected.value) ?: return
        mutableAdding.value = true
        launch {
            try {
                logging().createMealLog(body).body()
                mutableError.value = null
                mutableForm.update {
                    it.copy(
                        what = LogWhat.Custom(""),
                        servings = "1",
                        nutrition = NutritionFields(),
                    )
                }
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = "Could not log that meal."
            } finally {
                mutableAdding.value = false
            }
        }
    }

    fun delete(log: MealLog) {
        launch {
            try {
                logging().deleteMealLog(log.id)
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = "Could not delete that entry."
            }
        }
    }

    /**
     * A newer load replaces the one before it, so a slow answer never lands over a newer week. The
     * week on screen stays while its own reload runs.
     */
    private fun load() {
        val wanted = monday.value
        weekJob?.cancel()
        if ((week.value as? LogWeekState.Loaded)?.week?.monday != wanted) {
            mutableWeek.value = LogWeekState.Loading
        }
        weekJob = scope.launch {
            val next =
                try {
                    coroutineScope {
                        val to = wanted.plus(DatePeriod(days = 6))
                        val logs = async { logging().listMealLogs(wanted, to).body() }
                        val summary = async { logging().summarizeMealLogs(wanted, to).body() }
                        // Targets sit beside the log; when they fail to load, the log still shows.
                        val targets = async {
                            try {
                                fuel().getMyFuel(wanted, to).body().days.mapNotNull { day ->
                                    day.target?.let { day.date to it }
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                        LogWeekState.Loaded(
                            LogWeek(wanted, logs.await(), summary.await(), targets.await().toMap())
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            when {
                next != null -> mutableWeek.value = next
                week.value is LogWeekState.Loaded -> mutableError.value = "Could not load your log."
                else -> mutableWeek.value = LogWeekState.Failed
            }
        }
    }

    /** They only make logging quicker: without them the form still logs anything by hand. */
    private fun loadChoices() {
        choicesJob = scope.launch {
            suspend fun <T> optional(read: suspend () -> List<T>): List<T>? =
                try {
                    read()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            val (mine, library, catalog) =
                coroutineScope {
                    val mine = async { optional { recipes().listMyRecipes(null).body() } }
                    val library = async { optional { recipes().listLibraryRecipes(null).body() } }
                    val catalog = async { optional { recipes().listIngredients().body() } }
                    Triple(mine.await(), library.await(), catalog.await())
                }
            // A failed part is asked for again the next time the tab opens.
            choicesLoaded = mine != null && library != null && catalog != null
            mutableChoices.value = LogChoices(mine.orEmpty() + library.orEmpty(), catalog.orEmpty())
        }
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        jobs.removeAll { !it.isActive }
        jobs += scope.launch(block = block)
    }

    /** Forgets everything, for when the person signs out or the household changes. */
    fun reset() {
        scanner.close()
        weekJob?.cancel()
        weekJob = null
        choicesJob?.cancel()
        choicesJob = null
        choicesLoaded = false
        jobs.forEach { it.cancel() }
        jobs.clear()
        mutableMonday.value = mondayOf(today())
        mutableSelected.value = today()
        mutableWeek.value = LogWeekState.Loading
        mutableForm.value = LogForm()
        mutableChoices.value = LogChoices()
        mutableAdding.value = false
        mutableError.value = null
    }
}
