package io.github.noahhhx.mimos.planning.plan;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What a plan entry plans (ADR-0026): exactly one of a recipe and a single
 * food. A food is an ingredient by its catalog slug, shared or the owner's
 * own; a null unit counts pieces.
 */
public sealed interface PlannedItem {

    record Recipe(UUID recipeId) implements PlannedItem {}

    record Food(String catalogSlug, @Nullable String unit) implements PlannedItem {}
}
