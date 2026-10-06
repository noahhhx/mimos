package io.github.noahhhx.mimos.api.planning;

import io.github.noahhhx.mimos.planning.plan.MealType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Who eats a planned meal (ADR-0019). core-planning only requires one
 * diner, since it knows no households; membership and the defaults are
 * decided here, against the caller's household.
 */
final class Diners {

    private Diners() {}

    /**
     * A new entry's diners: the requested ones, or when none are named,
     * every member for a dinner and the caller alone for any other meal.
     */
    static Set<UUID> forNewEntry(
            MealType mealType, @Nullable List<UUID> requested, Set<UUID> memberIds, UUID callerId) {
        if (requested == null) {
            return mealType == MealType.DINNER ? Set.copyOf(memberIds) : Set.of(callerId);
        }
        return requireMembers(requested, memberIds);
    }

    /** The requested diners, when there is at least one and every one is a member. */
    static Set<UUID> requireMembers(List<UUID> requested, Set<UUID> memberIds) {
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("A planned meal needs at least one diner.");
        }
        // JSON can carry a null inside the list, whatever the generated type says.
        for (UUID id : requested) {
            if (id == null || !memberIds.contains(id)) {
                throw new IllegalArgumentException("Every diner must be a member of your household.");
            }
        }
        return Set.copyOf(requested);
    }
}
