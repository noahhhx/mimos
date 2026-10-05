package io.github.noahhhx.mimos.recipes.library;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One curated recipe as authored in {@code library-seed.json}. The seed file
 * is the library's content pipeline: edit it, restart, and the library
 * updates — content changes never need a migration (ADR-0005). Library
 * nutrition is always calculated from the lines' catalog entries
 * (ADR-0015), so a seed recipe has none of its own.
 */
public record LibrarySeed(
        String slug,
        String title,
        String description,
        int servings,
        @Nullable Integer prepMinutes,
        @Nullable Integer cookMinutes,
        List<String> tags,
        List<SeedIngredient> ingredients,
        List<String> steps) {

    public record SeedIngredient(
            @Nullable Double quantity,
            @Nullable String unit,
            String name,
            @Nullable String note,
            @Nullable String catalogSlug) {}
}
