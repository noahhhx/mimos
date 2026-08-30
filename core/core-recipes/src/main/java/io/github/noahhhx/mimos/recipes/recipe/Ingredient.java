package io.github.noahhhx.mimos.recipes.recipe;

import org.jspecify.annotations.Nullable;

/** One ingredient line: an amount, an optional unit, and a name. */
public record Ingredient(double quantity, @Nullable String unit, String name) {}
