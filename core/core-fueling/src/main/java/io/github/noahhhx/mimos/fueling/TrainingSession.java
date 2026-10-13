package io.github.noahhhx.mimos.fueling;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record TrainingSession(
        UUID id,
        UUID profileId,
        LocalDate date,
        Source source,
        Kind kind,
        Sport sport,
        int durationMinutes,
        @Nullable Double workKj,
        @Nullable Intensity intensity,
        @Nullable String externalId,
        @Nullable Integer reportedKcal) {

    static final LocalDate EARLIEST_DATE = LocalDate.of(1900, 1, 1);
    static final LocalDate LATEST_DATE = LocalDate.of(9999, 12, 31);
    static final String DATE_BOUNDS = "between " + EARLIEST_DATE + " and " + LATEST_DATE;

    static final int MAX_DURATION_MINUTES = 1440;
    static final int MAX_AVERAGE_WATTS = 600;
    static final int MAX_WORK_KJ = MAX_AVERAGE_WATTS * MAX_DURATION_MINUTES * 60 / 1000;
    public static final int MAX_REPORTED_KCAL = 20_000;

    public TrainingSession {
        if (date.isBefore(EARLIEST_DATE) || date.isAfter(LATEST_DATE)) {
            throw new IllegalArgumentException("date must be " + DATE_BOUNDS);
        }
        if (durationMinutes < 1 || durationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("durationMinutes must be between 1 and " + MAX_DURATION_MINUTES);
        }
        if (workKj != null && !(workKj >= 0 && workKj <= MAX_WORK_KJ)) {
            throw new IllegalArgumentException("workKj must be between 0 and " + MAX_WORK_KJ);
        }
        if ((source == Source.MANUAL) != (externalId == null)) {
            throw new IllegalArgumentException("a synced session needs its source's id, and a manual one has none");
        }
        if (reportedKcal != null && !(reportedKcal >= 0 && reportedKcal <= MAX_REPORTED_KCAL)) {
            throw new IllegalArgumentException("reportedKcal must be between 0 and " + MAX_REPORTED_KCAL);
        }
        if (source == Source.MANUAL && reportedKcal != null) {
            throw new IllegalArgumentException("only a synced session carries its source's reported calories");
        }
    }
}
