package io.github.noahhhx.mimos.planning.shopping;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A week's shopping list: aggregated items grouped by aisle category. */
public record ShoppingList(UUID id, LocalDate startDate, Instant generatedAt, List<ShoppingListItem> items) {

    /**
     * One aggregated line: total measured quantity of an ingredient across
     * the week, or null when every recipe leaves it unmeasured (ADR-0007).
     */
    public record ShoppingListItem(
            UUID id,
            String name,
            @Nullable String unit,
            @Nullable Double quantity,
            String category,
            boolean checked) {}
}
