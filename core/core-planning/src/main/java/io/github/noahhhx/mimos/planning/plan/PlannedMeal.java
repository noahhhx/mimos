package io.github.noahhhx.mimos.planning.plan;

import java.time.LocalDate;
import java.util.UUID;

/** One planned meal, with the recipe's title resolved for display. */
public record PlannedMeal(
        UUID id, LocalDate date, MealType mealType, UUID recipeId, String recipeTitle, double servings) {}
