package io.github.noahhhx.mimos.log

import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.FoodProduct
import io.github.noahhhx.mimos.api.models.MealLogInput
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.recipes.form.describeEntry
import io.github.noahhhx.mimos.recipes.form.jsNumber
import io.github.noahhhx.mimos.recipes.form.numberText
import io.github.noahhhx.mimos.recipes.form.searchByName
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate

/*
 * The Log page's rules, ported from the web (`apps/web/src/lib/log-entry.ts` and the Log table's
 * `nutritionCells` in `format.ts`) word for word: what the "Log something else" form logs, the
 * nutrition it previews, and the What search (ADR-0015, ADR-0016, ADR-0025).
 * `libraries/test-cases/log-entry.json` holds the cases both sides run.
 */

/**
 * What the form logs: anything described and counted by hand, a recipe counted in servings, or an
 * ingredient or a packaged product counted by amount.
 */
sealed interface LogWhat {
    data class Custom(val description: String) : LogWhat

    data class Recipe(val recipe: RecipeSummary) : WhatOption

    data class Ingredient(val entry: CatalogIngredient) : WhatOption, MeasuredWhat

    data class Product(val product: FoodProduct) : MeasuredWhat
}

/** What the What search offers: recipes and ingredients. A product comes from a barcode. */
sealed interface WhatOption : LogWhat

/** What is counted by amount. */
sealed interface MeasuredWhat : LogWhat

/**
 * Food whose nutrition is known per basis, so any amount of it can be counted; a product may also
 * say how much one serving is, in the basis's unit.
 */
data class Measured(
    val name: String,
    val basis: NutritionBasis,
    val nutrition: Nutrition,
    val servingSize: Double? = null,
)

/** The nutrition of a custom log, as typed. */
data class NutritionFields(
    val calories: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
)

/** An amount of measured food as typed, and its unit: "" counts pieces. */
data class AmountFields(val quantity: String, val unit: String)

private class LogUnit(val basis: NutritionBasis, val size: Double)

/**
 * Each unit's basis and its size in that basis's base unit, as `MetricUnit` sizes them. A basis's
 * first unit is the one its amount starts in.
 */
private val LOG_UNITS =
    linkedMapOf(
        "g" to LogUnit(NutritionBasis.PER_100_G, 1.0),
        "kg" to LogUnit(NutritionBasis.PER_100_G, 1000.0),
        "ml" to LogUnit(NutritionBasis.PER_100_ML, 1.0),
        "l" to LogUnit(NutritionBasis.PER_100_ML, 1000.0),
        "tsp" to LogUnit(NutritionBasis.PER_100_ML, 5.0),
        "tbsp" to LogUnit(NutritionBasis.PER_100_ML, 15.0),
        "" to LogUnit(NutritionBasis.PER_PIECE, 1.0),
    )

private fun basisAmount(basis: NutritionBasis): Double =
    if (basis == NutritionBasis.PER_PIECE) 1.0 else 100.0

private const val MAX_SERVINGS = 100.0

private const val MAX_DESCRIPTION_LENGTH = 200

/** The What field's text: a picked recipe's title, a picked food's name, or what was typed. */
fun whatText(what: LogWhat): String =
    when (what) {
        is LogWhat.Custom -> what.description
        is LogWhat.Recipe -> what.recipe.title
        is MeasuredWhat -> measured(what).name
    }

/** Typing into What makes a custom log, so changing a picked recipe's title unpicks it. */
fun typeWhat(text: String): LogWhat = LogWhat.Custom(text)

/** Recipes and ingredients with a word starting with what was typed, ranked together. */
fun searchWhat(
    query: String,
    recipes: List<RecipeSummary>,
    ingredients: List<CatalogIngredient>,
    limit: Int = 8,
): List<WhatOption> =
    searchByName(
        query,
        recipes.map { LogWhat.Recipe(it) } + ingredients.map { LogWhat.Ingredient(it) },
        ::whatText,
        limit,
    )

