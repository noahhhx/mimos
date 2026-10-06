package io.github.noahhhx.mimos.recipes.recipe;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A recipe: curated library recipe (no owner, public slug) or personal
 * recipe, owned by an opaque owner id and recording the profile that
 * created it (null when that profile is gone, and for library recipes).
 * Nutrition is per serving: typed in, or calculated from the ingredient
 * lines when the source is {@link NutritionSource#INGREDIENTS} (ADR-0015).
 */
public record Recipe(
        UUID id,
        @Nullable UUID ownerId,
        @Nullable UUID createdByProfileId,
        @Nullable String slug,
        String title,
        String description,
        int servings,
        @Nullable Integer prepMinutes,
        @Nullable Integer cookMinutes,
        Nutrition nutrition,
        NutritionSource nutritionSource,
        List<String> tags,
        List<Ingredient> ingredients,
        List<RecipeStep> steps,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isLibrary() {
        return ownerId == null;
    }
}
