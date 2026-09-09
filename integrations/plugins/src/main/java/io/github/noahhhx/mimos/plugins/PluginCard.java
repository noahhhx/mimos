package io.github.noahhhx.mimos.plugins;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One unvalidated card from a plugin's response, exactly as the wire gave
 * it. Fields may be missing or malformed; {@link SuggestionService}
 * validates and truncates before anything reaches a user (ADR-0006).
 */
public record PluginCard(
        @Nullable String title,
        @Nullable String blurb,
        @Nullable String icon,
        List<PluginCardEntry> entries) {

    public PluginCard {
        entries = List.copyOf(entries);
    }
}