/** An ingredient as a search option's second line, like "Ingredient · 387 kcal per 100 g". */
fun describeIngredient(entry: CatalogIngredient): String =
    "${if (entry.isShared) "Ingredient" else "Your ingredient"} · ${describeEntry(entry)}"

/** The food [what] counts by amount, if it counts one. */
fun measuredOf(what: LogWhat): Measured? = (what as? MeasuredWhat)?.let(::measured)

private fun measured(what: MeasuredWhat): Measured =
    when (what) {
        is LogWhat.Ingredient -> Measured(what.entry.name, what.entry.basis, what.entry.nutrition)
        is LogWhat.Product ->
            Measured(
                productName(what.product),
                what.product.basis,
                what.product.nutrition,
                what.product.servingSize,
            )
    }

/**
 * A product's name with its brand, like "Yeast Extract (Marmite)", unless the brand is its name.
 */
fun productName(product: FoodProduct): String {
    val brand = product.brand?.trim()
    return if (!brand.isNullOrEmpty() && brand.lowercase() != product.name.trim().lowercase()) {
        "${product.name} ($brand)"
    } else product.name
}

/** The units an amount of food on [basis] can be counted in. */
fun unitsFor(basis: NutritionBasis): List<String> =
    LOG_UNITS.filterValues { it.basis == basis }.keys.toList()

/** Where a picked food's amount starts: one serving if known, else 100 g, 100 ml, or 1 piece. */
fun defaultAmount(food: Measured): AmountFields =
    AmountFields(
        numberText(food.servingSize ?: basisAmount(food.basis)),
        unitsFor(food.basis).firstOrNull() ?: "",
    )

/** A food's serving in words, like "One serving is 15 g.", when it has one. */
fun servingHint(food: Measured): String? {
    val size = food.servingSize ?: return null
    return "One serving is ${numberText(size)} ${logUnitLabel(unitsFor(food.basis).first(), size)}."
}

/** A unit as words, like "tbsp" or "pieces"; pieces are plural unless [quantity] is 1. */
fun logUnitLabel(unit: String, quantity: Double = 2.0): String =
    when {
        unit != "" -> unit
        quantity == 1.0 -> "piece"
        else -> "pieces"
    }

/** A typed number of servings, if the API accepts it: more than 0, at most 100. */
fun parseServings(text: String): Double? = parseAmount(text)?.takeIf { it <= MAX_SERVINGS }

/** A typed amount, if it is more than 0. */
fun parseAmount(text: String): Double? {
    if (text.isBlank()) return null
    return jsNumber(text).takeIf { it.isFinite() && it > 0 }
}

/**
 * The request that logs [what], or null while it cannot be logged. A recipe sends only itself and
 * servings: the API counts its nutrition.
 */
fun logBody(
    what: LogWhat,
    date: LocalDate,
    mealType: MealType,
    servings: String,
    nutrition: NutritionFields,
    amount: AmountFields,
): MealLogInput? =
    when (what) {
        is LogWhat.Recipe ->
            parseServings(servings)?.let {
                MealLogInput(date, mealType, recipeId = what.recipe.id, servings = it)
            }
        is MeasuredWhat -> measuredBody(measured(what), date, mealType, amount)
        is LogWhat.Custom -> customBody(what.description, date, mealType, nutrition)
    }

/**
 * Measured food logs as one serving of the amount, named with it, carrying the nutrition the form
 * previewed, so the entry is what the person saw.
 */
fun measuredBody(
    food: Measured,
    date: LocalDate,
    mealType: MealType,
    amount: AmountFields,
): MealLogInput? {
    val quantity = parseAmount(amount.quantity) ?: return null
    if (LOG_UNITS[amount.unit]?.basis != food.basis) return null
    val measure = ", ${numberText(quantity)} ${logUnitLabel(amount.unit, quantity)}"
    return MealLogInput(
        date,
        mealType,
        description = food.name.trim().take(MAX_DESCRIPTION_LENGTH - measure.length) + measure,
        servings = 1.0,
        nutrition = measuredTotal(food, quantity, amount.unit) ?: Nutrition(),
    )
}

