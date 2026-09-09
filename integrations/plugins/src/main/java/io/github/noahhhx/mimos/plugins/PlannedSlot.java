package io.github.noahhhx.mimos.plugins;

import io.github.noahhhx.mimos.planning.plan.MealType;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One meal already in the plan, as sent to plugins (ADR-0006): the slot's
 * shape only. {@code recipeId} is present just where the planned recipe is
 * itself a library recipe — personal recipes contribute their shape, never
 * their identity.
 */
public record PlannedSlot(
        LocalDate date,
        MealType mealType,
        double servings,
        @Nullable UUID recipeId) {}
