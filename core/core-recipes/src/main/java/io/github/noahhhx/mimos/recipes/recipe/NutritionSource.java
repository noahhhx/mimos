package io.github.noahhhx.mimos.recipes.recipe;

/** Where a recipe's per-serving nutrition comes from (ADR-0015). */
public enum NutritionSource {
    /** Typed in by the recipe's author. */
    MANUAL,
    /** Calculated from the ingredient lines' catalog entries whenever the recipe is read. */
    INGREDIENTS
}
