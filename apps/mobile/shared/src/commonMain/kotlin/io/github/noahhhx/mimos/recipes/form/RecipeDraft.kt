package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.models.IngredientQuantity
import io.github.noahhhx.mimos.api.models.Nutrition
import io.github.noahhhx.mimos.api.models.NutritionEstimateInput
import io.github.noahhhx.mimos.api.models.NutritionSource
import io.github.noahhhx.mimos.api.models.RecipeDetail
import io.github.noahhhx.mimos.api.models.RecipeInput
import io.github.noahhhx.mimos.api.models.RecipeStep

/*
 * The web's recipe form values (`apps/web/src/lib/recipe-input.ts`), rule for rule and word for
 * word. `libraries/test-cases/recipe-input.json` holds the cases both sides run.
 */

/**
 * What a line counts as for calculated nutrition (ADR-0015, ADR-0018). A line is [Unlinked] until
 * typing or picking links it; [DontCount] is the web's `catalogSlug: ""`, a line its author chose
 * not to count, so its name never links it again.
 */
sealed interface RowLink {
    data object Unlinked : RowLink

    data class Entry(val slug: String) : RowLink

    data class Recipe(val id: String) : RowLink

    data object DontCount : RowLink
}

/** One ingredient line as typed. [unit] "" is a count of pieces. */
data class IngredientRow(
    val quantity: String = "",
    val unit: String = "",
    val name: String = "",
    val note: String = "",
    val link: RowLink = RowLink.Unlinked,
)

/** The recipe form as typed: every field a string, as the person left it. */
data class RecipeDraft(
    val title: String = "",
    val description: String = "",
    val servings: String = "4",
    val prepMinutes: String = "",
    val cookMinutes: String = "",
    val tags: String = "",
    val nutritionSource: NutritionSource = NutritionSource.INGREDIENTS,
    val calories: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
    val ingredients: List<IngredientRow> = listOf(IngredientRow()),
    val steps: List<String> = listOf(""),
)

/** The units the form offers: metric only (ADR-0015), with "" for a count of pieces. */
val UNITS = listOf("", "g", "kg", "ml", "l", "tsp", "tbsp")

/** The unit a line that is one of the user's recipes counts in (ADR-0018). */
const val SERVINGS = "servings"

/** The unit that counts all of a linked recipe's servings (ADR-0018). */
const val RECIPE = "recipe"

/** How a unit reads in the form: "" is "pieces". */
fun unitLabel(unit: String): String = if (unit == "") "pieces" else unit

/**
 * The unit choices for a row: the metric units, servings or the whole recipe for a row that is one
 * of the user's recipes, plus the unit an older recipe was written in ("cups"), so editing the
 * recipe keeps it.
 */
fun unitChoices(current: String, isRecipe: Boolean = false): List<String> {
    val units =
        if (isRecipe || current == SERVINGS || current == RECIPE) UNITS + SERVINGS + RECIPE
        else UNITS
    return if (current in units) units else units + current
}

/** The form for [recipe], or for a new recipe. */
fun draftOf(recipe: RecipeDetail?): RecipeDraft {
    if (recipe == null) return RecipeDraft()
    return RecipeDraft(
        title = recipe.title,
        description = recipe.description,
        servings = recipe.servings.toString(),
        prepMinutes = recipe.prepMinutes?.toString() ?: "",
        cookMinutes = recipe.cookMinutes?.toString() ?: "",
        tags = recipe.tags.joinToString(", "),
        nutritionSource = recipe.nutritionSource,
        calories = numberText(recipe.nutrition.calories),
        proteinG = numberText(recipe.nutrition.proteinG),
        carbsG = numberText(recipe.nutrition.carbsG),
        fatG = numberText(recipe.nutrition.fatG),
        ingredients =
            recipe.ingredients
                .map {
                    IngredientRow(
                        quantity = numberText(it.quantity),
                        unit = metricOrAsWritten(it.unit ?: ""),
                        name = it.name,
                        note = it.note ?: "",
                        link =
                            when {
                                !it.recipeId.isNullOrEmpty() -> RowLink.Recipe(it.recipeId)
                                !it.catalogSlug.isNullOrEmpty() -> RowLink.Entry(it.catalogSlug)
                                else -> RowLink.Unlinked
                            },
                    )
                }
                .ifEmpty { listOf(IngredientRow()) },
        steps = recipe.steps.map { it.instruction }.ifEmpty { listOf("") },
    )
}

sealed interface CheckedRecipe {
    data class Valid(val input: RecipeInput) : CheckedRecipe

    /** Every problem with the form, one per row at fault. */
    data class Invalid(val errors: List<String>) : CheckedRecipe
}

/**
 * The [RecipeInput] for this form, or every problem with it. Beyond the web's checks, it checks
 * what the web leaves to the browser's own input constraints: whole numbers in range for servings
 * and the times, and typed nutrition of 0 or more.
 */
