package io.github.noahhhx.mimos.intervalsicu;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * What one call to intervals.icu came back with. A refused key and a rate
 * limit are their own outcomes, because a sync treats them differently:
 * the first breaks the connection, the second waits.
 */
sealed interface IcuResult<T> {

    record Ok<T>(T value) implements IcuResult<T> {}

    /** A 401 or 403: the key is wrong or revoked, or it does not belong to that athlete. */
    record Unauthorized<T>() implements IcuResult<T> {}

    /** A 429, with its {@code Retry-After} when it gave one in seconds. */
    record RateLimited<T>(@Nullable Duration retryAfter) implements IcuResult<T> {}

    /** Anything else: another status, an unreadable body, or no answer. */
    record Failed<T>(String reason) implements IcuResult<T> {}
}
