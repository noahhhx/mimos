package io.github.noahhhx.mimos.intervalsicu;

import io.github.noahhhx.mimos.fueling.FuelService;
import java.sql.Timestamp;
import java.time.Instant;
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

    private static final Logger log = LoggerFactory.getLogger(IntervalsIcuConnections.class);

    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final IntervalsIcuClient client;
    private final SecretBox secretBox;
    private final FuelService fuel;

    IntervalsIcuConnections(
            JdbcTemplate jdbc,
            TransactionOperations transactions,
            IntervalsIcuClient client,
            SecretBox secretBox,
            FuelService fuel) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.client = client;
        this.secretBox = secretBox;
        this.fuel = fuel;
    }

    public Optional<IntervalsIcuConnection> connection(UUID profileId) {
        return jdbc.query("""
                        select athlete_id, key_ciphertext, key_nonce, status, last_sync_at, last_error
                        from intervals_icu_connection where profile_id = ?
                        """, ROW_MAPPER, profileId).stream().findFirst().map(this::asSeen);
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
        SecretBox.Sealed sealed = secretBox.seal(key);
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
            jdbc.update("""
                    insert into intervals_icu_connection (profile_id, athlete_id, key_ciphertext, key_nonce, status)
                    values (?, ?, ?, ?, 'CONNECTED')
                    on conflict (profile_id) do update
                    set athlete_id = excluded.athlete_id, key_ciphertext = excluded.key_ciphertext,
                        key_nonce = excluded.key_nonce, status = excluded.status, last_error = null,
                        last_sync_at = case when ? then intervals_icu_connection.last_sync_at end
                    """, profileId, athlete, sealed.ciphertext(), sealed.nonce(), sameAthlete);
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

    private IntervalsIcuConnection asSeen(Row row) {
        try {
            secretBox.open(row.sealed());
        } catch (UnreadableSecretException e) {
            return new IntervalsIcuConnection(
                    row.athleteId(), ConnectionStatus.BROKEN, row.lastSyncAt(), UNREADABLE_KEY);
        }
        return new IntervalsIcuConnection(row.athleteId(), row.status(), row.lastSyncAt(), row.lastError());
    }

    private record Row(
            String athleteId,
            SecretBox.Sealed sealed,
            ConnectionStatus status,
            @Nullable Instant lastSyncAt,
            @Nullable String lastError) {}

    private static final RowMapper<Row> ROW_MAPPER = (rs, rowNum) -> {
        @Nullable Timestamp lastSync = rs.getTimestamp("last_sync_at");
        return new Row(
                rs.getString("athlete_id"),
                new SecretBox.Sealed(rs.getBytes("key_ciphertext"), rs.getBytes("key_nonce")),
                ConnectionStatus.valueOf(rs.getString("status")),
                lastSync == null ? null : lastSync.toInstant(),
                rs.getString("last_error"));
    };
}
