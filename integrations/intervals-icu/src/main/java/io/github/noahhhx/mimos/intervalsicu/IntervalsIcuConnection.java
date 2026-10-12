package io.github.noahhhx.mimos.intervalsicu;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A person's saved connection, as they may see it: never the key. */
public record IntervalsIcuConnection(
        String athleteId,
        ConnectionStatus status,
        @Nullable Instant lastSyncAt,
        @Nullable String lastError) {}
