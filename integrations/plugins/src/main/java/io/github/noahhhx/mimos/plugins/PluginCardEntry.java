package io.github.noahhhx.mimos.plugins;

import org.jspecify.annotations.Nullable;

/**
 * One unvalidated proposed meal from a plugin card: every field may be
 * {@code null} or malformed until {@link SuggestionService} checks it.
 */
public record PluginCardEntry(
        @Nullable String date,
        @Nullable String mealType,
        @Nullable String recipeId,
        @Nullable Double servings) {}
