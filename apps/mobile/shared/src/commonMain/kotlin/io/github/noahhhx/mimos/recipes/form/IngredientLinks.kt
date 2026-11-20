package io.github.noahhhx.mimos.recipes.form

import io.github.noahhhx.mimos.api.models.CatalogIngredient
import io.github.noahhhx.mimos.api.models.IngredientLineStatus
import io.github.noahhhx.mimos.api.models.RecipeSummary
import io.github.noahhhx.mimos.recipes.formatServings
import kotlin.math.roundToLong

/*
 * The web's ingredient links (`apps/web/src/lib/ingredient-links.ts`), rule for rule and word for
 * word: what a row counts as for calculated nutrition, a catalog entry (ADR-0015, ADR-0016) or one
 * of the user's own recipes in servings (ADR-0018), and how typing and picking change it. A recipe
 * is only ever linked by picking it, never by its name. `libraries/test-cases/ingredient-links.json`
 * holds the cases both sides run.
 */

/** A row's link, resolved among what the form has loaded. */
sealed interface LineLink {
    data class Entry(val entry: CatalogIngredient) : LineLink

    data class Recipe(val recipe: RecipeSummary) : LineLink
}

/** The row's link, among what the form has loaded; none for a link to something not loaded. */
fun linkOf(
    row: IngredientRow,
    bySlug: Map<String, CatalogIngredient>,
    byRecipeId: Map<String, RecipeSummary>,
): LineLink? =
    when (val link = row.link) {
        is RowLink.Recipe -> byRecipeId[link.id]?.let(LineLink::Recipe)
        is RowLink.Entry -> bySlug[link.slug]?.let(LineLink::Entry)
        RowLink.Unlinked,
        RowLink.DontCount -> null
    }

/** The user's recipes whose title has a word that starts with what was typed, best first. */
fun searchRecipes(
    query: String,
    recipes: List<RecipeSummary>,
    limit: Int = 4,
): List<RecipeSummary> = searchByName(query, recipes, { it.title }, limit)

/** A recipe as a search option's second line, like "Your recipe · 278 kcal per serving". */
fun describeRecipe(recipe: RecipeSummary): String {
    val n = recipe.nutrition
    val calories = n.calories
    val known = calories != null && n.proteinG != null && n.carbsG != null && n.fatG != null
    val whose = if (recipe.isLibrary) "Library" else "Your recipe"
    return "$whose · ${if (known) "${calories.roundToLong()} kcal per serving" else "nutrition unknown"}"
}

/** What a linked row counts as, in words for the form. */
fun describeLink(link: LineLink): String =
    when (link) {
        is LineLink.Entry -> "Matched to ${link.entry.name} (${describeEntry(link.entry)})."
        is LineLink.Recipe ->
            "Uses your recipe ${link.recipe.title} (makes ${formatServings(link.recipe.servings)})."
    }

/** Why a linked row adds nothing, in words for the form; nothing for a row that counted. */
fun lineHint(status: IngredientLineStatus?, link: LineLink?): String? {
    if (link == null) return null
    return when (status) {
        IngredientLineStatus.UNMEASURED -> "No amount, so it adds nothing."
        IngredientLineStatus.UNIT_NOT_SUPPORTED ->
            when (link) {
                is LineLink.Entry ->
                    "Not counted. ${link.entry.name} counts in ${unitsOf(link.entry.basis)}."
                is LineLink.Recipe ->
                    "Not counted. A recipe counts in servings or as a whole recipe."
            }
        IngredientLineStatus.NUTRITION_UNKNOWN ->
            (link as? LineLink.Recipe)?.let {
                "Not counted. ${it.recipe.title}'s nutrition isn't known yet."
            }
        else -> null
    }
}

/**
 * Links a row to a catalog entry, keeping what was typed when it already names the entry ("garlic
 * cloves, crushed"), in the entry's own casing when it is just the entry's name.
 */
fun pickEntry(row: IngredientRow, entry: CatalogIngredient): IngredientRow =
    row.copy(
        name = pickedName(row.name, lineNameOf(entry), mentions(row.name, entry)),
        link = RowLink.Entry(entry.slug),
    )

/**
 * Links a row to one of the user's recipes, measured in servings of it, keeping what was typed when
 * it already names the recipe.
 */
fun pickRecipe(row: IngredientRow, recipe: RecipeSummary): IngredientRow =
    row.copy(
        name = pickedName(row.name, recipe.title, containsAtWordStart(row.name, recipe.title)),
        unit = SERVINGS,
        link = RowLink.Recipe(recipe.id),
    )

/** A row its author chose not to count, so it is not linked again by name. */
fun dontCount(row: IngredientRow): IngredientRow = row.copy(link = RowLink.DontCount)

/**
 * A row after its name is typed. It keeps its link while the name still names what it links
 * ("garlic clove" to "garlic cloves"), and a recipe link while the recipe is still loading.
 * Otherwise it links the catalog entry its name names, unless its author chose not to count it.
 */
fun retype(
    row: IngredientRow,
    name: String,
    catalog: List<CatalogIngredient>,
    bySlug: Map<String, CatalogIngredient>,
    byRecipeId: Map<String, RecipeSummary>,
): IngredientRow {
    when (val link = row.link) {
        RowLink.DontCount -> return row.copy(name = name)
        is RowLink.Recipe -> {
            val recipe = byRecipeId[link.id]
            if (recipe == null || containsAtWordStart(name, recipe.title)) {
                return row.copy(name = name)
            }
        }
        is RowLink.Entry -> {
            val entry = bySlug[link.slug]
            if (entry != null && mentions(name, entry)) return row.copy(name = name)
        }
        RowLink.Unlinked -> {}
    }
    val slug = suggestCatalogSlug(name, catalog)
    return row.copy(name = name, link = slug?.let(RowLink::Entry) ?: RowLink.Unlinked)
}

/** What a picked row is called: what was typed when it names the pick and says more. */
private fun pickedName(typed: String, name: String, named: Boolean): String {
    val same = typed.trim().lowercase() == name.lowercase()
    return if (named && !same) typed else name
}
