package io.github.noahhhx.mimos.recipes.recipe;

import org.jspecify.annotations.Nullable;

/**
 * One ingredient line: an amount, an optional unit, and a name. A null
 * quantity is an unmeasured ingredient ("salt, to taste").
 */
public record Ingredient(
        @Nullable Double quantity, @Nullable String unit, String name) {}
