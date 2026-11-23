package io.github.noahhhx.mimos.planning.shopping;

import org.jspecify.annotations.Nullable;

/** A shopping-list item to store; its id and position are assigned when it is saved. */
public record ShoppingListItemDraft(
        String name,
        @Nullable String unit,
        @Nullable Double quantity,
        String category,
        boolean checked,
        boolean added) {}
