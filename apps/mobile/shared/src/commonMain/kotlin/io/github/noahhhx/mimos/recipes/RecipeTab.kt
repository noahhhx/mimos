package io.github.noahhhx.mimos.recipes

import io.github.noahhhx.mimos.api.models.RecipeCollection

sealed interface RecipeTab {
    data object Gallery : RecipeTab

    sealed interface Listing : RecipeTab

    data object Mine : Listing

    data object Library : Listing

    data class Collection(val id: String) : Listing
}

fun landingTab(collections: List<RecipeCollection>): RecipeTab =
    collections.firstOrNull { it.isDefault }?.let { RecipeTab.Collection(it.id) }
        ?: if (collections.isEmpty()) RecipeTab.Mine else RecipeTab.Gallery

fun shownTab(chosen: RecipeTab?, collections: List<RecipeCollection>): RecipeTab =
    when (chosen) {
        null -> landingTab(collections)
        RecipeTab.Mine,
        RecipeTab.Library -> chosen
        RecipeTab.Gallery -> if (collections.isEmpty()) landingTab(collections) else chosen
        is RecipeTab.Collection ->
            if (collections.any { it.id == chosen.id }) chosen else landingTab(collections)
    }

fun tabBar(collections: List<RecipeCollection>): List<RecipeTab> =
    if (collections.isEmpty()) listOf(RecipeTab.Mine, RecipeTab.Library)
    else
        listOf(RecipeTab.Gallery, RecipeTab.Mine, RecipeTab.Library) +
            collections.map { RecipeTab.Collection(it.id) }
