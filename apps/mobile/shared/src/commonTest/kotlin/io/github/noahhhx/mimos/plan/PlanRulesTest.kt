package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.MealLogInput
import io.github.noahhhx.mimos.api.models.MealType
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.api.models.PlannedFood
import io.github.noahhhx.mimos.api.models.PlannedFoodInput
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable private class SizedCase(val mealType: MealType, val members: Int, val expected: Int)

@Serializable
private class ToggleCase(val diners: List<String>, val member: String, val expected: List<String>?)

@Serializable private class ChipCase(val names: List<String>, val expected: List<String>)

@Serializable
private class AmountCase(
    val recipeTitle: String? = null,
    val food: PlannedFood? = null,
    val servings: Double,
    val expected: String,
)

@Serializable private class ExpectedFood(val food: PlannedFoodInput, val servings: Double)

@Serializable
private class NewFoodCase(
    val basis: NutritionBasis,
    val servingSize: Double? = null,
    val mealType: MealType,
    val members: Int,
    val expected: ExpectedFood,
)

@Serializable private class ParseCase(val text: String, val expected: Double?)

@Serializable private class NoteCase(val mealsWithoutNutrition: Int, val expected: String?)

@Serializable
private class FoodLogCase(
    val about: String,
    val food: PlannedFood,
    val catalog: List<String>,
    val amount: Double,
    val expected: MealLogInput? = null,
    val logsNothing: Boolean = false,
)

@Serializable
private class PlanCases(
    val defaultServingsCases: List<SizedCase>,
    val defaultDinerCountCases: List<SizedCase>,
    val toggleDinerCases: List<ToggleCase>,
    val chipLabelCases: List<ChipCase>,
    val amountTextCases: List<AmountCase>,
    val newFoodEntryCases: List<NewFoodCase>,
    val parseFoodAmountCases: List<ParseCase>,
    val withoutNutritionNoteCases: List<NoteCase>,
    val foodLogCatalog: List<CatalogIngredient>,
    val foodLogCases: List<FoodLogCase>,
)

private val cases = MimosJson.decodeFromString<PlanCases>(TestCases.read("plan.json"))

/** The cases are the web's too (`libraries/test-cases`). */
class PlanRulesTest {
    @Test
    fun `a new meal starts at the web's servings and diners`() {
        assertTrue(cases.defaultServingsCases.isNotEmpty())
        for (case in cases.defaultServingsCases) {
            assertEquals(case.expected, defaultServings(case.mealType, case.members), "$case")
        }
        for (case in cases.defaultDinerCountCases) {
            assertEquals(case.expected, defaultDinerCount(case.mealType, case.members), "$case")
        }
    }

    @Test
    fun `toggling a diner never leaves a meal with nobody`() {
        for (case in cases.toggleDinerCases) {
            assertEquals(case.expected, toggleDiner(case.diners, case.member), case.member)
        }
    }

    @Test
    fun `each member's chip reads as on the web`() {
        for (case in cases.chipLabelCases) {
            assertEquals(case.expected, chipLabels(case.names))
        }
    }

    @Test
    fun `a planned amount reads as on the web`() {
        for (case in cases.amountTextCases) {
            val what =
                case.food?.let { PlannedWhat.Food(it) }
                    ?: PlannedWhat.Recipe("r1", case.recipeTitle!!)
            assertEquals(case.expected, amountText(what, case.servings))
        }
    }

    @Test
    fun `a new food plans one portion per default diner`() {
        for (case in cases.newFoodEntryCases) {
            val planned =
                newFoodEntry("x", case.basis, case.servingSize, case.mealType, case.members)
            assertEquals(case.expected.food, planned.food, "$case")
            assertEquals(case.expected.servings, planned.servings, "$case")
        }
    }

    @Test
    fun `a typed food amount is taken only when the API would take it`() {
        for (case in cases.parseFoodAmountCases) {
            assertEquals(case.expected, parseFoodAmount(case.text), "'${case.text}'")
        }
    }

    @Test
    fun `meals with unknown nutrition are noted`() {
        for (case in cases.withoutNutritionNoteCases) {
            assertEquals(case.expected, withoutNutritionNote(case.mealsWithoutNutrition))
        }
    }

    @Test
    fun `a planned food logs as the web logs it`() {
        for (case in cases.foodLogCases) {
            val catalog = cases.foodLogCatalog.filter { it.slug in case.catalog }
            val body =
                foodLogBody(
                    LocalDate(2026, 11, 16),
                    MealType.SNACK,
                    case.food,
                    catalog,
                    case.amount,
                )
            if (case.logsNothing) assertEquals(null, body, case.about)
            else assertEquals(case.expected, body, case.about)
        }
    }

    @Test
    fun `a share is at least 0 point 1 and at most what the API takes`() {
        val recipe = PlannedWhat.Recipe("r", "Stew")
        val food = PlannedWhat.Food(PlannedFood("bagel", "Bagel", "g"))
        assertEquals(1.5, parseShare("1.5", recipe))
        assertEquals(0.1, parseShare("0.1", recipe))
        assertEquals(null, parseShare("0.05", recipe))
        assertEquals(null, parseShare("100.5", recipe))
        assertEquals(250.0, parseShare("250", food))
        assertEquals(null, parseShare("10001", food))
        assertEquals(null, parseShare("", food))
        assertEquals(null, parseShare("2f", food))
    }

    @Test
    fun `a week starts on its Monday and names its days`() {
        val thursday = LocalDate(2026, 11, 19)
        val monday = mondayOf(thursday)
        assertEquals(LocalDate(2026, 11, 16), monday)
        assertEquals(monday, mondayOf(monday))
        assertEquals(LocalDate(2026, 11, 16), mondayOf(LocalDate(2026, 11, 22)))
        assertEquals(7, weekDays(monday).size)
        assertEquals("Mon 16 Nov", dayLabel(monday))
        assertEquals("Sunday", weekdayName(weekDays(monday).last()))
    }
}
