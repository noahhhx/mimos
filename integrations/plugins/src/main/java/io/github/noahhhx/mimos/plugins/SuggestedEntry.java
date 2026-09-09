package io.github.noahhhx.mimos.plugins;

import io.github.noahhhx.mimos.planning.plan.MealType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One validated proposed meal in a suggestion card. {@code recipeTitle} is
 * hydrated from core's own recipe data, so per-entry text in the UI is
 * never plugin-supplied.
 */
public record SuggestedEntry(LocalDate date, MealType mealType, UUID recipeId, String recipeTitle, double servings) {}
