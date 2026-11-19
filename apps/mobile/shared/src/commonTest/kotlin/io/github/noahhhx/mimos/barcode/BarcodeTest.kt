package io.github.noahhhx.mimos.barcode

import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.FoodProduct
import io.github.noahhhx.mimos.api.models.IngredientInput
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable

@Serializable private class ParseCase(val about: String, val text: String, val expected: String?)

@Serializable private class FailureCase(val status: Int?, val message: String)

@Serializable private class ProductPage(val barcode: String, val url: String)

@Serializable private class NameLimit(val length: Int, val limit: Int)

@Serializable
private class ExistingCase(
    val about: String,
    val isShared: Boolean,
    val calories: Double,
    val found: Boolean,
)

@Serializable
private class BarcodeCases(
    val parseCases: List<ParseCase>,
    val lookupFailureCases: List<FailureCase>,
    val productPage: ProductPage,
    val product: FoodProduct,
    val productIngredient: IngredientInput,
    val nameLimit: NameLimit,
    val existingIngredientCases: List<ExistingCase>,
)

private val cases = MimosJson.decodeFromString<BarcodeCases>(TestCases.read("barcode.json"))

class BarcodeTest {
    @Test
    fun `a typed barcode is read as the web reads it`() {
        for (case in cases.parseCases) assertEquals(
            case.expected,
            parseBarcode(case.text),
            case.about,
        )
    }

    @Test
    fun `a failed lookup says why, and a product links its page`() {
        for (case in cases.lookupFailureCases) {
            assertEquals(case.message, lookupFailure(case.status), "${case.status}")
        }
        assertEquals(cases.productPage.url, openFoodFactsUrl(cases.productPage.barcode))
    }

    @Test
    fun `a product with all four values becomes an ingredient named with its brand`() {
        assertEquals(cases.productIngredient, productIngredient(cases.product))
        val noFat = cases.product.copy(nutrition = cases.product.nutrition.copy(fatG = null))
        assertEquals(null, productIngredient(noFat))
        val long = cases.product.copy(name = "x".repeat(cases.nameLimit.length))
        assertEquals(cases.nameLimit.limit, productIngredient(long)?.name?.length)
    }

    @Test
    fun `the household's own ingredient a product made is found again, and no other`() {
        val input = productIngredient(cases.product)!!
        for (case in cases.existingIngredientCases) {
            val own =
                CatalogIngredient(
                    "nutella-k3",
                    input.name,
                    case.isShared,
                    input.basis,
                    input.nutrition.copy(calories = case.calories),
                )
            assertEquals(
                if (case.found) own else null,
                existingIngredient(input, listOf(own)),
                case.about,
            )
        }
    }

    @Test
    fun `a product missing a value opens a draft with what is known`() {
        val noFat = cases.product.copy(nutrition = cases.product.nutrition.copy(fatG = null))
        val draft = productDraft(noFat)
        assertEquals("Nutella (Ferrero)", draft.name)
        assertEquals("539", draft.calories)
        assertEquals("6.3", draft.proteinG)
        assertEquals("", draft.fatG)
    }
}
