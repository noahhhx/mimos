package io.github.noahhhx.mimos.planning.plan;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One planned meal, with its title resolved for display (the recipe's
 * title or the ingredient's name). {@code amount} is what is planned for
 * all its diners together: servings of the recipe, or the food's quantity
 * in its unit (pieces without one). The diners are profile ids, never
 * empty.
 */
public record PlannedMeal(
        UUID id,
        LocalDate date,
        MealType mealType,
        PlannedItem item,
        String title,
        double amount,
        Set<UUID> dinerProfileIds) {

    /** How much of the meal is {@code profileId}'s to eat, or {@code null} when they are not one of its diners. */
    public @Nullable Double shareOf(UUID profileId) {
        return dinerProfileIds.contains(profileId) ? PlannedDayTotal.shareOf(amount, dinerProfileIds.size()) : null;
    }
}
