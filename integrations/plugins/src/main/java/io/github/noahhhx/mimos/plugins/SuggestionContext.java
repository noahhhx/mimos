package io.github.noahhhx.mimos.plugins;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The request body Mimos sends to {@code POST /v1/plan-suggestions}
 * (ADR-0006): the user's pseudonym for this plugin (ADR-0017), the week
 * being planned, and the library catalog. This is the entire
 * plugin-facing data surface for suggestions.
 */
public record SuggestionContext(
        UUID subject, LocalDate weekStartDate, List<PlannedSlot> plannedSlots, List<LibraryRecipe> libraryRecipes) {

    public SuggestionContext {
        plannedSlots = List.copyOf(plannedSlots);
        libraryRecipes = List.copyOf(libraryRecipes);
    }
}
