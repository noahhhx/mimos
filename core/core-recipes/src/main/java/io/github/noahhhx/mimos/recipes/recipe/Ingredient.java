package io.github.noahhhx.mimos.recipes.recipe;

import org.jspecify.annotations.Nullable;

/**
 * One ingredient line: an amount, an optional unit, a name, and an
 * optional prep note ("minced", "to serve"). A null quantity is an
 * unmeasured ingredient ("salt, to taste"). A catalog slug names the
 * catalog entry the line is, for calculated nutrition (ADR-0015).
 */
public record Ingredient(
        @Nullable Double quantity,
        @Nullable String unit,
        String name,
        @Nullable String note,
        @Nullable String catalogSlug) {}
