package io.github.noahhhx.mimos.intervalsicu;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A person's saved connection, as they may see it: never the key.
 * {@code retryAt} is set while intervals.icu has asked Mimos to wait;
 * no sync calls it for this person before then.
 */
public record IntervalsIcuConnection(
        String athleteId,
        ConnectionStatus status,
        @Nullable Instant lastSyncAt,
        @Nullable String lastError,
        @Nullable Instant retryAt) {}
