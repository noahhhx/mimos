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
        /** Linked, but its unit is not one its catalog entry's basis counts. */
        UNIT_NOT_SUPPORTED
    }
}
