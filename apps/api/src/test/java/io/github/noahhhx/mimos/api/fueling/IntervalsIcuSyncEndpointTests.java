package io.github.noahhhx.mimos.api.fueling;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.MutableClock;
import io.github.noahhhx.mimos.api.support.TestUser;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Syncing training from intervals.icu on demand (ADR-0022), against a local stub of it and a pinned clock. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntervalsIcuSyncEndpointTests extends ApiIntegrationTestSupport {

    private static final String CONNECTION = "/api/v1/me/intervals-icu";
    private static final String SYNC = CONNECTION + "/sync";

    /**
     * Plays intervals.icu for athlete i1 with any key: the athlete record,
     * and whatever events and activities a test sets. A forced status
     * answers every read instead. Every read is logged.
     */
    private static final HttpServer stub;

    private static volatile String events = "[]";
    private static volatile String activities = "[]";
    private static volatile int forcedStatus;
    private static volatile @Nullable String retryAfter;
    private static final List<String> requests = new CopyOnWriteArrayList<>();

    /** While set, an events read waits for it, after counting down {@link #held}. */
    private static volatile @Nullable CountDownLatch hold;

    private static volatile CountDownLatch held = new CountDownLatch(1);

    static {
        try {
            stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        stub.setExecutor(Executors.newCachedThreadPool());
        stub.createContext("/api/v1/athlete/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(path + "?" + exchange.getRequestURI().getQuery());
            CountDownLatch release = hold;
            if (release != null && path.endsWith("/events")) {
                held.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String body = path.endsWith("/events")
                    ? events
                    : path.endsWith("/activities") ? activities : "{\"id\":\"i1\",\"sex\":\"M\"}";
            int status = forcedStatus != 0 ? forcedStatus : 200;
            String header = retryAfter;
            if (header != null) {
                exchange.getResponseHeaders().set("Retry-After", header);
            }
            byte[] bytes = status == 200 ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        stub.start();
    }

    @DynamicPropertySource
    static void intervalsIcu(DynamicPropertyRegistry registry) {
        registry.add(
                "mimos.intervals-icu.base-url",
                () -> "http://localhost:" + stub.getAddress().getPort());
        registry.add("mimos.intervals-icu.secret-key", () -> "BwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyAhIiMkJSY=");
    }

    @TestConfiguration
    static class PinnedClock {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @AfterEach
    void reset() {
        events = "[]";
        activities = "[]";
        forcedStatus = 0;
        retryAfter = null;
        requests.clear();
        release();
    }

    @Test
    void readsTheWeekBeforeToTwoWeeksAfterTodayAndStoresItsTraining() {
        TestUser user = connected();
        events = "[" + event(101, today(), 3600, 900_000L) + "]";
        activities = "[" + activity("i201", today().minusDays(1), 5400, 1_500_000L) + "]";
        requests.clear();

        JsonNode synced = user.ok(HttpMethod.POST, SYNC, null);

        assertThat(synced.get("status").asText()).isEqualTo("CONNECTED");
        assertThat(Instant.parse(synced.get("lastSyncAt").asText())).isEqualTo(now());
        assertThat(synced.has("lastError")).isFalse();
        String range = "oldest=" + today().minusDays(7) + "&newest=" + today().plusDays(14);
        assertThat(requests)
                .containsExactly(
                        "/api/v1/athlete/i1/events?" + range + "&category=WORKOUT",
                        "/api/v1/athlete/i1/activities?" + range);
        assertThat(sessions(user))
                .containsExactlyInAnyOrder(
                        today() + " PLANNED RIDE 60 900.0 101",
                        today().minusDays(1) + " COMPLETED RIDE 90 1500.0 i201");
    }

    @Test
    void replacesSyncedSessionsInTheRangeOnlyAndKeepsHandEnteredOnes() {
        TestUser user = connected();
        UUID manual = UUID.fromString(user.ok(HttpMethod.POST, "/api/v1/me/training", manual(today()))
                .get("id")
                .asText());
        synced(user, today().plusDays(1), "stale");
        synced(user, today().minusDays(8), "before-the-range");
        synced(user, today().plusDays(15), "after-the-range");
        events = "[" + event(101, today(), 3600, null) + "]";

        user.ok(HttpMethod.POST, SYNC, null);

        assertThat(externalIds(user)).containsExactlyInAnyOrder("101", "before-the-range", "after-the-range");
        assertThat(jdbc.queryForObject("select count(*) from training_session where id = ?", Integer.class, manual))
                .isEqualTo(1);
    }

    @Test
    void aRepeatedSyncLeavesTheSameRows() {
        TestUser user = connected();
        events = "[" + event(101, today(), 3600, null) + "," + event(102, today().plusDays(2), 5400, null) + "]";
        activities = "[" + activity("i201", today().minusDays(1), 1800, null) + "]";

        user.ok(HttpMethod.POST, SYNC, null);
        List<Map<String, @Nullable Object>> first = rows(user);
        clock.advance(Duration.ofMinutes(1));
        user.ok(HttpMethod.POST, SYNC, null);

        assertThat(rows(user)).isEqualTo(first).hasSize(3);
    }

    @Test
    void aSyncFollowsAChangedWorkout() {
        TestUser user = connected();
        events = "[" + event(101, today(), 3600, null) + "]";
        user.ok(HttpMethod.POST, SYNC, null);

        events = "[" + event(101, today(), 7200, null) + "]";
        user.ok(HttpMethod.POST, SYNC, null);

        assertThat(sessions(user)).containsExactly(today() + " PLANNED RIDE 120 null 101");
    }

    @Test
    void aSyncThatFailsPartwayLeavesTheOldRows() {
        TestUser user = connected();
        events = "[" + event(101, today(), 3600, null) + "]";
        user.ok(HttpMethod.POST, SYNC, null);
        jdbc.execute("""
                create function refuse_boom() returns trigger language plpgsql as $$
                begin
                    raise exception 'refused for the test';
                end $$;
                create trigger refuse_boom before insert on training_session
                    for each row when (new.external_id = 'boom') execute function refuse_boom();
                """);
        try {
            events = "[" + event(102, today(), 3600, null) + "]";
            activities = "[" + activity("boom", today(), 3600, null) + "]";

            JsonNode failed = user.ok(HttpMethod.POST, SYNC, null);

            assertThat(failed.get("status").asText()).isEqualTo("CONNECTED");
            assertThat(failed.get("lastError").asText()).contains("could not be saved");
        } finally {
            jdbc.execute("drop trigger refuse_boom on training_session; drop function refuse_boom();");
        }

        assertThat(externalIds(user)).containsExactly("101");
    }

    @Test
    void aWorkoutMovedIntoTheRangeFromBeforeItReplacesItsOldRow() {
        TestUser user = connected();
        clock.advance(Duration.ofDays(-10));
        events = "[" + event(101, today(), 3600, null) + "]";
        user.ok(HttpMethod.POST, SYNC, null);
        clock.advance(Duration.ofDays(10));
        assertThat(sessions(user)).containsExactly(today().minusDays(10) + " PLANNED RIDE 60 null 101");

        events = "[" + event(101, today().plusDays(1), 3600, null) + "]";
        JsonNode synced = user.ok(HttpMethod.POST, SYNC, null);

        assertThat(synced.has("lastError")).isFalse();
        assertThat(sessions(user)).containsExactly(today().plusDays(1) + " PLANNED RIDE 60 null 101");
    }

    @Test
    void aPlannedWorkoutAndAnActivityThatShareAnIdAreTwoSessions() {
        TestUser user = connected();
        events = "[" + event(555, today(), 3600, null) + "]";
        activities = "[" + activity("555", today(), 3000, null) + "]";

        user.ok(HttpMethod.POST, SYNC, null);

        assertThat(sessions(user))
                .containsExactlyInAnyOrder(
                        today() + " PLANNED RIDE 60 null 555", today() + " COMPLETED RIDE 50 null 555");
    }

    @Test
    void aSyncWhileAnotherRunsCallsNothing() throws Exception {
        TestUser user = connected();
        events = "[" + event(101, today(), 3600, null) + "]";
        requests.clear();
        CompletableFuture<JsonNode> first = whileHeld(() -> user.ok(HttpMethod.POST, SYNC, null));

        JsonNode second = user.ok(HttpMethod.POST, SYNC, null);

        assertThat(second.has("lastSyncAt")).as("the second sync ran nothing").isFalse();
        assertThat(requests).as("only the first sync's events read").hasSize(1);
        release();
        assertThat(first.get(30, TimeUnit.SECONDS).has("lastSyncAt")).isTrue();
        assertThat(externalIds(user)).containsExactly("101");
    }

    @Test
    void disconnectingWhileASyncRunsWins() throws Exception {
        TestUser user = connected();
        events = "[" + event(101, today(), 3600, null) + "]";
        CompletableFuture<JsonNode> sync = whileHeld(() -> user.ok(HttpMethod.POST, SYNC, null));

        assertThat(user.status(HttpMethod.DELETE, CONNECTION, null)).isEqualTo(204);
        release();

        assertThat(sync.get(30, TimeUnit.SECONDS).get("status").asText()).isEqualTo("DISCONNECTED");
        assertThat(externalIds(user)).as("the sync stored nothing").isEmpty();
    }

    @Test
    void connectingAgainWhileASyncRunsWinsEvenWhenTheOldKeyIsRefused() throws Exception {
        TestUser user = connected();
        CompletableFuture<JsonNode> sync = whileHeld(() -> user.ok(HttpMethod.POST, SYNC, null));

        user.ok(HttpMethod.PUT, CONNECTION, Map.of("athleteId", "i1", "apiKey", "a-new-key"));
        forcedStatus = 401;
        release();

        JsonNode after = sync.get(30, TimeUnit.SECONDS);
        assertThat(after.get("status").asText())
                .as("the stale sync's 401 is not stored")
                .isEqualTo("CONNECTED");
        assertThat(after.has("lastError")).isFalse();
    }

    @Test
    void aRateLimitRecordsWhenToTryAgainAndNothingCallsBeforeThen() {
        TestUser user = connected();
        synced(user, today(), "kept");
        forcedStatus = 429;
        retryAfter = "120";
        requests.clear();

        JsonNode limited = user.ok(HttpMethod.POST, SYNC, null);

        assertThat(limited.get("status").asText()).isEqualTo("CONNECTED");
        assertThat(limited.get("lastError").asText()).contains("wait");
        assertThat(Instant.parse(limited.get("retryAt").asText())).isEqualTo(now().plusSeconds(120));
        assertThat(requests).hasSize(1);
        assertThat(externalIds(user)).containsExactly("kept");

        forcedStatus = 0;
        retryAfter = null;
        clock.advance(Duration.ofSeconds(119));
        assertThat(user.ok(HttpMethod.POST, SYNC, null)).isEqualTo(limited);
        assertThat(requests).as("nothing calls intervals.icu inside the wait").hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        JsonNode synced = user.ok(HttpMethod.POST, SYNC, null);
        assertThat(synced.has("retryAt")).isFalse();
        assertThat(synced.has("lastError")).isFalse();
        assertThat(requests).hasSize(3);
    }

    @Test
    void aRateLimitWithoutRetryAfterWaitsFifteenMinutes() {
        TestUser user = connected();
        forcedStatus = 429;

        JsonNode limited = user.ok(HttpMethod.POST, SYNC, null);

        assertThat(Instant.parse(limited.get("retryAt").asText())).isEqualTo(now().plus(Duration.ofMinutes(15)));
        forcedStatus = 0;
        JsonNode reconnected = user.ok(HttpMethod.PUT, CONNECTION, Map.of("athleteId", "i1", "apiKey", "any-key"));
        assertThat(reconnected.get("retryAt"))
                .as("connecting the same athlete again keeps the wait")
                .isEqualTo(limited.get("retryAt"));
    }

    @Test
    void aRefusedKeyBreaksTheConnectionUntilItIsEnteredAgain() {
        TestUser user = connected();
        synced(user, today(), "kept");
        forcedStatus = 401;

        JsonNode broken = user.ok(HttpMethod.POST, SYNC, null);

        assertThat(broken.get("status").asText()).isEqualTo("BROKEN");
        assertThat(broken.get("lastError").asText()).contains("Enter it again");
        assertThat(user.ok(HttpMethod.GET, CONNECTION, null)).isEqualTo(broken);
        assertThat(externalIds(user)).containsExactly("kept");

        forcedStatus = 0;
        requests.clear();
        assertThat(user.ok(HttpMethod.POST, SYNC, null)).isEqualTo(broken);
        assertThat(requests).as("a broken connection never calls intervals.icu").isEmpty();

        user.ok(HttpMethod.PUT, CONNECTION, Map.of("athleteId", "i1", "apiKey", "a-new-key"));
        assertThat(user.ok(HttpMethod.POST, SYNC, null).get("status").asText()).isEqualTo("CONNECTED");
    }

    @Test
    void aFailedSyncSaysSoAndKeepsTheConnection() {
        TestUser user = connected();
        synced(user, today(), "kept");
        forcedStatus = 503;

        JsonNode failed = user.ok(HttpMethod.POST, SYNC, null);

        assertThat(failed.get("status").asText()).isEqualTo("CONNECTED");
        assertThat(failed.get("lastError").asText()).contains("could not be reached");
        assertThat(failed.has("retryAt")).isFalse();
        assertThat(externalIds(user)).containsExactly("kept");
    }

    @Test
    void aPersonWhoIsNotConnectedSyncsNothing() {
        TestUser user = new TestUser(port, objectMapper, jdbc);

        JsonNode answer = user.ok(HttpMethod.POST, SYNC, null);

        assertThat(answer.get("status").asText()).isEqualTo("DISCONNECTED");
        assertThat(requests).isEmpty();
    }

    @Test
    void readingFuelOrTheConnectionNeverCallsIntervalsIcu() {
        TestUser user = connected();
        requests.clear();

        user.ok(HttpMethod.GET, CONNECTION, null);
        user.ok(HttpMethod.GET, "/api/v1/me/fuel?from=" + today() + "&to=" + today().plusDays(6), null);

        assertThat(requests).isEmpty();
    }

    /** Starts {@code call} and returns once it is waiting on its events read. */
    private CompletableFuture<JsonNode> whileHeld(Supplier<JsonNode> call) throws InterruptedException {
        held = new CountDownLatch(1);
        hold = new CountDownLatch(1);
        CompletableFuture<JsonNode> running = CompletableFuture.supplyAsync(call);
        assertThat(held.await(30, TimeUnit.SECONDS))
                .as("the sync reached intervals.icu")
                .isTrue();
        return running;
    }

    private static void release() {
        CountDownLatch release = hold;
        hold = null;
        if (release != null) {
            release.countDown();
        }
    }

    private TestUser connected() {
        TestUser user = new TestUser(port, objectMapper, jdbc);
        user.ok(HttpMethod.PUT, CONNECTION, Map.of("athleteId", "i1", "apiKey", "any-key"));
        return user;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String event(long id, LocalDate date, int seconds, @Nullable Long joules) {
        return "{\"id\":" + id + ",\"start_date_local\":\"" + date + "T00:00:00\",\"type\":\"Ride\",\"moving_time\":"
                + seconds + (joules == null ? "" : ",\"joules\":" + joules) + "}";
    }

    private static String activity(String id, LocalDate date, int seconds, @Nullable Long joules) {
        return "{\"id\":\"" + id + "\",\"start_date_local\":\"" + date
                + "T07:30:00\",\"type\":\"Ride\",\"moving_time\":" + seconds
                + (joules == null ? "" : ",\"icu_joules\":" + joules) + "}";
    }

    private static Map<String, Object> manual(LocalDate date) {
        return Map.of("date", date.toString(), "sport", "SWIM", "durationMinutes", 30, "intensity", "EASY");
    }

    private void synced(TestUser user, LocalDate date, String externalId) {
        jdbc.update("""
                insert into training_session (id, profile_id, session_date, source, kind, sport, duration_minutes,
                                              external_id)
                values (?, ?, ?, 'INTERVALS_ICU', 'PLANNED', 'RIDE', 60, ?)
                """, UUID.randomUUID(), user.profileId, Date.valueOf(date), externalId);
    }

    private List<String> sessions(TestUser user) {
        return jdbc.query(
                """
                select session_date, kind, sport, duration_minutes, work_kj, external_id from training_session
                where profile_id = ? and source = 'INTERVALS_ICU'
                """,
                (rs, rowNum) -> rs.getDate(1).toLocalDate() + " " + rs.getString(2) + " " + rs.getString(3) + " "
                        + rs.getInt(4) + " " + (rs.getBigDecimal(5) == null ? null : rs.getDouble(5)) + " "
                        + rs.getString(6),
                user.profileId);
    }

    private List<String> externalIds(TestUser user) {
        return jdbc.queryForList(
                "select external_id from training_session where profile_id = ? and source = 'INTERVALS_ICU'",
                String.class,
                user.profileId);
    }

    private List<Map<String, @Nullable Object>> rows(TestUser user) {
        return jdbc.queryForList(
                "select id, session_date, kind, duration_minutes, external_id from training_session"
                        + " where profile_id = ? order by id",
                user.profileId);
    }
}
