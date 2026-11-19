package io.github.noahhhx.mimos.barcode

import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.FoodProduct
import io.github.noahhhx.mimos.api.models.IngredientInput
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.ingredients.NewIngredientDraft
import io.github.noahhhx.mimos.log.productName
import io.github.noahhhx.mimos.recipes.form.numberText

/*
 * Barcodes on packaged food and the products they find (ADR-0025, ADR-0026), ported from the web's
 * `barcode.ts` and `planned-item.ts` word for word. `libraries/test-cases/barcode.json` holds the
 * cases both sides run.
 */

private val SEPARATORS = Regex("""[\s-]""")
private val BARCODE = Regex("""\d{8,14}""")

/**
 * A barcode's digits as the lookup takes them: spaces and dashes dropped, then 8 to 14 digits
 * (EAN-8, UPC-A, EAN-13, GTIN-14), or not a barcode.
 */
fun parseBarcode(text: String): String? =
    text.replace(SEPARATORS, "").takeIf { BARCODE.matches(it) }

/** The product's page on Open Food Facts, which the attribution links to. */
fun openFoodFactsUrl(barcode: String): String = "https://world.openfoodfacts.org/product/$barcode"

/**
 * Why a lookup found nothing: a 404 is a barcode Open Food Facts does not know; else it is down.
 */
fun lookupFailure(status: Int?): String =
    if (status == 404) "Open Food Facts doesn't know that barcode. Log it by hand instead."
    else "Open Food Facts didn't answer. Try again, or log it by hand."

private const val MAX_NAME_LENGTH = 100

/**
 * The household ingredient a scanned product becomes when planned, named with its brand; null when
 * a value is missing, so the person fills it in first.
 */
fun productIngredient(product: FoodProduct): IngredientInput? {
    val (calories, proteinG, carbsG, fatG) = product.nutrition
    if (calories == null || proteinG == null || carbsG == null || fatG == null) return null
    return IngredientInput(
        productName(product).take(MAX_NAME_LENGTH),
        product.basis,
        Nutrition(calories, proteinG, carbsG, fatG),
    )
}

/** The household's own ingredient that already is [input], so scanning one twice adds it once. */
fun existingIngredient(
    input: IngredientInput,
    ingredients: List<CatalogIngredient>,
): CatalogIngredient? = ingredients.find {
    !it.isShared &&
        it.name == input.name &&
        it.basis == input.basis &&
        it.nutrition == input.nutrition
}

/**
 * A new ingredient for a product missing a value: what Open Food Facts knew, the rest to fill in.
 */
fun productDraft(product: FoodProduct): NewIngredientDraft =
    NewIngredientDraft(
        productName(product).take(MAX_NAME_LENGTH),
        product.basis,
        calories = numberText(product.nutrition.calories),
        proteinG = numberText(product.nutrition.proteinG),
        carbsG = numberText(product.nutrition.carbsG),
        fatG = numberText(product.nutrition.fatG),
    )
