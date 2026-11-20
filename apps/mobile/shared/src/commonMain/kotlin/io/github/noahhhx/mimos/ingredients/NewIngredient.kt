package io.github.noahhhx.mimos.ingredients

import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.IngredientInput
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.recipes.collationKey
import io.github.noahhhx.mimos.recipes.compareKeys
import io.github.noahhhx.mimos.recipes.form.jsNumber
import kotlinx.coroutines.CancellationException

/** A new ingredient of the household's own (ADR-0016), as typed into [NewIngredientPanel]. */
data class NewIngredientDraft(
    val name: String,
    val basis: NutritionBasis,
    val calories: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

sealed interface CheckedIngredient {
    data class Valid(val input: IngredientInput) : CheckedIngredient

    data class Invalid(val message: String) : CheckedIngredient
}

/** What the API would be sent, or what to say first about the draft. */
fun NewIngredientDraft.checkInput(): CheckedIngredient {
    val values = listOf(calories, proteinG, carbsG, fatG)
    val numbers = values.map(::jsNumber)
    return when {
        name.isBlank() -> CheckedIngredient.Invalid("Give the ingredient a name.")
        values.any { it.isBlank() } || numbers.any { !(it >= 0) } ->
            CheckedIngredient.Invalid("Fill in all four values, using 0 when there is none.")
        else ->
            CheckedIngredient.Valid(
                IngredientInput(
                    name = name.trim(),
                    basis = basis,
                    nutrition = Nutrition(numbers[0], numbers[1], numbers[2], numbers[3]),
                )
            )
    }
}

sealed interface SavedIngredient {
    data class Created(val ingredient: CatalogIngredient) : SavedIngredient

    data class Failed(val message: String) : SavedIngredient
}

suspend fun RecipesApi.saveIngredient(input: IngredientInput): SavedIngredient =
    try {
        SavedIngredient.Created(createIngredient(input).body())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        SavedIngredient.Failed(
            if (failure is ApiProblem) failure.detail ?: "The ingredient could not be saved."
            else "Could not reach the server."
        )
    }

/** [catalog] with [created] in it, in the order the catalog is listed. */
fun withIngredient(
    catalog: List<CatalogIngredient>,
    created: CatalogIngredient,
): List<CatalogIngredient> =
    (catalog + created).sortedWith { a, b ->
        compareKeys(collationKey(a.name), collationKey(b.name))
    }
