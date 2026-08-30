package io.github.noahhhx.mimos.planning.logging;

import io.github.noahhhx.mimos.planning.plan.MealType;
import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A meal to log: from a recipe (nutrition derived) or ad-hoc (manual totals). */
public record MealLogDraft(
        LocalDate date,
        MealType mealType,
        @Nullable UUID recipeId,
        double servings,
        @Nullable String description,
        Nutrition nutrition) {}
