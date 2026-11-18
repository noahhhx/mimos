package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.models.Nutrition
import kotlin.math.roundToLong

/*
 * The web's recipe text (`apps/web/src/lib/format.ts`), word for word, so a recipe reads the same
 * in both. `libraries/test-cases/recipe-text.json` holds the cases both sides run.
 */

private fun isWholeRecipeUnit(unit: String?): Boolean =
    unit?.trim()?.lowercase().let { it == "recipe" || it == "recipes" }

private fun roundQuantity(quantity: Double): Double = (quantity * 100).roundToLong() / 100.0

private fun numberText(rounded: Double): String {
    val cents = (rounded * 100).roundToLong()
    if (cents % 100 == 0L) return (cents / 100).toString()
    val text = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    return if (text.endsWith("0")) text.dropLast(1) else text
}

/**
 * "2 cups flour" style amounts. An unmeasured ingredient ("salt, to taste") has no quantity: it
 * formats as its unit alone, or as nothing. A whole recipe reads "2x", and one serving of a recipe
 * reads "1 serving".
 */
fun formatQuantity(quantity: Double?, unit: String?): String {
    val wholeRecipe = isWholeRecipeUnit(unit)
    if (quantity == null) return if (wholeRecipe) "" else unit ?: ""
    val rounded = roundQuantity(quantity)
    val number = numberText(rounded)
    if (wholeRecipe) return "${number}x"
    if (unit.isNullOrEmpty()) return number
    return "$number ${if (rounded == 1.0 && unit.trim().lowercase() == "servings") "serving" else unit}"
}

/** A parenthetical that names what a piece is, so it takes the plural: "Rye bread (slices)". */
private val COUNTED_IN = setOf("slice", "rasher", "piece", "sheet", "strip", "wedge")

private val IRREGULAR_PLURALS =
    mapOf(
        "half" to "halves",
        "knife" to "knives",
        "leaf" to "leaves",
        "loaf" to "loaves",
        "potato" to "potatoes",
        "tomato" to "tomatoes",
    )

private fun pluralWord(word: String): String {
    IRREGULAR_PLURALS[word.lowercase()]?.let {
        return word.take(1) + it.drop(1)
    }
    return when {
        Regex("s$", RegexOption.IGNORE_CASE).containsMatchIn(word) -> word
        Regex("(ch|sh|x|z)$", RegexOption.IGNORE_CASE).containsMatchIn(word) -> "${word}es"
        Regex("[^aeiou]y$", RegexOption.IGNORE_CASE).containsMatchIn(word) ->
            "${word.dropLast(1)}ies"
        else -> "${word}s"
    }
}

private val COUNTED_PARENTHETICAL = Regex("""\((\w+)\)""")
private val QUALIFIER = Regex(""",|\s\(|\s(?:of|on|in|with)\s""", RegexOption.IGNORE_CASE)
private val LAST_WORD = Regex("""(\S+)(\s*)$""")

/**
 * An ingredient's name as it reads after its amount: a count of pieces (no unit) other than one
 * takes the plural, "2 Eggs" but "1 Egg". The plural goes on the head noun, before any qualifier:
 * "Chicken thighs, bone-in", "Corns on the cob", "Rye bread (slices)".
 */
fun countedName(name: String, quantity: Double?, unit: String?): String {
    if (!unit.isNullOrBlank() || quantity == null || roundQuantity(quantity) == 1.0) return name
    val counted = COUNTED_PARENTHETICAL.find(name)
    if (counted != null && counted.groupValues[1].lowercase() in COUNTED_IN) {
        return name.replaceFirst(counted.value, "(${pluralWord(counted.groupValues[1])})")
    }
    val end = QUALIFIER.find(name)?.range?.first ?: name.length
    val head =
        name.substring(0, end).replace(LAST_WORD) {
            pluralWord(it.groupValues[1]) + it.groupValues[2]
        }
    return head + name.substring(end)
}

/** "1 serving", "2 servings", "1.5 servings". */
fun formatServings(servings: Int): String =
    "$servings ${if (servings == 1) "serving" else "servings"}"

/** Whether any per-serving value is known: absent values are unknown, not zero. */
fun Nutrition.isKnown(): Boolean = listOf(calories, proteinG, carbsG, fatG).any { it != null }

/** "Serves 4 · 10 min prep · 15 min cook", leaving out a time the recipe does not give. */
fun servesLine(servings: Int, prepMinutes: Int?, cookMinutes: Int?): String = buildString {
    append("Serves $servings")
    if (prepMinutes != null) append(" · $prepMinutes min prep")
    if (cookMinutes != null) append(" · $cookMinutes min cook")
}

/** One per-serving figure: "412 kcal", "30 g", or the dash for an unknown value. */
fun figure(value: Double?, unit: String): String =
    if (value != null) "${value.roundToLong()} $unit" else "–"
