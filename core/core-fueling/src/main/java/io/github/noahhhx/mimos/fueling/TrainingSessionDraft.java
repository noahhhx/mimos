package io.github.noahhhx.mimos.fueling;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record TrainingSessionDraft(
        LocalDate date,
        Kind kind,
        Sport sport,
        int durationMinutes,
        @Nullable Double workKj,
        Intensity intensity) {}
