package io.github.noahhhx.mimos.intervalsicu;

import io.github.noahhhx.mimos.fueling.TrainingSession;
import io.github.noahhhx.mimos.intervalsicu.IntervalsIcuConnections.Outcome;
import io.github.noahhhx.mimos.intervalsicu.IntervalsIcuConnections.SyncClaim;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Syncs one person's training from intervals.icu (ADR-0022): planned
 * workouts and activities from {@value #DAYS_BACK} days back to
 * {@value #DAYS_AHEAD} ahead replace their synced sessions in that range.
 * A sync never waits for intervals.icu to allow it: a refused key breaks
 * the connection, and a 429 records when to try again.
 */
@Service
@Conditional(IntervalsIcuConfiguration.Active.class)
public class IntervalsIcuSync {

    static final int DAYS_BACK = 7;
    static final int DAYS_AHEAD = 14;

    /** How long to wait after a 429 that names no {@code Retry-After} in seconds, and the most any may ask. */
    static final Duration DEFAULT_WAIT = Duration.ofMinutes(15);

    static final Duration MAX_WAIT = Duration.ofHours(24);

    static final String REFUSED = "intervals.icu no longer accepts this API key. Enter it again.";
    static final String RATE_LIMITED = "intervals.icu asked Mimos to wait before syncing again.";
    static final String UNAVAILABLE = "intervals.icu could not be reached. Training syncs again later.";
    static final String UNSAVED = "Training from intervals.icu could not be saved. It syncs again later.";

    private static final Logger log = LoggerFactory.getLogger(IntervalsIcuSync.class);

    private final IntervalsIcuConnections connections;
    private final IntervalsIcuClient client;
    private final Clock clock;

    IntervalsIcuSync(IntervalsIcuConnections connections, IntervalsIcuClient client, Clock clock) {
        this.connections = connections;
        this.client = client;
        this.clock = clock;
    }

    /**
     * Syncs now unless the connection is broken, intervals.icu asked to wait,
     * or a sync for this person is already running, and answers with the
     * connection as it then stands: absent when the person is not connected.
     */
    public Optional<IntervalsIcuConnection> sync(UUID profileId) {
        connections.claimSync(profileId, clock.instant()).ifPresent(claim -> run(profileId, claim));
        return connections.connection(profileId);
    }

    private void run(UUID profileId, SyncClaim claim) {
        Outcome outcome = fetch(profileId, claim.credentials());
        try {
            connections.record(profileId, claim, outcome, clock.instant());
        } catch (DataAccessException e) {
            log.error("Could not store an intervals.icu sync", e);
            connections.record(profileId, claim, new Outcome.Failed(UNSAVED, null), clock.instant());
        }
    }

    private Outcome fetch(UUID profileId, IcuCredentials credentials) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(DAYS_BACK);
        LocalDate to = today.plusDays(DAYS_AHEAD);
        IcuResult<List<IcuJson.Event>> events = client.plannedWorkouts(credentials, from, to);
        if (!(events instanceof IcuResult.Ok<List<IcuJson.Event>>(List<IcuJson.Event> planned))) {
            return failure(events);
        }
        IcuResult<List<IcuJson.Activity>> activities = client.activities(credentials, from, to);
        if (!(activities instanceof IcuResult.Ok<List<IcuJson.Activity>>(List<IcuJson.Activity> completed))) {
            return failure(activities);
        }
        Map<UUID, TrainingSession> sessions = new LinkedHashMap<>();
        Stream.concat(
                        IntervalsIcuTranslator.planned(profileId, planned).stream(),
                        IntervalsIcuTranslator.completed(profileId, completed).stream())
                .filter(session ->
                        !session.date().isBefore(from) && !session.date().isAfter(to))
                .forEach(session -> sessions.putIfAbsent(session.id(), session));
        return new Outcome.Synced(from, to, List.copyOf(sessions.values()));
    }

    private Outcome failure(IcuResult<?> result) {
        return switch (result) {
            case IcuResult.Unauthorized<?> refused -> new Outcome.Refused(REFUSED);
            case IcuResult.RateLimited<?> limited ->
                new Outcome.Failed(RATE_LIMITED, clock.instant().plus(wait(limited.retryAfter())));
            case IcuResult.Failed<?> failed -> {
                log.warn("intervals.icu sync failed: {}", failed.reason());
                yield new Outcome.Failed(UNAVAILABLE, null);
            }
            case IcuResult.Ok<?> ok -> throw new IllegalStateException("not a failure: " + ok);
        };
    }

    private static Duration wait(@Nullable Duration retryAfter) {
        if (retryAfter == null) {
            return DEFAULT_WAIT;
        }
        return retryAfter.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : retryAfter;
    }
}
