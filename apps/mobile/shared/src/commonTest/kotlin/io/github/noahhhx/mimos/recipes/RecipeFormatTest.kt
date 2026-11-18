package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private class QuantityCase(val quantity: Double?, val unit: String?, val expected: String)

@Serializable
private class NameCase(
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val expected: String,
)

@Serializable private class ServingsCase(val servings: Int, val expected: String)

@Serializable
private class TextCases(
    val quantityCases: List<QuantityCase>,
    val nameCases: List<NameCase>,
    val servingsCases: List<ServingsCase>,
)

private val cases = Json.decodeFromString<TextCases>(TestCases.read("recipe-text.json"))

/** The cases are the web's too (`libraries/test-cases`). */
class RecipeFormatTest {
    @Test
    fun `every shared case reads as the web's does`() {
        assertTrue(cases.quantityCases.isNotEmpty() && cases.nameCases.isNotEmpty())
        for (case in cases.quantityCases) {
            assertEquals(
                case.expected,
                formatQuantity(case.quantity, case.unit),
                "${case.quantity} ${case.unit}",
            )
        }
        for (case in cases.nameCases) {
            assertEquals(case.expected, countedName(case.name, case.quantity, case.unit), case.name)
        }
        for (case in cases.servingsCases) {
            assertEquals(case.expected, formatServings(case.servings), "${case.servings}")
        }
    }

    @Test
    fun `nutrition is known when any one value is`() {
        assertTrue(Nutrition(carbsG = 40.0).isKnown())
        assertTrue(Nutrition(fatG = 0.0).isKnown())
        assertFalse(Nutrition().isKnown())
    }

    @Test
    fun `the serves line leaves out a time it does not have`() {
        assertEquals("Serves 4 · 10 min prep · 15 min cook", servesLine(4, 10, 15))
        assertEquals("Serves 2 · 20 min cook", servesLine(2, null, 20))
        assertEquals("Serves 1", servesLine(1, null, null))
    }

    @Test
    fun `figures round, and an unknown value is a dash`() {
        assertEquals("412 kcal", figure(411.6, "kcal"))
        assertEquals("–", figure(null, "g"))
    }
}
