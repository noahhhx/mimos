package io.github.noahhhx.mimos.planning.logging;

import io.github.noahhhx.mimos.planning.plan.MealType;
import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One logged meal with the calorie/macro totals actually eaten. */
public record MealLog(
        UUID id,
        LocalDate date,
        MealType mealType,
        @Nullable UUID recipeId,
        String description,
        double servings,
        Nutrition nutrition,
        Instant loggedAt) {}
