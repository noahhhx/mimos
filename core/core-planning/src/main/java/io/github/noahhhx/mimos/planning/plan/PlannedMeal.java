package io.github.noahhhx.mimos.planning.plan;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

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
        Set<UUID> dinerProfileIds) {}
