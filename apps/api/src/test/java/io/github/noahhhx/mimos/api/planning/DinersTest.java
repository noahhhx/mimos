package io.github.noahhhx.mimos.api.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.noahhhx.mimos.planning.plan.MealType;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DinersTest {

    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID PARTNER = UUID.randomUUID();
    private static final Set<UUID> HOUSEHOLD = Set.of(CALLER, PARTNER);

    @Test
    void unnamedDinersAreEveryoneAtDinnerAndTheCallerOtherwise() {
        assertThat(Diners.forNewEntry(MealType.DINNER, null, HOUSEHOLD, CALLER)).isEqualTo(HOUSEHOLD);
        for (MealType other : List.of(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK)) {
            assertThat(Diners.forNewEntry(other, null, HOUSEHOLD, CALLER))
                    .as(other.name())
                    .containsExactly(CALLER);
        }
    }

    @Test
    void namedDinersWinOverTheDefault() {
        assertThat(Diners.forNewEntry(MealType.DINNER, List.of(PARTNER), HOUSEHOLD, CALLER))
                .containsExactly(PARTNER);
        assertThat(Diners.forNewEntry(MealType.LUNCH, List.of(PARTNER, CALLER, PARTNER), HOUSEHOLD, CALLER))
                .containsExactlyInAnyOrder(CALLER, PARTNER);
    }

    @Test
    void noDinerOrANonMemberIsRefused() {
        assertThatThrownBy(() -> Diners.requireMembers(List.of(), HOUSEHOLD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A planned meal needs at least one diner.");
        assertThatThrownBy(() -> Diners.requireMembers(List.of(CALLER, UUID.randomUUID()), HOUSEHOLD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Every diner must be a member of your household.");
        assertThatThrownBy(() -> Diners.requireMembers(Arrays.asList(CALLER, null), HOUSEHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
