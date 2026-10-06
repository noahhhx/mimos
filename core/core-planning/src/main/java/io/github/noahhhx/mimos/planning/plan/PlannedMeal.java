package io.github.noahhhx.mimos.planning.plan;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * One planned meal, with the recipe's title resolved for display.
 * {@code servings} is the amount cooked for all its diners together; the
 * diners are profile ids, never empty.
 */
public record PlannedMeal(
        UUID id,
        LocalDate date,
        MealType mealType,
        UUID recipeId,
        String recipeTitle,
        double servings,
        Set<UUID> dinerProfileIds) {}
