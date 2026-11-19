package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.NutritionBasis
import io.github.noahhhx.mimos.recipes.collationKey
import io.github.noahhhx.mimos.recipes.compareKeys
import kotlin.math.roundToLong

/*
 * The web's catalog matching (`apps/web/src/lib/catalog-match.ts`), rule for rule and word for
 * word: searching the ingredient catalog as the author types, keeping a link while the line's name
 * still names its entry, and describing an entry (ADR-0015, ADR-0016).
 * `libraries/test-cases/catalog-match.json` holds the cases both sides run.
 */

/** The units a basis counts in, as the form names them. */
fun unitsOf(basis: NutritionBasis): String =
    when (basis) {
        NutritionBasis.PER_100_G -> "g or kg"
        NutritionBasis.PER_100_ML -> "ml, l, tsp or tbsp"
        NutritionBasis.PER_PIECE -> "pieces"
        NutritionBasis.unknown_default_open_api -> "its own units"
    }

private fun perBasis(basis: NutritionBasis): String =
    when (basis) {
        NutritionBasis.PER_100_G -> "per 100 g"
        NutritionBasis.PER_100_ML -> "per 100 ml"
        NutritionBasis.PER_PIECE -> "each"
        NutritionBasis.unknown_default_open_api -> ""
    }

/** Entries with a word in their name that starts with what the author typed, best first. */
fun searchCatalog(
    query: String,
    catalog: List<CatalogIngredient>,
    limit: Int = 8,
): List<CatalogIngredient> = searchByName(query, catalog, { it.name }, limit)

/**
 * Items with a word in their name that starts with what the author typed, best first: names that
 * start with it, then the rest, each by name as the web's `localeCompare` orders them.
 */
fun <T> searchByName(query: String, items: List<T>, nameOf: (T) -> String, limit: Int): List<T> {
    val text = normalize(query)
    if (text == "") return emptyList()
    fun rank(item: T): Int {
        val name = normalize(nameOf(item))
        return when {
            name.startsWith(text) -> 0
            startsAWord(name, text) -> 1
            else -> -1
        }
    }
    return items
        .map { Ranked(it, rank(it), collationKey(nameOf(it))) }
        .filter { it.rank >= 0 }
        .sortedWith { a, b ->
            a.rank.compareTo(b.rank).takeIf { it != 0 } ?: compareKeys(a.key, b.key)
        }
        .take(limit)
        .map { it.item }
}

private class Ranked<T>(val item: T, val rank: Int, val key: IntArray)

/** Whether [phrase] appears in [text] at the start of a word, ignoring case and spacing. */
fun containsAtWordStart(text: String, phrase: String): Boolean {
    val wanted = normalize(phrase)
    return wanted != "" && startsAWord(normalize(text), wanted)
}

/**
 * The catalog entry a line's name most likely means: the one with the longest name or slug found in
 * it at a word start ("garlic cloves" is a garlic clove; "parsley" is fresh parsley by its slug;
 * "olive oil" is olive oil, not oil).
 */
fun suggestCatalogSlug(name: String, catalog: List<CatalogIngredient>): String? {
    val text = normalize(name)
    var best: String? = null
    var bestLength = 0
    for (entry in catalog) {
        for (phrase in phrasesOf(entry)) {
            if (phrase.length > bestLength && startsAWord(text, phrase)) {
                best = entry.slug
                bestLength = phrase.length
            }
        }
    }
    return best
}

/** Whether a line's name still names the entry, so "garlic clove" to "garlic cloves" keeps it. */
fun mentions(name: String, entry: CatalogIngredient): Boolean {
    val text = normalize(name)
    return phrasesOf(entry).any { startsAWord(text, it) }
}

private val QUALIFIERS = Regex("""\(.*\)|,.*$""")

/** The name a line takes when its author picks an entry: "Red lentils, dry" is "Red lentils". */
fun lineNameOf(entry: CatalogIngredient): String = entry.name.replace(QUALIFIERS, "").trim()

/** An entry's calories in words, like "813 kcal per 100 ml" or "4 kcal each". */
fun describeEntry(entry: CatalogIngredient): String =
    "${(entry.nutrition.calories ?: 0.0).roundToLong()} kcal ${perBasis(entry.basis)}"

/** The basis a new ingredient most likely has, from the unit its line is measured in. */
fun basisForUnit(unit: String): NutritionBasis {
    val normalized = normalize(unit)
    return when {
        normalized == "" -> NutritionBasis.PER_PIECE
        normalized in listOf("ml", "l", "tsp", "tbsp") -> NutritionBasis.PER_100_ML
        else -> NutritionBasis.PER_100_G
    }
}

private fun phrasesOf(entry: CatalogIngredient): List<String> =
    listOf(normalize(lineNameOf(entry)), normalize(entry.slug.replace("-", " "))).filter {
        it != ""
    }

private val SPACES = Regex("""\s+""")

private fun normalize(value: String): String = value.lowercase().replace(SPACES, " ").trim()

private fun startsAWord(text: String, phrase: String): Boolean {
    var at = text.indexOf(phrase)
    while (at != -1) {
        if (at == 0 || text[at - 1] !in 'a'..'z') return true
        at = text.indexOf(phrase, at + 1)
    }
    return false
}
