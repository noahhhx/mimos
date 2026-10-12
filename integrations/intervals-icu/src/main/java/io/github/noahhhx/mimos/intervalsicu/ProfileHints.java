package io.github.noahhhx.mimos.intervalsicu;

import io.github.noahhhx.mimos.fueling.Sex;
import org.jspecify.annotations.Nullable;

/**
 * What intervals.icu knows that a fuel profile asks for (ADR-0022). Each
 * value is absent when intervals.icu has none; none is applied to a
 * profile here.
 */
public record ProfileHints(
        @Nullable Double weightKg,
        @Nullable Double bodyFatPercent,
        @Nullable Sex sex,
        @Nullable Integer ftpWatts) {}
