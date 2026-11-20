package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.models.RecipeSummary

/**
 * The recipes list's orders, with the web's keys and labels (`apps/web/src/lib/recipe-order.ts`).
 */
enum class RecipeSort(val key: String, val label: String) {
    Title("title", "Title, A to Z"),
    Newest("newest", "Newest first"),
    Oldest("oldest", "Oldest first"),
    Edited("edited", "Recently edited");

    companion object {
        val default = Title

        /** The sort named [key], or [default] for anything else. */
        fun parse(key: String?): RecipeSort = entries.firstOrNull { it.key == key } ?: default
    }
}

/**
 * [recipes] that have every one of [selectedTags], in [sort] order. Titles compare as the web's do
 * (see [collationKey]), ties go by id, and a date sort compares instants to the millisecond, then
 * falls back to the title.
 */
fun orderRecipes(
    recipes: List<RecipeSummary>,
    sort: RecipeSort,
    selectedTags: Set<String>,
): List<RecipeSummary> {
    val keyed =
        recipes
            .filter { recipe -> selectedTags.all { it in recipe.tags } }
            .map { KeyedRecipe(it, collationKey(it.title)) }
    val byTitle =
        Comparator<KeyedRecipe> { a, b ->
            compareKeys(a.titleKey, b.titleKey).takeIf { it != 0 }
                ?: a.recipe.id.compareTo(b.recipe.id)
        }
    val byInstant = { direction: Int, instant: (RecipeSummary) -> Long ->
        Comparator<KeyedRecipe> { a, b ->
            (direction * instant(a.recipe).compareTo(instant(b.recipe))).takeIf { it != 0 }
                ?: byTitle.compare(a, b)
        }
    }
    val comparator =
        when (sort) {
            RecipeSort.Title -> byTitle
            RecipeSort.Newest -> byInstant(-1) { it.createdAt.toEpochMilliseconds() }
            RecipeSort.Oldest -> byInstant(1) { it.createdAt.toEpochMilliseconds() }
            RecipeSort.Edited -> byInstant(-1) { it.updatedAt.toEpochMilliseconds() }
        }
    return keyed.sortedWith(comparator).map { it.recipe }
}

/** Every tag of [recipes] once, in the order the tag filter lists them. */
fun recipeTags(recipes: List<RecipeSummary>): List<String> =
    recipes
        .flatMap { it.tags }
        .distinct()
        .map { it to collationKey(it) }
        .sortedWith { a, b -> compareKeys(a.second, b.second) }
        .map { it.first }

/** The selected tags that [available] still offers: a tag from the other tab is not applied. */
fun activeTags(available: List<String>, selected: Set<String>): Set<String> =
    selected.filterTo(LinkedHashSet()) { it in available }

private class KeyedRecipe(val recipe: RecipeSummary, val titleKey: IntArray)
