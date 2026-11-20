package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.api.models.NutritionSource
import io.github.noahhhx.mimos.api.models.RecipeSummary
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/*
 * How the shared cases in `libraries/test-cases` write the form's values (README there): rows as
 * the web holds them, with `catalogSlug: ""` for a line not to count.
 */

@Serializable
internal data class RowSketch(
    val quantity: String,
    val unit: String,
    val name: String,
    val note: String = "",
    val catalogSlug: String? = null,
    val recipeId: String? = null,
) {
    fun toRow() =
        IngredientRow(
            quantity = quantity,
            unit = unit,
            name = name,
            note = note,
            link =
                when {
                    !recipeId.isNullOrEmpty() -> RowLink.Recipe(recipeId)
                    catalogSlug == "" -> RowLink.DontCount
                    catalogSlug != null -> RowLink.Entry(catalogSlug)
                    else -> RowLink.Unlinked
                },
        )
}

@Serializable
internal data class DraftSketch(
    val title: String,
    val description: String,
    val servings: String,
    val prepMinutes: String,
    val cookMinutes: String,
    val tags: String,
    val nutritionSource: NutritionSource,
    val calories: String,
    val proteinG: String,
    val carbsG: String,
    val fatG: String,
    val ingredients: List<RowSketch>,
    val steps: List<String>,
) {
    fun toDraft() =
        RecipeDraft(
            title,
            description,
            servings,
            prepMinutes,
            cookMinutes,
            tags,
            nutritionSource,
            calories,
            proteinG,
            carbsG,
            fatG,
            ingredients.map { it.toRow() },
            steps,
        )
}

@Serializable
internal data class EntrySketch(
    val slug: String,
    val name: String,
    val basis: NutritionBasis,
    val calories: Double,
    val isShared: Boolean = true,
) {
    fun toEntry() =
        CatalogIngredient(
            slug = slug,
            name = name,
            isShared = isShared,
            basis = basis,
            nutrition = Nutrition(calories, 0.0, 0.0, 0.0),
        )
}

@Serializable
internal data class RecipeSketch(
    val id: String,
    val title: String,
    val servings: Int,
    val nutrition: Nutrition,
) {
    fun toSummary(isLibrary: Boolean = false) =
        RecipeSummary(
            id = id,
            title = title,
            description = "",
            servings = servings,
            isLibrary = isLibrary,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
            tags = emptyList(),
            nutrition = nutrition,
        )
}

/** [base] with [patch]'s fields in place of its own, as the web's `{ ...base, ...patch }`. */
internal fun JsonObject.patched(patch: JsonObject) = JsonObject(this + patch)

/**
 * Whether [actual], as the app sends it, is [expected], as the web sends it: numbers compare by
 * value (the app writes 250.0 where the web writes 250), everything else exactly.
 */
internal fun assertSameJson(expected: JsonElement, actual: JsonElement, at: String) {
    when (expected) {
        is JsonObject -> {
            if (actual !is JsonObject) fail("$at: expected an object, got $actual")
            assertEquals(expected.keys, actual.keys, "$at: fields")
            for ((key, value) in expected) assertSameJson(value, actual.getValue(key), "$at.$key")
        }
        is JsonArray -> {
            if (actual !is JsonArray) fail("$at: expected a list, got $actual")
            assertEquals(expected.size, actual.size, "$at: length of $actual")
            expected.forEachIndexed { i, value -> assertSameJson(value, actual[i], "$at[$i]") }
        }
        JsonNull -> assertEquals<JsonElement>(JsonNull, actual, at)
        is JsonPrimitive -> {
            assertTrue(actual is JsonPrimitive && actual !is JsonNull, "$at: expected $expected")
            assertEquals(expected.isString, actual.isString, "$at: $actual is not $expected")
            if (!expected.isString && expected.doubleOrNull != null) {
                assertEquals(expected.doubleOrNull, actual.doubleOrNull, at)
            } else {
                assertEquals(expected, actual, at)
            }
        }
    }
}
