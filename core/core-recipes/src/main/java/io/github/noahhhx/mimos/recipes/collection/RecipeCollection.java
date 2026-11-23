package io.github.noahhhx.mimos.recipes.collection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A household's recipe collection as one person sees it (ADR-0036). Its
 * members are the picked {@code recipeIds} and every recipe carrying any of
 * its {@code tags}, among the household's recipes and the library;
 * {@code recipeCount} counts them. {@code isDefault} is that person's own
 * choice of the collection Recipes opens on.
 */
public record RecipeCollection(
        UUID id,
        UUID ownerId,
        String name,
        List<String> tags,
        List<UUID> recipeIds,
        @Nullable UUID pictureId,
        Instant createdAt,
        boolean isDefault,
        int recipeCount) {}
