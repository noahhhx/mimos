package io.github.noahhhx.mimos.log

import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.FoodProduct
import io.github.noahhhx.mimos.api.models.MealLogInput
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
private class LogRecipeSketch(val id: String, val title: String, val nutrition: Nutrition)

@Serializable
private class WhatSketch(
    val custom: String? = null,
    val recipe: String? = null,
    val ingredient: String? = null,
    val product: String? = null,
)

@Serializable
private class TypedNutrition(
    val calories: String,
    val proteinG: String,
    val carbsG: String,
    val fatG: String,
)

@Serializable private class TypedAmount(val quantity: String, val unit: String)

@Serializable
private class LogBodyCase(
    val about: String,
    val what: WhatSketch,
    val mealType: MealType = MealType.SNACK,
    val servings: String = "1",
    val nutrition: TypedNutrition? = null,
    val amount: TypedAmount? = null,
    val expected: MealLogInput? = null,
    val logsNothing: Boolean = false,
    val description: String? = null,
)

@Serializable private class ServingsCase(val text: String, val expected: Double?)

@Serializable
private class RecipeTotalCase(
    val about: String,
    val recipe: String,
    val servings: Double,
    val total: Nutrition?,
    val text: String,
)

@Serializable
private class MeasuredTotalCase(
    val ingredient: String,
    val quantity: Double,
    val unit: String,
    val total: Nutrition?,
    val text: String? = null,
)

@Serializable
private class MeasuredCase(
    val about: String,
    val what: WhatSketch,
    val name: String,
    val basis: NutritionBasis,
    val amount: TypedAmount,
    val hint: String? = null,
)

@Serializable private class WhatTextCase(val about: String, val what: WhatSketch, val text: String)

@Serializable
private class SearchCase(
    val about: String,
    val query: String,
    val recipes: List<String>,
    val ingredients: List<String>,
    val expected: List<String>,
)

@Serializable private class SearchLimit(val query: String, val count: Int, val limit: Int)

@Serializable private class DescribeCase(val ingredient: String, val text: String)

@Serializable private class CellsCase(val nutrition: Nutrition?, val cells: List<String>)

@Serializable
private class LogCases(
    val date: LocalDate,
    val recipes: Map<String, LogRecipeSketch>,
    val ingredients: Map<String, CatalogIngredient>,
    val products: Map<String, FoodProduct>,
    val logBodyCases: List<LogBodyCase>,
    val servingsCases: List<ServingsCase>,
    val recipeTotalCases: List<RecipeTotalCase>,
    val measuredTotalCases: List<MeasuredTotalCase>,
    val unitsFor: Map<NutritionBasis, List<String>>,
    val measuredCases: List<MeasuredCase>,
    val whatTextCases: List<WhatTextCase>,
    val searchCases: List<SearchCase>,
    val searchLimit: SearchLimit,
    val describeIngredientCases: List<DescribeCase>,
    val nutritionCellsCases: List<CellsCase>,
)

private val cases = MimosJson.decodeFromString<LogCases>(TestCases.read("log-entry.json"))

private fun recipe(key: String): RecipeSummary {
    val sketch = cases.recipes.getValue(key)
    return RecipeSummary(
        id = sketch.id,
        title = sketch.title,
        description = "",
        servings = 4,
        isLibrary = true,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
        tags = emptyList(),
        nutrition = sketch.nutrition,
    )
}

private fun what(sketch: WhatSketch): LogWhat =
    when {
        sketch.recipe != null -> LogWhat.Recipe(recipe(sketch.recipe))
        sketch.ingredient != null ->
            LogWhat.Ingredient(cases.ingredients.getValue(sketch.ingredient))
        sketch.product != null -> LogWhat.Product(cases.products.getValue(sketch.product))
        else -> typeWhat(sketch.custom ?: "")
    }

private fun key(option: WhatOption): String =
    when (option) {
        is LogWhat.Recipe -> "recipe:${option.recipe.id}"
        is LogWhat.Ingredient -> "ingredient:${option.entry.slug}"
    }

class LogRulesTest {
    @Test
    fun `the form logs what the web logs`() {
        for (case in cases.logBodyCases) {
            val body =
                logBody(
                    what(case.what),
                    cases.date,
                    case.mealType,
                    case.servings,
                    case.nutrition?.let {
                        NutritionFields(it.calories, it.proteinG, it.carbsG, it.fatG)
                    } ?: NutritionFields(),
                    case.amount?.let { AmountFields(it.quantity, it.unit) }
                        ?: AmountFields("1", "g"),
                )
            if (case.logsNothing) assertEquals(null, body, case.about)
            case.expected?.let { assertEquals(it, body, case.about) }
            case.description?.let { assertEquals(it, body?.description, case.about) }
        }
    }

    @Test
    fun `servings run from more than 0 to 100`() {
        for (case in cases.servingsCases) {
            assertEquals(case.expected, parseServings(case.text), case.text)
        }
    }

    @Test
    fun `What shows what was picked or typed`() {
        for (case in cases.whatTextCases) {
            assertEquals(case.text, whatText(what(case.what)), case.about)
        }
    }

    @Test
    fun `a recipe's servings come to the web's total`() {
        for (case in cases.recipeTotalCases) {
            val total = recipeTotal(recipe(case.recipe), case.servings)
            assertEquals(case.total, total, case.about)
            assertEquals(case.text, describeTotal(total), case.about)
        }
    }

    @Test
    fun `an amount of food comes to the web's total`() {
        for (case in cases.measuredTotalCases) {
            val label = "${case.quantity} ${case.unit} of ${case.ingredient}"
            val total =
                measuredTotal(cases.ingredients.getValue(case.ingredient), case.quantity, case.unit)
            assertEquals(case.total, total, label)
            case.text?.let { assertEquals(it, describeTotal(total), label) }
        }
    }

    @Test
    fun `food is offered in its basis's units and starts where the web starts`() {
        for ((basis, units) in cases.unitsFor) assertEquals(units, unitsFor(basis), basis.name)
        for (case in cases.measuredCases) {
            val food = measuredOf(what(case.what))!!
            assertEquals(case.name, food.name, case.about)
            assertEquals(case.name, whatText(what(case.what)), case.about)
            assertEquals(case.basis, food.basis, case.about)
            assertEquals(AmountFields(case.amount.quantity, case.amount.unit), defaultAmount(food))
            assertEquals(case.hint, servingHint(food), case.about)
        }
    }

    @Test
    fun `the What search ranks as the web's`() {
        for (case in cases.searchCases) {
            val options =
                searchWhat(
                    case.query,
                    case.recipes.map(::recipe),
                    case.ingredients.map { cases.ingredients.getValue(it) },
                )
            assertEquals(case.expected, options.map(::key), case.about)
        }
        val limit = cases.searchLimit
        val many =
            (0 until limit.count).map {
                cases.ingredients.getValue("sugar").copy(slug = "s$it", name = "${limit.query} $it")
            }
        assertEquals(limit.limit, searchWhat(limit.query, emptyList(), many).size)
        for (case in cases.describeIngredientCases) {
            assertEquals(case.text, describeIngredient(cases.ingredients.getValue(case.ingredient)))
        }
    }

    @Test
    fun `the week table reads as the web's`() {
        for (case in cases.nutritionCellsCases) {
            assertEquals(case.cells, nutritionCells(case.nutrition))
        }
    }
}
