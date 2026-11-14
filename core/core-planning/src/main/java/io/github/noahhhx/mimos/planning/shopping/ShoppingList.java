package io.github.noahhhx.mimos.planning.shopping;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A week's shopping list: aggregated items grouped by aisle category. */
public record ShoppingList(UUID id, LocalDate startDate, Instant generatedAt, List<ShoppingListItem> items) {

    /**
     * One line. A generated line is the total measured quantity of an
     * ingredient across the week, or null when every recipe leaves it
     * unmeasured (ADR-0007). An {@code added} line was put on the list by
     * hand, with whatever amount it was given, and regeneration keeps it
     * (ADR-0027).
     */
    public record ShoppingListItem(
            UUID id,
            String name,
            @Nullable String unit,
            @Nullable Double quantity,
            String category,
            boolean checked,
            boolean added) {}
}
