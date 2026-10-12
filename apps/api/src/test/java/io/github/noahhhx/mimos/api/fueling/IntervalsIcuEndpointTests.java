package io.github.noahhhx.mimos.api.fueling;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestUser;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntervalsIcuEndpointTests extends ApiIntegrationTestSupport {

    private static final String CONNECTION = "/api/v1/me/intervals-icu";
    private static final String ATHLETE = "i1";
    private static final String GOOD_KEY = "7c1kq0intervals-good-key";
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    /** Plays intervals.icu's athlete read: one athlete, one key that works, a 403 for another athlete. */
    private static final HttpServer stub;

    private static final AtomicInteger forcedStatus = new AtomicInteger();

    static {
        try {
            stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String goodAuthorization =
                "Basic " + Base64.getEncoder().encodeToString(("API_KEY:" + GOOD_KEY).getBytes(StandardCharsets.UTF_8));
        stub.createContext("/api/v1/athlete/", exchange -> {
            String athlete = exchange.getRequestURI().getPath().substring("/api/v1/athlete/".length());
            int status = forcedStatus.get() != 0
                    ? forcedStatus.get()
                    : !goodAuthorization.equals(exchange.getRequestHeaders().getFirst("Authorization"))
                            ? 401
                            : athlete.equals(ATHLETE) ? 200 : 403;
            byte[] body = status == 200
                    ? "{\"id\":\"i1\",\"sex\":\"M\",\"weight\":72.0}".getBytes(StandardCharsets.UTF_8)
                    : new byte[0];
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
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

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clearFault() {
        forcedStatus.set(0);
    }

    private TestUser user() {
        return new TestUser(port, objectMapper, jdbc);
    }

    @Test
    void aPersonWhoNeverConnectedReadsAsDisconnected() {
        JsonNode connection = user().ok(HttpMethod.GET, CONNECTION, null);

        assertThat(connection.get("status").asText()).isEqualTo("DISCONNECTED");
        assertThat(connection.has("athleteId")).isFalse();
    }

    @Test
    void connectsWithAKeyIntervalsIcuAcceptsAndNeverShowsIt() {
        TestUser user = user();

        JsonNode connected = user.ok(HttpMethod.PUT, CONNECTION, input(" " + ATHLETE + " ", GOOD_KEY));

        assertThat(connected.get("status").asText()).isEqualTo("CONNECTED");
        assertThat(connected.get("athleteId").asText()).isEqualTo(ATHLETE);
        assertThat(connected.has("lastSyncAt")).isFalse();
        assertThat(connected.has("lastError")).isFalse();
        JsonNode read = user.ok(HttpMethod.GET, CONNECTION, null);
        assertThat(read).isEqualTo(connected);
        assertThat(read.toString()).doesNotContain(GOOD_KEY);
        Map<String, @Nullable Object> row =
                jdbc.queryForMap("select * from intervals_icu_connection where profile_id = ?", user.profileId);
        assertThat(row.values())
                .as("the stored row holds the key only sealed")
                .noneMatch(value -> String.valueOf(value).contains(GOOD_KEY)
                        || value instanceof byte[] bytes
                                && new String(bytes, StandardCharsets.UTF_8).contains(GOOD_KEY));
        assertThat(user.ok(HttpMethod.GET, "/api/v1/account/export", null).toString())
                .doesNotContain(GOOD_KEY)
                .doesNotContain("intervals");
    }

    @Test
    void aKeyIntervalsIcuRefusesIsA400AndNothingIsSaved() {
        TestUser user = user();

        TestUser.Response wrongKey = user.send(HttpMethod.PUT, CONNECTION, input(ATHLETE, "not-the-key"));
        TestUser.Response otherAthlete = user.send(HttpMethod.PUT, CONNECTION, input("i2", GOOD_KEY));

        assertThat(wrongKey.status()).isEqualTo(400);
        assertThat(otherAthlete.status()).isEqualTo(400);
        assertThat(String.valueOf(wrongKey.body())).contains("did not accept this athlete id and API key");
        assertThat(rows(user)).isZero();
        assertThat(user.ok(HttpMethod.GET, CONNECTION, null).get("status").asText())
                .isEqualTo("DISCONNECTED");
    }

    @Test
    void malformedInputIsA400WithoutAskingIntervalsIcu() {
        TestUser user = user();
        forcedStatus.set(500);

        assertThat(user.status(HttpMethod.PUT, CONNECTION, input("../i1", GOOD_KEY)))
                .isEqualTo(400);
        assertThat(user.status(HttpMethod.PUT, CONNECTION, input(ATHLETE, "  ")))
                .isEqualTo(400);
        assertThat(user.status(HttpMethod.PUT, CONNECTION, input(ATHLETE, "k".repeat(201))))
                .isEqualTo(400);
        assertThat(user.status(HttpMethod.PUT, CONNECTION, Map.of("athleteId", ATHLETE)))
                .isEqualTo(400);
        assertThat(rows(user)).isZero();
    }

    @Test
    void intervalsIcuNotAnsweringIsA502AndNothingIsSaved() {
        TestUser user = user();

        forcedStatus.set(503);
        TestUser.Response down = user.send(HttpMethod.PUT, CONNECTION, input(ATHLETE, GOOD_KEY));
        forcedStatus.set(429);
        TestUser.Response limited = user.send(HttpMethod.PUT, CONNECTION, input(ATHLETE, GOOD_KEY));

        assertThat(down.status()).isEqualTo(502);
        assertThat(limited.status()).isEqualTo(502);
        assertThat(rows(user)).isZero();
    }

    @Test
    void disconnectingDeletesTheKeyAndSyncedTrainingButKeepsHandEnteredTraining() {
        TestUser user = user();
        user.ok(HttpMethod.PUT, CONNECTION, input(ATHLETE, GOOD_KEY));
        String manual = user.ok(HttpMethod.POST, "/api/v1/me/training", manualSession())
                .get("id")
                .asText();
        synced(user, "event-1");

        assertThat(user.status(HttpMethod.DELETE, CONNECTION, null)).isEqualTo(204);

        assertThat(rows(user)).isZero();
        assertThat(sessionIds(user)).containsExactly(manual);
        assertThat(user.ok(HttpMethod.GET, CONNECTION, null).get("status").asText())
                .isEqualTo("DISCONNECTED");
        assertThat(user.status(HttpMethod.DELETE, CONNECTION, null))
                .as("disconnecting again changes nothing")
                .isEqualTo(204);
    }

    @Test
    void reconnectingKeepsSyncedTrainingForTheSameAthleteOnly() {
        TestUser user = user();
        user.ok(HttpMethod.PUT, CONNECTION, input(ATHLETE, GOOD_KEY));
        UUID kept = synced(user, "event-1");

        user.ok(HttpMethod.PUT, CONNECTION, input(ATHLETE, GOOD_KEY));
        assertThat(sessionIds(user)).containsExactly(kept.toString());

        jdbc.update("update intervals_icu_connection set athlete_id = 'i9' where profile_id = ?", user.profileId);
        user.ok(HttpMethod.PUT, CONNECTION, input(ATHLETE, GOOD_KEY));
        assertThat(sessionIds(user)).as("the previous athlete's sessions go").isEmpty();
    }

    @Test
    void aKeyThisInstanceCanNoLongerReadIsABrokenConnection() {
        TestUser user = user();
        user.ok(HttpMethod.PUT, CONNECTION, input(ATHLETE, GOOD_KEY));
        jdbc.update(
                "update intervals_icu_connection set key_ciphertext = ? where profile_id = ?",
                new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17},
                user.profileId);

        JsonNode broken = user.ok(HttpMethod.GET, CONNECTION, null);

        assertThat(broken.get("status").asText()).isEqualTo("BROKEN");
        assertThat(broken.get("athleteId").asText()).isEqualTo(ATHLETE);
        assertThat(broken.get("lastError").asText()).contains("Enter it again");
        assertThat(user.ok(HttpMethod.PUT, CONNECTION, input(ATHLETE, GOOD_KEY))
                        .get("status")
                        .asText())
                .as("entering the key again mends it")
                .isEqualTo("CONNECTED");
    }

    private static Map<String, Object> input(String athleteId, String apiKey) {
        return Map.of("athleteId", athleteId, "apiKey", apiKey);
    }

    private static Map<String, Object> manualSession() {
        return Map.of("date", MONDAY.toString(), "sport", "SWIM", "durationMinutes", 30, "intensity", "EASY");
    }

    private UUID synced(TestUser user, String externalId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into training_session (id, profile_id, session_date, source, kind, sport, duration_minutes,
                                              external_id)
                values (?, ?, ?, 'INTERVALS_ICU', 'PLANNED', 'RIDE', 60, ?)
                """, id, user.profileId, Date.valueOf(MONDAY), externalId);
        return id;
    }

    private List<String> sessionIds(TestUser user) {
        return user.ok(HttpMethod.GET, "/api/v1/me/training?from=" + MONDAY + "&to=" + MONDAY, null)
                .valueStream()
                .map(session -> session.get("id").asText())
                .toList();
    }

    private int rows(TestUser user) {
        Integer count = jdbc.queryForObject(
                "select count(*) from intervals_icu_connection where profile_id = ?", Integer.class, user.profileId);
        return count == null ? 0 : count;
    }
}
