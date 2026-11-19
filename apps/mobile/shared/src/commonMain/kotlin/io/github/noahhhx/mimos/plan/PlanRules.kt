package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.MealLogInput
import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.api.models.PlannedDayTotal
import io.github.noahhhx.mimos.api.models.PlannedFood
import io.github.noahhhx.mimos.api.models.PlannedFoodInput
import io.github.noahhhx.mimos.log.AmountFields
import io.github.noahhhx.mimos.log.Measured
import io.github.noahhhx.mimos.log.measuredBody
import io.github.noahhhx.mimos.log.parseServings
import io.github.noahhhx.mimos.recipes.form.numberText
import kotlin.math.roundToLong
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/*
 * The plan page's rules, ported from the web (`apps/web/src/lib/diners.ts`, `planned-item.ts`,
 * `planned-totals.ts`, and `format.ts`) word for word: whose meals a view shows, what a new meal
 * starts with, and how a planned meal reads (ADR-0019, ADR-0026).
 * `libraries/test-cases/plan.json` holds the cases both sides run.
 */

/** The four meals of a day, in the order the plan lists them. */
val MEAL_TYPES = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)

fun mealLabel(mealType: MealType): String =
    when (mealType) {
        MealType.BREAKFAST -> "Breakfast"
        MealType.LUNCH -> "Lunch"
        MealType.DINNER -> "Dinner"
        MealType.SNACK -> "Snack"
        MealType.unknown_default_open_api -> "Meal"
    }

/** The plan's toggle: the caller's meals, or the whole household's. */
enum class PlanView(val label: String) {
    Mine("Mine"),
    Everyone("Everyone"),
}

/** The Monday of the week [date] is in: plans are keyed by it. */
fun mondayOf(date: LocalDate): LocalDate = date.minus(DatePeriod(days = date.dayOfWeek.ordinal))

/** The seven days of the week starting [monday]. */
fun weekDays(monday: LocalDate): List<LocalDate> = (0..6).map { monday.plus(DatePeriod(days = it)) }

private val shortWeekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val shortMonths =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Mon 17 Nov": a day as the plan names it. */
fun dayLabel(date: LocalDate): String =
    "${shortWeekdays[date.dayOfWeek.ordinal]} ${date.day} ${shortMonths[date.month.ordinal]}"

/** "Monday". */
fun weekdayName(date: LocalDate): String =
    date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

/** Whether the caller is one of the meal's diners. */
fun MealPlanEntry.eatenByYou(): Boolean = diners.any { it.you }

/** The entries a view shows: Mine keeps the meals the caller eats, shared ones included. */
fun visibleEntries(entries: List<MealPlanEntry>, view: PlanView): List<MealPlanEntry> =
    if (view == PlanView.Everyone) entries else entries.filter { it.eatenByYou() }

/** Planned totals show in a household of one, and in a shared household's Mine. */
fun showsPlannedTotals(shared: Boolean, view: PlanView): Boolean = !shared || view == PlanView.Mine

/**
 * How many people a new meal is for by default: every member at dinner, the caller alone otherwise.
 */
fun defaultDinerCount(mealType: MealType, memberCount: Int): Int =
    if (memberCount > 1 && mealType == MealType.DINNER) memberCount else 1

/**
 * The servings a new meal starts with. A household of one keeps 2; a shared household cooks one
 * serving for each of the meal's default diners.
 */
fun defaultServings(mealType: MealType, memberCount: Int): Int =
    if (memberCount <= 1) 2 else defaultDinerCount(mealType, memberCount)

/** The diners after tapping a member; null when that would leave the meal with nobody. */
fun toggleDiner(dinerIds: List<String>, memberId: String): List<String>? =
    when {
        memberId !in dinerIds -> dinerIds + memberId
        dinerIds.size == 1 -> null
        else -> dinerIds - memberId
    }

/**
 * A short label for each member's chip: their initials ("Sam Lee" is "SL"), or, where two members'
 * initials match, the start of their name ("Sam" and "Sara" are "Sam" and "Sar").
 */
fun chipLabels(names: List<String>): List<String> {
    val initials = names.map(::initialsOf)
    fun start(name: String, length: Int) = name.trim().take(length).lowercase()
    return names.mapIndexed { index, name ->
        val clashing = names.filterIndexed { other, _ -> initials[other] == initials[index] }
        if (clashing.size == 1) {
            initials[index]
        } else {
            val two = start(name, 2)
            val twoIsEnough = clashing.count { start(it, 2) == two } == 1
            (if (twoIsEnough) two else start(name, 3)).replaceFirstChar { it.uppercase() }
        }
    }
}

private fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val last = if (words.size > 1) words.last().take(1) else ""
    return (words.first().take(1) + last).uppercase()
}

/** What a plan entry plans: a recipe, counted in servings, or a food, counted in its unit. */
sealed interface PlannedWhat {
    data class Recipe(val recipeId: String, val title: String) : PlannedWhat

    data class Food(val food: PlannedFood) : PlannedWhat
}

