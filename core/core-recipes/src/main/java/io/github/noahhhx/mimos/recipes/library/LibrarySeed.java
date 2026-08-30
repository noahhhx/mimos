package io.github.noahhhx.mimos.recipes.library;

import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One curated recipe as authored in {@code library-seed.json}. The seed file
 * is the library's content pipeline: edit it, restart, and the library
 * updates — content changes never need a migration (ADR-0005).
 */
public record LibrarySeed(
        String slug,
        String title,
        String description,
        int servings,
        @Nullable Integer prepMinutes,
        @Nullable Integer cookMinutes,
        List<String> tags,
        Nutrition nutrition,
        List<SeedIngredient> ingredients,
        List<String> steps) {

    public record SeedIngredient(double quantity, @Nullable String unit, String name) {}
}
