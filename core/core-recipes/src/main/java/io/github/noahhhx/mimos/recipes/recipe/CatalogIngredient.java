package io.github.noahhhx.mimos.recipes.recipe;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An ingredient with known nutrition for one {@link NutritionBasis}, every
 * value known (ADR-0015). Shared entries are seeded for everyone; an entry
 * with an owner is that user's own (ADR-0016).
 */
public record CatalogIngredient(
        String slug,
        @Nullable UUID ownerId,
        String name,
        NutritionBasis basis,
        double calories,
        double proteinG,
        double carbsG,
        double fatG) {

    public CatalogIngredient {
        if (slug.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("a catalog ingredient needs a slug and a name");
        }
        if (!(calories >= 0 && proteinG >= 0 && carbsG >= 0 && fatG >= 0)
                || Double.isInfinite(calories + proteinG + carbsG + fatG)) {
            throw new IllegalArgumentException("nutrition values must be numbers, zero or more");
        }
    }

    public boolean isShared() {
        return ownerId == null;
    }

    /** Whether this owner may link recipe lines to the entry: it is shared, or theirs. */
    public boolean isVisibleTo(@Nullable UUID viewerOwnerId) {
        return ownerId == null || ownerId.equals(viewerOwnerId);
    }

    public Nutrition nutrition() {
        return new Nutrition(calories, proteinG, carbsG, fatG);
    }
}
