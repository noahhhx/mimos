package io.github.noahhhx.mimos.recipes.recipe;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A recipe: curated library recipe (no owner, public slug) or personal
 * recipe (owned by a profile). Nutrition is per serving.
 */
public record Recipe(
        UUID id,
        @Nullable UUID ownerProfileId,
        @Nullable String slug,
        String title,
        String description,
        int servings,
        @Nullable Integer prepMinutes,
        @Nullable Integer cookMinutes,
        Nutrition nutrition,
        List<String> tags,
        List<Ingredient> ingredients,
        List<RecipeStep> steps,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isLibrary() {
        return ownerProfileId == null;
    }
}
