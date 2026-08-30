package io.github.noahhhx.mimos.recipes.recipe;

import org.jspecify.annotations.Nullable;

/**
 * Per-serving nutrition for a recipe (or totals for a logged meal). Absent
 * values mean unknown, not zero.
 */
public record Nutrition(
        @Nullable Double calories,
        @Nullable Double proteinG,
        @Nullable Double carbsG,
        @Nullable Double fatG) {

    public static final Nutrition UNKNOWN = new Nutrition(null, null, null, null);
}
