package io.github.noahhhx.mimos.recipes.recipe;

import java.util.List;

/**
 * Calculated per-serving nutrition and, for each ingredient line in order,
 * whether it counted (ADR-0015).
 */
public record NutritionEstimate(Nutrition perServing, List<LineStatus> lines) {

    public enum LineStatus {
        COUNTED,
        /** Linked, but has no amount ("salt, to taste"). */
        UNMEASURED,
        NOT_LINKED,
        /** Linked, but its unit is not one its link counts: its catalog entry's basis, or servings of a recipe. */
        UNIT_NOT_SUPPORTED,
        /** Linked to a recipe whose per-serving nutrition is not fully known (ADR-0018). */
        NUTRITION_UNKNOWN
    }
}