/** The API sends exactly one of a recipe and a food; an entry with neither is not one. */
fun MealPlanEntry.what(): PlannedWhat =
    food?.let { PlannedWhat.Food(it) }
        ?: PlannedWhat.Recipe(
            recipeId ?: error("A planned meal names a recipe or a food"),
            recipeTitle ?: error("A planned meal names a recipe or a food"),
        )

/** A planned meal's name: the recipe's title or the food's name. */
fun MealPlanEntry.name(): String =
    when (val what = what()) {
        is PlannedWhat.Recipe -> what.title
        is PlannedWhat.Food -> what.food.name
    }

/** "2 servings", "1.5 servings", "1 serving". */
fun formatServings(servings: Double): String =
    "${numberText(servings)} ${if (servings == 1.0) "serving" else "servings"}"

/** A unit as words: itself, or pieces, singular for one. */
fun foodUnitLabel(unit: String?, quantity: Double): String =
    if (!unit.isNullOrEmpty()) unit else if (quantity == 1.0) "piece" else "pieces"

/** An amount planned, in words, like "2 servings", "85 g", or "1 piece". */
fun amountText(what: PlannedWhat, servings: Double): String =
    when (what) {
        is PlannedWhat.Recipe -> formatServings(servings)
        is PlannedWhat.Food -> "${numberText(servings)} ${foodUnitLabel(what.food.unit, servings)}"
    }

fun MealPlanEntry.amountText(): String = amountText(what(), servings)

/** The most the API takes as a food's amount (`MAX_FOOD_QUANTITY`). */
const val MAX_FOOD_AMOUNT = 10_000.0

private val DECIMAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

/** A typed amount of a food, if the API takes it: more than 0, at most 10,000. */
fun parseFoodAmount(text: String): Double? {
    val trimmed = text.trim()
    if (!DECIMAL.matches(trimmed)) return null
    val amount = trimmed.toDouble()
    return amount.takeIf { it > 0 && it <= MAX_FOOD_AMOUNT }
}

/** The food a new entry plans and how much of it. */
data class NewFood(val food: PlannedFoodInput, val servings: Double)

/**
 * A new planned food: what the Log page would start at (one serving, else 100 g, 100 ml, or 1
 * piece) for each of the meal's default diners.
 */
fun newFoodEntry(
    slug: String,
    basis: NutritionBasis,
    servingSize: Double?,
    mealType: MealType,
    memberCount: Int,
): NewFood {
    val (amount, unit) =
        when (basis) {
            NutritionBasis.PER_100_G -> 100.0 to "g"
            NutritionBasis.PER_100_ML -> 100.0 to "ml"
            NutritionBasis.PER_PIECE,
            NutritionBasis.unknown_default_open_api -> 1.0 to null
        }
    val portion = servingSize ?: amount
    val servings =
        (portion * defaultDinerCount(mealType, memberCount) * 1000).roundToLong() / 1000.0
    return NewFood(PlannedFoodInput(catalogSlug = slug, unit = unit), servings)
}

fun newFoodEntry(food: CatalogIngredient, mealType: MealType, memberCount: Int): NewFood =
    newFoodEntry(food.slug, food.basis, null, mealType, memberCount)

/** Whether [query] names a food none of [foods], the catalog's matches for it, is called. */
fun offersNewIngredient(query: String, foods: List<CatalogIngredient>): Boolean {
    val typed = query.trim().lowercase()
    return typed != "" && foods.none { it.name.lowercase() == typed }
}

/** The note under a day's planned totals when some of its meals' nutrition is unknown. */
fun withoutNutritionNote(day: PlannedDayTotal): String? =
    withoutNutritionNote(day.mealsWithoutNutrition)

fun withoutNutritionNote(mealsWithoutNutrition: Int): String? =
    when (mealsWithoutNutrition) {
        0 -> null
        1 -> "Unknown nutrition in 1 meal is not counted."
        else -> "Unknown nutrition in $mealsWithoutNutrition meals is not counted."
    }

/**
 * The ad-hoc log of [amount] of a planned food, with the nutrition its ingredient in [catalog]
 * gives it; null when the catalog lacks the ingredient or the unit does not fit it.
 */
fun foodLogBody(
    date: LocalDate,
    mealType: MealType,
    food: PlannedFood,
    catalog: List<CatalogIngredient>,
    amount: Double,
): MealLogInput? {
    val entry = catalog.find { it.slug == food.catalogSlug } ?: return null
    return measuredBody(
        Measured(entry.name, entry.basis, entry.nutrition),
        date,
        mealType,
        AmountFields(numberText(amount), food.unit ?: ""),
    )
}

/** The least share the plan's Log form takes, as the web's field does. */
const val MIN_SHARE = 0.1

/**
 * A typed share of a shared meal, if it can be logged: at least 0.1, and at most 100 servings of a
 * recipe or 10,000 of a food.
 */
fun parseShare(text: String, what: PlannedWhat): Double? {
    val amount =
        when (what) {
            is PlannedWhat.Recipe -> parseServings(text)
            is PlannedWhat.Food -> parseFoodAmount(text)
        }
    return amount?.takeIf { it >= MIN_SHARE }
}