private fun customBody(
    text: String,
    date: LocalDate,
    mealType: MealType,
    nutrition: NutritionFields,
): MealLogInput? {
    val description = text.trim()
    if (description == "") return null
    // The web's Number() of a value that is no number goes out as null, which is what this sends.
    fun value(typed: String) =
        if (typed.isBlank()) null else jsNumber(typed).takeIf { it.isFinite() }
    return MealLogInput(
        date,
        mealType,
        description = description,
        servings = 1.0,
        nutrition =
            Nutrition(
                value(nutrition.calories),
                value(nutrition.proteinG),
                value(nutrition.carbsG),
                value(nutrition.fatG),
            ),
    )
}

private fun hasNutrition(nutrition: Nutrition): Boolean =
    listOf(nutrition.calories, nutrition.proteinG, nutrition.carbsG, nutrition.fatG).any {
        it != null
    }

/**
 * What [servings] of a recipe add to the day, rounded to 0.1 as the API rounds a logged recipe;
 * null when its nutrition is unknown.
 */
fun recipeTotal(recipe: RecipeSummary, servings: Double): Nutrition? =
    recipe.nutrition.takeIf(::hasNutrition)?.let { scaled(it, servings) }

/**
 * What [quantity] of [unit] of a food adds to the day, rounded to 0.1; a value the food lacks stays
 * absent. Null when its nutrition is unknown or the unit does not fit its basis.
 */
fun measuredTotal(food: Measured, quantity: Double, unit: String): Nutrition? {
    val logUnit = LOG_UNITS[unit] ?: return null
    if (logUnit.basis != food.basis || !hasNutrition(food.nutrition)) return null
    return scaled(food.nutrition, quantity * logUnit.size / basisAmount(food.basis))
}

fun measuredTotal(entry: CatalogIngredient, quantity: Double, unit: String): Nutrition? =
    measuredTotal(Measured(entry.name, entry.basis, entry.nutrition), quantity, unit)

/** A total in words: "Comes to 675 kcal, 30 g protein, 80 g carbs, 20 g fat." */
fun describeTotal(total: Nutrition?): String {
    if (total == null) return "Nutrition unknown, so it adds nothing."
    val parts =
        listOfNotNull(
            total.calories?.let { "${it.roundToLong()} kcal" },
            total.proteinG?.let { "${it.roundToLong()} g protein" },
            total.carbsG?.let { "${it.roundToLong()} g carbs" },
            total.fatG?.let { "${it.roundToLong()} g fat" },
        )
    return "Comes to ${parts.joinToString(", ")}."
}

/** Rounds as JavaScript's `Math.round(value * 10) / 10` does: halves up. */
private fun scaled(nutrition: Nutrition, factor: Double): Nutrition {
    fun scale(value: Double?) = value?.let { (it * factor * 10).roundToLong() / 10.0 }
    return Nutrition(
        scale(nutrition.calories),
        scale(nutrition.proteinG),
        scale(nutrition.carbsG),
        scale(nutrition.fatG),
    )
}

/** "675 kcal", or a dash when unknown. */
fun formatKcal(value: Double?): String = value?.let { "${it.roundToLong()} kcal" } ?: "–"

/** The Log table's calories, protein, carbs, and fat; an unknown value is a dash. */
fun nutritionCells(nutrition: Nutrition?): List<String> {
    fun grams(value: Double?) = value?.let { "${it.roundToLong()} g" } ?: "–"
    return listOf(
        formatKcal(nutrition?.calories),
        grams(nutrition?.proteinG),
        grams(nutrition?.carbsG),
        grams(nutrition?.fatG),
    )
}
