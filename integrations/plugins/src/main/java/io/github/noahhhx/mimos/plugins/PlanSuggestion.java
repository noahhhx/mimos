package io.github.noahhhx.mimos.plugins;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One validated suggestion card, attributed to its plugin (ADR-0006).
 * Advisory only: applying it is the user calling the existing plan-entry
 * endpoint — plugins have no write power.
 */
public record PlanSuggestion(
        String pluginId,
        String pluginName,
        String title,
        @Nullable String blurb,
        @Nullable String icon,
        List<SuggestedEntry> entries) {

    public PlanSuggestion {
        entries = List.copyOf(entries);
    }
}
