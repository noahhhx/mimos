package io.github.noahhhx.mimos.fueling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class FuelProfileTest {

    @Test
    void acceptsEveryValueOnItsBounds() {
        assertThatCode(() -> profile(30, 3.0, 50, 1.2)).doesNotThrowAnyException();
        assertThatCode(() -> profile(250, 60.0, 600, 3.0)).doesNotThrowAnyException();
        assertThatCode(() -> profile(70, null, null, 1.6)).doesNotThrowAnyException();
    }

    @Test
    void refusesAWeightOutOfBounds() {
        assertRefused(() -> profile(29.9, null, null, 1.6), "weightKg must be between 30 and 250");
        assertRefused(() -> profile(250.1, null, null, 1.6), "weightKg must be between 30 and 250");
        assertRefused(() -> profile(Double.NaN, null, null, 1.6), "weightKg must be between 30 and 250");
    }

    @Test
    void refusesBodyFatOutOfBounds() {
        assertRefused(() -> profile(70, 2.9, null, 1.6), "bodyFatPercent must be between 3 and 60");
        assertRefused(() -> profile(70, 60.1, null, 1.6), "bodyFatPercent must be between 3 and 60");
    }

    @Test
    void refusesAnFtpOutOfBounds() {
        assertRefused(() -> profile(70, null, 49, 1.6), "ftpWatts must be between 50 and 600");
        assertRefused(() -> profile(70, null, 601, 1.6), "ftpWatts must be between 50 and 600");
    }

    @Test
    void refusesProteinOutOfBounds() {
        assertRefused(() -> profile(70, null, null, 1.19), "proteinGPerKg must be between 1.2 and 3.0");
        assertRefused(() -> profile(70, null, null, 3.01), "proteinGPerKg must be between 1.2 and 3.0");
    }

    @Test
    void namesEveryValueOutOfBoundsAtOnce() {
        assertThatThrownBy(() -> profile(10, 80.0, null, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("weightKg must be between 30 and 250; bodyFatPercent must be between 3 and 60;"
                        + " proteinGPerKg must be between 1.2 and 3.0");
    }

    @Test
    void eachGoalCarriesItsLevelAndDefaultProtein() {
        assertThat(Goal.FUEL.energyAvailabilityKcalPerKgFfm()).isEqualTo(45);
        assertThat(Goal.FUEL.defaultProteinGPerKg()).isEqualTo(1.6);
        assertThat(Goal.GRADUAL_LOSS.energyAvailabilityKcalPerKgFfm()).isEqualTo(40);
        assertThat(Goal.GRADUAL_LOSS.defaultProteinGPerKg()).isEqualTo(2.0);
        assertThat(Goal.FASTER_LOSS.energyAvailabilityKcalPerKgFfm()).isEqualTo(35);
        assertThat(Goal.FASTER_LOSS.defaultProteinGPerKg()).isEqualTo(2.2);
        assertThat(Goal.GAIN.energyAvailabilityKcalPerKgFfm()).isEqualTo(50);
        assertThat(Goal.GAIN.defaultProteinGPerKg()).isEqualTo(1.8);
    }

    private static FuelProfile profile(
            double weightKg, @Nullable Double bodyFatPercent, @Nullable Integer ftpWatts, double proteinGPerKg) {
        return new FuelProfile(
                UUID.randomUUID(), true, weightKg, Sex.MALE, bodyFatPercent, ftpWatts, Goal.FUEL, proteinGPerKg);
    }

    private static void assertRefused(ThrowingCallable make, String message) {
        assertThatThrownBy(make).isInstanceOf(IllegalArgumentException.class).hasMessage(message);
    }
}
