package io.github.noahhhx.mimos.recipes

/**
 * Where the Recipes tab is. Following an ingredient that is one of your recipes pushes (ADR-0018),
 * so the route is the cook trail: [Recipe.trail] holds the recipes followed to reach [Recipe.id],
 * first one first.
 */
sealed interface RecipeRoute {
    data object List : RecipeRoute

    data class Recipe(val trail: kotlin.collections.List<RecipeId>, val id: RecipeId) : RecipeRoute

    /** The route after back, or null when this is the list and there is nowhere to go. */
    fun back(): RecipeRoute? =
        when (this) {
            List -> null
            is Recipe -> if (trail.isEmpty()) List else Recipe(trail.dropLast(1), trail.last())
        }

    /** The route after opening [target] from here. A recipe already on the trail is returned to. */
    fun follow(target: RecipeId): RecipeRoute =
        when (this) {
            List -> Recipe(emptyList(), target)
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
