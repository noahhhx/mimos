package io.github.noahhhx.mimos.plugins;

import java.util.List;
import java.util.UUID;

/**
 * One curated library recipe as sent to plugins — the only recipes plugins
 * ever see (ADR-0006).
 */
public record LibraryRecipe(UUID id, String title, List<String> tags, int servings) {

    public LibraryRecipe {
        tags = List.copyOf(tags);
    }
}
