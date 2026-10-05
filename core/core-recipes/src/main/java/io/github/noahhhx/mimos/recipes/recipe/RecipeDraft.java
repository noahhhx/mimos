package io.github.noahhhx.mimos.recipes.recipe;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A recipe as submitted for creation or replacement, before validation. */
public record RecipeDraft(
        String title,
        String description,
        int servings,
        @Nullable Integer prepMinutes,
        @Nullable Integer cookMinutes,
        List<String> tags,
        Nutrition nutrition,
        NutritionSource nutritionSource,
        List<Ingredient> ingredients,
        List<RecipeStep> steps) {}
