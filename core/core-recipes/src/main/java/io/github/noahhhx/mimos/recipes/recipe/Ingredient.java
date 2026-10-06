package io.github.noahhhx.mimos.recipes.recipe;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One ingredient line: an amount, an optional unit, a name, and an
 * optional prep note ("minced", "to serve"). A null quantity is an
 * unmeasured ingredient ("salt, to taste"). For calculated nutrition a line
 * links at most one thing: the catalog entry it is, by slug (ADR-0015), or
 * one of its owner's own recipes, counted in servings (ADR-0018).
 */
public record Ingredient(
        @Nullable Double quantity,
        @Nullable String unit,
        String name,
        @Nullable String note,
        @Nullable String catalogSlug,
        @Nullable UUID recipeId) {

    public Ingredient {
        if (catalogSlug != null && recipeId != null) {
            throw new IllegalArgumentException(
                    "ingredient \"" + name + "\" links both a catalog entry and a recipe; a line links at most one");
        }
    }
}
