package io.github.noahhhx.mimos.recipes

/**
 * Where the Recipes tab is. Following an ingredient that is one of your recipes pushes (ADR-0018),
 * so the route is the cook trail: [Recipe.trail] holds the recipes followed to reach [Recipe.id],
 * first one first. The form is [New], from the list, or [Edit], from the recipe it edits. The tab
 * form (ADR-0036) is [NewTab] or [EditTab], both from the list.
 */
sealed interface RecipeRoute {
    data object List : RecipeRoute

    data class Recipe(val trail: kotlin.collections.List<RecipeId>, val id: RecipeId) : RecipeRoute

    data object New : RecipeRoute

    data class Edit(val from: Recipe) : RecipeRoute

    data object NewTab : RecipeRoute

    data class EditTab(val collectionId: String) : RecipeRoute

    /** The route after back, or null when this is the list and there is nowhere to go. */
    fun back(): RecipeRoute? =
        when (this) {
            List -> null
            is Recipe -> if (trail.isEmpty()) List else Recipe(trail.dropLast(1), trail.last())
            New,
            NewTab,
            is EditTab -> List
            is Edit -> from
        }

    /** The route after opening [target] from here. A recipe already on the trail is returned to. */
    fun follow(target: RecipeId): RecipeRoute =
        when (this) {
            List,
            New,
            NewTab,
            is EditTab -> Recipe(emptyList(), target)
            is Edit -> from.follow(target)
            is Recipe -> {
                val seen = trail.indexOf(target)
                when {
                    target == id -> this
                    seen >= 0 -> Recipe(trail.take(seen), target)
                    else -> Recipe(trail + id, target)
                }
            }
        }
}
