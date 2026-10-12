package io.github.noahhhx.mimos.intervalsicu;

import io.github.noahhhx.mimos.fueling.FuelService;
import io.github.noahhhx.mimos.fueling.Source;
import io.github.noahhhx.mimos.fueling.TrainingSession;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Each person's intervals.icu connection (ADR-0022): their athlete id and
 * API key, sealed by {@link SecretBox} in {@code intervals_icu_connection}.
 * Exists only while the feature is {@linkplain IntervalsIcuProperties#active() active}.
 */
@Service
@Conditional(IntervalsIcuConfiguration.Active.class)
public class IntervalsIcuConnections {

    static final Pattern ATHLETE_ID = Pattern.compile("[A-Za-z0-9]{1,32}");
    static final int MAX_KEY_LENGTH = 200;
    static final String UNREADABLE_KEY = "This instance can no longer read the saved API key. Enter it again.";

    /** Longer than a sync can take (two calls, each within the client's timeouts), so a claim outlives its sync. */
    static final Duration CLAIM_LEASE = Duration.ofMinutes(2);

    private static final Logger log = LoggerFactory.getLogger(IntervalsIcuConnections.class);

    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final IntervalsIcuClient client;
    private final SecretBox secretBox;
    private final FuelService fuel;
    private final SecureRandom random;

    IntervalsIcuConnections(
            JdbcTemplate jdbc,
            TransactionOperations transactions,
            IntervalsIcuClient client,
            SecretBox secretBox,
            FuelService fuel,
            SecureRandom random) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.client = client;
        this.secretBox = secretBox;
        this.fuel = fuel;
        this.random = random;
    }

    public Optional<IntervalsIcuConnection> connection(UUID profileId) {
        return jdbc.query("""
                        select athlete_id, key_ciphertext, key_nonce, status, last_sync_at, last_error, retry_at
                        from intervals_icu_connection where profile_id = ?
                        """, ROW_MAPPER, profileId).stream().findFirst().map(row -> asSeen(profileId, row));
    }

    /**
     * Saves the key once intervals.icu accepts it for the athlete, replacing any
     * earlier connection. Nothing is saved when it does not: a refused key is an
     * {@link IllegalArgumentException}, no answer an {@link IntervalsIcuUnavailableException}.
     */
    public IntervalsIcuConnection connect(UUID profileId, String athleteId, String apiKey) {
        String athlete = athleteId.strip();
        String key = apiKey.strip();
        if (!ATHLETE_ID.matcher(athlete).matches()) {
            throw new IllegalArgumentException("athleteId must be 1 to 32 letters and digits, such as i12345");
        }
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("apiKey must be 1 to " + MAX_KEY_LENGTH + " characters");
        }
        switch (client.athlete(new IcuCredentials(athlete, key))) {
            case IcuResult.Ok<IcuJson.Athlete> ok -> {}
            case IcuResult.Unauthorized<IcuJson.Athlete> refused ->
                throw new IllegalArgumentException(
                        "intervals.icu did not accept this athlete id and API key. Check both and try again.");
            case IcuResult.RateLimited<IcuJson.Athlete> limited ->
                throw new IntervalsIcuUnavailableException(
                        "intervals.icu asked to wait before the next request. Try again in a few minutes.");
            case IcuResult.Failed<IcuJson.Athlete> failed -> {
                log.warn("intervals.icu connect check failed: {}", failed.reason());
                throw new IntervalsIcuUnavailableException("intervals.icu could not be reached. Try again later.");
            }
        }
        SecretBox.Sealed sealed = secretBox.seal(key, owner(profileId));
        return Objects.requireNonNull(transactions.execute(status -> {
            @Nullable
            String previous = jdbc
                    .query(
                            "select athlete_id from intervals_icu_connection where profile_id = ? for update",
                            (rs, rowNum) -> rs.getString(1),
                            profileId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            boolean sameAthlete = athlete.equals(previous);
            if (previous != null && !sameAthlete) {
                fuel.deleteSynced(profileId);
            }
            // Clearing the claim makes a sync that started before this one store nothing.
            jdbc.update("""
                    insert into intervals_icu_connection (profile_id, athlete_id, key_ciphertext, key_nonce, status)
                    values (?, ?, ?, ?, 'CONNECTED')
                    on conflict (profile_id) do update
                    set athlete_id = excluded.athlete_id, key_ciphertext = excluded.key_ciphertext,
                        key_nonce = excluded.key_nonce, status = excluded.status, last_error = null,
                        sync_claim = null, sync_claimed_at = null,
                        last_sync_at = case when ? then intervals_icu_connection.last_sync_at end,
                        retry_at = case when ? then intervals_icu_connection.retry_at end
                    """, profileId, athlete, sealed.ciphertext(), sealed.nonce(), sameAthlete, sameAthlete);
            return connection(profileId).orElseThrow();
        }));
    }

    /** Forgets the key and the training synced with it; training entered by hand stays. */
    public void disconnect(UUID profileId) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("delete from intervals_icu_connection where profile_id = ?", profileId);
            fuel.deleteSynced(profileId);
        });
    }

    /**
     * The people a scheduled sync should try at {@code now}: connected, and
     * not waiting on intervals.icu. Those synced longest ago come first.
     */
    List<UUID> due(Instant now) {
        return jdbc.queryForList("""
                select profile_id from intervals_icu_connection
                where status = 'CONNECTED' and (retry_at is null or retry_at <= ?)
                order by last_sync_at nulls first, profile_id
                """, UUID.class, timestamp(now));
    }

    /** A sync's right to call intervals.icu for a person and store what it read. */
    record SyncClaim(UUID token, IcuCredentials credentials) {}

    /** What a sync found. */
    sealed interface Outcome {

        /** intervals.icu answered: these sessions are the person's synced training from {@code from} to {@code to}. */
        record Synced(LocalDate from, LocalDate to, List<TrainingSession> sessions) implements Outcome {}

        /** intervals.icu no longer accepts the key. */
        record Refused(String error) implements Outcome {}

        /** The sync did not work, but the connection still does; no call before {@code retryAt} when set. */
        record Failed(String error, @Nullable Instant retryAt) implements Outcome {}
    }

    /**
     * Claims the person's next sync, or nothing when they are not connected,
     * their connection is broken, intervals.icu asked to wait until after
     * {@code now}, or another sync holds a claim that has not lapsed.
     */
    Optional<SyncClaim> claimSync(UUID profileId, Instant now) {
        UUID token = new UUID(random.nextLong(), random.nextLong());
        Optional<SyncClaim> claim = jdbc
                .query(
                        """
                        update intervals_icu_connection set sync_claim = ?, sync_claimed_at = ?
                        where profile_id = ? and status = 'CONNECTED'
                          and (retry_at is null or retry_at <= ?)
                          and (sync_claimed_at is null or sync_claimed_at <= ?)
                        returning athlete_id, key_ciphertext, key_nonce
                        """,
                        (rs, rowNum) -> credentials(
                                profileId,
                                rs.getString("athlete_id"),
                                new SecretBox.Sealed(rs.getBytes("key_ciphertext"), rs.getBytes("key_nonce"))),
                        token,
                        timestamp(now),
                        profileId,
                        timestamp(now),
                        timestamp(now.minus(CLAIM_LEASE)))
                .stream()
                .flatMap(Optional::stream)
                .findFirst()
                .map(credentials -> new SyncClaim(token, credentials));
        if (claim.isEmpty()) {
            jdbc.update(
                    "update intervals_icu_connection set sync_claim = null, sync_claimed_at = null"
                            + " where profile_id = ? and sync_claim = ?",
                    profileId,
                    token);
        }
        return claim;
    }

    /**
     * Stores a sync's outcome and ends its claim, in one transaction. Stores
     * nothing when the claim no longer stands: the person disconnected or
     * connected again while the sync ran, and that wins.
     */
    void record(UUID profileId, SyncClaim claim, Outcome outcome, Instant now) {
        transactions.executeWithoutResult(status -> {
            boolean stands = !jdbc.query(
                            "select 1 from intervals_icu_connection where profile_id = ? and sync_claim = ? for update",
                            (rs, rowNum) -> 1,
                            profileId,
                            claim.token())
                    .isEmpty();
            if (!stands) {
                return;
            }
            switch (outcome) {
                case Outcome.Synced synced -> {
                    fuel.replaceSynced(profileId, Source.INTERVALS_ICU, synced.from(), synced.to(), synced.sessions());
                    jdbc.update("""
                            update intervals_icu_connection
                            set last_sync_at = ?, last_error = null, retry_at = null
                            where profile_id = ?
                            """, timestamp(now), profileId);
                }
                case Outcome.Refused refused -> jdbc.update("""
                            update intervals_icu_connection set status = 'BROKEN', last_error = ?, retry_at = null
                            where profile_id = ?
                            """, refused.error(), profileId);
                case Outcome.Failed failed -> {
                    Instant retryAt = failed.retryAt();
                    jdbc.update("""
                            update intervals_icu_connection set last_error = ?, retry_at = ?
                            where profile_id = ?
                            """, failed.error(), retryAt == null ? null : timestamp(retryAt), profileId);
                }
            }
            jdbc.update(
                    "update intervals_icu_connection set sync_claim = null, sync_claimed_at = null where profile_id = ?",
                    profileId);
        });
    }

    private Optional<IcuCredentials> credentials(UUID profileId, String athleteId, SecretBox.Sealed sealed) {
        try {
            return Optional.of(new IcuCredentials(athleteId, secretBox.open(sealed, owner(profileId))));
        } catch (UnreadableSecretException e) {
            return Optional.empty();
        }
    }

    private IntervalsIcuConnection asSeen(UUID profileId, Row row) {
        try {
            secretBox.open(row.sealed(), owner(profileId));
        } catch (UnreadableSecretException e) {
            return new IntervalsIcuConnection(
                    row.athleteId(), ConnectionStatus.BROKEN, row.lastSyncAt(), UNREADABLE_KEY, null);
        }
        return new IntervalsIcuConnection(
                row.athleteId(), row.status(), row.lastSyncAt(), row.lastError(), row.retryAt());
    }

    /** Binds a sealed key to its row, so a key copied to another person's row does not open. */
    private static String owner(UUID profileId) {
        return profileId.toString();
    }

    /** Postgres keeps microseconds, so a time is stored as it will be read back. */
    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant.truncatedTo(ChronoUnit.MICROS));
    }

    private record Row(
            String athleteId,
            SecretBox.Sealed sealed,
            ConnectionStatus status,
            @Nullable Instant lastSyncAt,
            @Nullable String lastError,
            @Nullable Instant retryAt) {}

    private static final RowMapper<Row> ROW_MAPPER = (rs, rowNum) -> new Row(
            rs.getString("athlete_id"),
            new SecretBox.Sealed(rs.getBytes("key_ciphertext"), rs.getBytes("key_nonce")),
            ConnectionStatus.valueOf(rs.getString("status")),
            instant(rs.getTimestamp("last_sync_at")),
            rs.getString("last_error"),
            instant(rs.getTimestamp("retry_at")));

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
