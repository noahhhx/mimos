package io.github.noahhhx.mimos.plugins;

import java.time.LocalDate;
import java.util.List;

/**
 * The request body Mimos sends to {@code POST /v1/plan-suggestions}
 * (ADR-0006): the week being planned and the library catalog. This is the
 * entire plugin-facing data surface in v1.
 */
public record SuggestionContext(
        LocalDate weekStartDate, List<PlannedSlot> plannedSlots, List<LibraryRecipe> libraryRecipes) {

    public SuggestionContext {
        plannedSlots = List.copyOf(plannedSlots);
        libraryRecipes = List.copyOf(libraryRecipes);
    }
}
