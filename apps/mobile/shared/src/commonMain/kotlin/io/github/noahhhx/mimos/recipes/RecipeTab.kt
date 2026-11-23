package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.models.RecipeCollection

/**
 * What the Recipes list shows: the [Gallery] of the household's tabs, its own recipes, the library,
 * or one tab (a recipe collection, ADR-0036).
 */
sealed interface RecipeTab {
    data object Gallery : RecipeTab

    data object Mine : RecipeTab

    data object Library : RecipeTab

    data class Collection(val id: String) : RecipeTab
}

/**
 * The tab Recipes opens on when none is chosen (ADR-0036): the caller's default tab, else the
 * gallery when the household has any tab, else My recipes, as before tabs existed.
 */
fun landingTab(collections: List<RecipeCollection>): RecipeTab =
    collections.firstOrNull { it.isDefault }?.let { RecipeTab.Collection(it.id) }
        ?: if (collections.isEmpty()) RecipeTab.Mine else RecipeTab.Gallery

/**
 * The tab shown: [chosen] while it still exists among [collections], else the landing tab. A tab
 * deleted elsewhere, or the gallery once the last tab is gone, falls back to it.
 */
fun shownTab(chosen: RecipeTab?, collections: List<RecipeCollection>): RecipeTab =
    when (chosen) {
        null -> landingTab(collections)
        RecipeTab.Mine,
        RecipeTab.Library -> chosen
        RecipeTab.Gallery -> if (collections.isEmpty()) landingTab(collections) else chosen
        is RecipeTab.Collection ->
            if (collections.any { it.id == chosen.id }) chosen else landingTab(collections)
    }

/** The tab bar, in order: without tabs, exactly My recipes and Library. */
fun tabBar(collections: List<RecipeCollection>): List<RecipeTab> =
    if (collections.isEmpty()) listOf(RecipeTab.Mine, RecipeTab.Library)
    else
        listOf(RecipeTab.Gallery, RecipeTab.Mine, RecipeTab.Library) +
            collections.map { RecipeTab.Collection(it.id) }
