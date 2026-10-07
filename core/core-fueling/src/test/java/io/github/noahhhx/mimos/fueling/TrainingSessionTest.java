package io.github.noahhhx.mimos.fueling;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TrainingSessionTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Test
    void acceptsADurationOnItsBounds() {
        assertThatCode(() -> manual(MONDAY, 1, null)).doesNotThrowAnyException();
        assertThatCode(() -> manual(MONDAY, 1440, 0.0)).doesNotThrowAnyException();
    }

    @Test
    void refusesADurationOutOfBounds() {
        assertThatThrownBy(() -> manual(MONDAY, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("durationMinutes must be between 1 and 1440");
        assertThatThrownBy(() -> manual(MONDAY, 1441, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("durationMinutes must be between 1 and 1440");
    }

    @Test
    void refusesWorkOutOfBounds() {
        assertThatCode(() -> manual(MONDAY, 1440, 51_840.0)).doesNotThrowAnyException();
        for (double workKj : new double[] {-1, 51_840.1, Double.POSITIVE_INFINITY, Double.NaN}) {
            assertThatThrownBy(() -> manual(MONDAY, 60, workKj))
                    .as("workKj %s", workKj)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("workKj must be between 0 and 51840");
        }
    }

    @Test
    void refusesADateOutOfBounds() {
        assertThatCode(() -> manual(LocalDate.of(1900, 1, 1), 60, null)).doesNotThrowAnyException();
        assertThatCode(() -> manual(LocalDate.of(9999, 12, 31), 60, null)).doesNotThrowAnyException();
        for (LocalDate date : new LocalDate[] {LocalDate.of(1899, 12, 31), LocalDate.of(10_000, 1, 1)}) {
            assertThatThrownBy(() -> manual(date, 60, null))
                    .as("date %s", date)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("date must be between 1900-01-01 and 9999-12-31");
        }
    }

    @Test
    void aSyncedSessionHasItsSourcesIdAndAManualOneHasNone() {
        assertThatCode(() -> session(Source.INTERVALS_ICU, "e123")).doesNotThrowAnyException();
        assertThatThrownBy(() -> session(Source.INTERVALS_ICU, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> session(Source.MANUAL, "e123")).isInstanceOf(IllegalArgumentException.class);
    }

    private static TrainingSession manual(LocalDate date, int durationMinutes, @Nullable Double workKj) {
        return new TrainingSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                date,
                Source.MANUAL,
                Kind.PLANNED,
                Sport.RIDE,
                durationMinutes,
                workKj,
                Intensity.ENDURANCE,
                null);
    }

    private static TrainingSession session(Source source, @Nullable String externalId) {
        return new TrainingSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                MONDAY,
                source,
                Kind.COMPLETED,
                Sport.RUN,
                45,
                null,
                null,
                externalId);
    }
}