fun RecipeDraft.toRecipeInput(): CheckedRecipe {
    val errors = mutableListOf<String>()
    if (title.isBlank()) errors += "Give the recipe a title."
    if (description.isBlank()) errors += "Give the recipe a description."
    val servingsNumber = wholeNumber(servings)
    if (servingsNumber == null || servingsNumber !in 1..50) {
        errors += "Servings must be a whole number from 1 to 50."
    }
    val prep = optionalMinutes(prepMinutes, "Prep minutes", errors)
    val cook = optionalMinutes(cookMinutes, "Cook minutes", errors)

    val lines = mutableListOf<IngredientQuantity>()
    ingredients.forEachIndexed { index, row ->
        val name = row.name.trim()
        val unit = row.unit.trim()
        val amount = row.quantity.trim()
        if (name == "" && unit == "" && amount == "") return@forEachIndexed
        val label = "Ingredient ${index + 1}"
        if (name == "") errors += "$label needs a name."
        val quantity = if (amount == "") null else jsNumber(amount)
        if (quantity != null && !(quantity.isFinite() && quantity > 0)) {
            errors += "$label's amount must be more than 0, or leave it blank for \"to taste\"."
        }
        lines +=
            IngredientQuantity(
                name = name,
                quantity = quantity,
                unit = unit.ifEmpty { null },
                note = row.note.trim().ifEmpty { null },
                catalogSlug = (row.link as? RowLink.Entry)?.slug,
                recipeId = (row.link as? RowLink.Recipe)?.id,
            )
    }
    if (lines.isEmpty()) errors += "Add at least one ingredient."

    val instructions = steps.map { it.trim() }.filter { it != "" }.map { RecipeStep(it) }
    if (instructions.isEmpty()) errors += "Add at least one step."

    if (nutritionSource == NutritionSource.MANUAL) {
        for ((label, value) in
            listOf(
                "Calories" to calories,
                "Protein" to proteinG,
                "Carbs" to carbsG,
                "Fat" to fatG,
            )) {
            val number = value.trim().takeIf { it != "" }?.let(::jsNumber)
            if (number != null && !(number.isFinite() && number >= 0)) {
                errors += "$label must be 0 or more."
            }
        }
    }

    if (errors.isNotEmpty() || servingsNumber == null) return CheckedRecipe.Invalid(errors)
    return CheckedRecipe.Valid(
        RecipeInput(
            title = title.trim(),
            description = description.trim(),
            servings = servingsNumber,
            prepMinutes = prep,
            cookMinutes = cook,
            tags = tags.split(",").map { it.trim().lowercase() }.filter { it != "" }.distinct(),
            nutrition =
                Nutrition(
                    calories = optionalNumber(calories),
                    proteinG = optionalNumber(proteinG),
                    carbsG = optionalNumber(carbsG),
                    fatG = optionalNumber(fatG),
                ),
            nutritionSource = nutritionSource,
            ingredients = lines,
            steps = instructions,
        )
    )
}

/** What the estimate is sent, and the form row each of its lines came from. */
data class EstimateRequest(val input: NutritionEstimateInput, val rows: List<Int>)

/**
 * What the nutrition estimate needs from the form, and which form row each line came from; nothing
 * until servings is a whole number from 1 to 50. Rows without a name and amounts that are not
 * positive numbers are left out, so a half-typed row never makes the estimate fail.
 */
fun RecipeDraft.toEstimateInput(): EstimateRequest? {
    val count = wholeNumber(servings)?.takeIf { it in 1..50 } ?: return null
    val rows = mutableListOf<Int>()
    val lines = mutableListOf<IngredientQuantity>()
    ingredients.forEachIndexed { index, row ->
        val name = row.name.trim()
        val amount = row.quantity.trim()
        val quantity = if (amount == "") null else jsNumber(amount)
        if (name == "" || (quantity != null && !(quantity.isFinite() && quantity > 0))) {
            return@forEachIndexed
        }
        rows += index
        lines +=
            IngredientQuantity(
                name = name,
                quantity = quantity,
                unit = row.unit.trim().ifEmpty { null },
                catalogSlug = (row.link as? RowLink.Entry)?.slug,
                recipeId = (row.link as? RowLink.Recipe)?.id,
            )
    }
    return EstimateRequest(NutritionEstimateInput(count, lines), rows)
}

/** "G", " tbsp", and "Servings" are units the form offers, written loosely; any other stays. */
private fun metricOrAsWritten(unit: String): String {
    val normalized = unit.trim().lowercase()
    return if (normalized in UNITS || normalized == SERVINGS || normalized == RECIPE) normalized
    else unit
}

private val DECIMAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

/**
 * A typed number as JavaScript's `Number` reads it, for the decimals people type: blank is 0, and
 * anything else that is not a decimal ("1,5") is NaN. Kotlin's own parse would take "1f".
 */
internal fun jsNumber(text: String): Double {
    val trimmed = text.trim()
    if (trimmed == "") return 0.0
    return if (DECIMAL.matches(trimmed)) trimmed.toDouble() else Double.NaN
}

/** A number as JavaScript's `String` writes it: 250, not 250.0; nothing for no number. */
internal fun numberText(value: Double?): String =
    when {
        value == null -> ""
        value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15 -> value.toLong().toString()
        else -> value.toString()
    }

private fun wholeNumber(text: String): Int? {
    if (text.isBlank()) return null
    val number = jsNumber(text)
    return if (number.isFinite() && number % 1.0 == 0.0 && kotlin.math.abs(number) < 1e9) {
        number.toInt()
    } else null
}

private fun optionalMinutes(text: String, label: String, errors: MutableList<String>): Int? {
    if (text.isBlank()) return null
    val minutes = wholeNumber(text)
    if (minutes == null || minutes !in 0..1440) {
        errors += "$label must be a whole number from 0 to 1440."
        return null
    }
    return minutes
}

private fun optionalNumber(text: String): Double? {
    if (text.isBlank()) return null
    return jsNumber(text).takeIf { it.isFinite() }
}
