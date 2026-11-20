package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.models.NutritionSource
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
private class DraftInputCase(val name: String, val patch: JsonObject, val expected: JsonObject)

@Serializable
private class DraftCase(
    val name: String,
    val recipe: RecipeDetail?,
    val expected: DraftSketch,
    val input: JsonObject? = null,
)

@Serializable
private class EstimateCase(val name: String, val patch: JsonObject, val expected: JsonElement)

@Serializable
private class UnitChoiceCase(val current: String, val isRecipe: Boolean, val expected: List<String>)

@Serializable
private class DraftCases(
    val base: JsonObject,
    val inputCases: List<DraftInputCase>,
    val draftCases: List<DraftCase>,
    val estimateCases: List<EstimateCase>,
    val unitChoiceCases: List<UnitChoiceCase>,
)

private val cases = MimosJson.decodeFromString<DraftCases>(TestCases.read("recipe-input.json"))

private fun draft(patch: JsonObject = JsonObject(emptyMap())): RecipeDraft =
    MimosJson.decodeFromJsonElement<DraftSketch>(cases.base.patched(patch)).toDraft()

/** The fields [expected] names, as the API is sent them; the rest are another case's. */
private fun assertFields(expected: JsonObject, actual: CheckedRecipe, case: String) {
    assertIs<CheckedRecipe.Valid>(actual, case)
    val sent = MimosJson.encodeToJsonElement(actual.input).jsonObject
    for ((field, value) in expected) {
        assertSameJson(value, sent[field] ?: JsonNull, "$case: $field")
    }
}

/** The cases are the web's too (`libraries/test-cases`). */
class RecipeDraftTest {
    @Test
    fun `every shared input case sends or refuses as the web's does`() {
        assertTrue(cases.inputCases.isNotEmpty())
        for (case in cases.inputCases) {
            val result = draft(case.patch).toRecipeInput()
            val errors = case.expected["errors"]
            if (errors != null) {
                assertEquals(
                    MimosJson.decodeFromJsonElement<List<String>>(errors),
                    (result as? CheckedRecipe.Invalid)?.errors,
                    case.name,
                )
            } else {
                assertFields(case.expected.getValue("input").jsonObject, result, case.name)
            }
        }
    }

    @Test
    fun `every shared recipe reads into the form as the web's does`() {
        for (case in cases.draftCases) {
            val form = draftOf(case.recipe)
            assertEquals(case.expected.toDraft(), form, case.name)
            case.input?.let { assertFields(it, form.toRecipeInput(), case.name) }
        }
    }

    @Test
    fun `every shared estimate case sends what the web's does, from the same rows`() {
        for (case in cases.estimateCases) {
            val request = draft(case.patch).toEstimateInput()
            if (case.expected is JsonObject) {
                assertSameJson(
                    case.expected,
                    JsonObject(
                        mapOf(
                            "input" to MimosJson.encodeToJsonElement(request?.input),
                            "rows" to MimosJson.encodeToJsonElement(request?.rows),
                        )
                    ),
                    case.name,
                )
            } else {
                assertNull(request, case.name)
            }
        }
    }

    @Test
    fun `every shared unit choice case offers the web's units`() {
        for (case in cases.unitChoiceCases) {
            assertEquals(case.expected, unitChoices(case.current, case.isRecipe), case.current)
        }
        assertEquals("pieces", unitLabel(""))
        assertEquals("g", unitLabel("g"))
    }

    @Test
    fun `servings and the times must be whole numbers in range, which the web leaves to the browser`() {
        val result =
            draft().copy(servings = "2.5", prepMinutes = "-5", cookMinutes = "1441").toRecipeInput()
        assertEquals(
            listOf(
                "Servings must be a whole number from 1 to 50.",
                "Prep minutes must be a whole number from 0 to 1440.",
                "Cook minutes must be a whole number from 0 to 1440.",
            ),
            (result as? CheckedRecipe.Invalid)?.errors,
        )
        assertEquals(
            listOf("Servings must be a whole number from 1 to 50."),
            (draft().copy(servings = "").toRecipeInput() as? CheckedRecipe.Invalid)?.errors,
        )
        val whole = draft().copy(servings = "50", prepMinutes = "1440").toRecipeInput()
        assertIs<CheckedRecipe.Valid>(whole)
        assertEquals(50, whole.input.servings)
        assertEquals(1440, whole.input.prepMinutes)
    }

    @Test
    fun `typed nutrition must be 0 or more, but only when it is typed in`() {
        val typed = draft().copy(calories = "-1", fatG = "x", proteinG = "0")
        assertEquals(
            listOf("Calories must be 0 or more.", "Fat must be 0 or more."),
            (typed.copy(nutritionSource = NutritionSource.MANUAL).toRecipeInput()
                    as? CheckedRecipe.Invalid)
                ?.errors,
        )
        assertIs<CheckedRecipe.Valid>(typed.toRecipeInput())
    }

    @Test
    fun `numbers read and write as JavaScript's do`() {
        assertEquals(250.0, jsNumber(" 250 "))
        assertEquals(0.5, jsNumber(".5"))
        assertEquals(1000.0, jsNumber("1e3"))
        assertTrue(jsNumber("1,5").isNaN())
        assertTrue(jsNumber("1f").isNaN())
        assertEquals("250", numberText(250.0))
        assertEquals("0.5", numberText(0.5))
        assertEquals("", numberText(null))
    }
}
