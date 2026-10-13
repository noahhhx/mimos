package io.github.noahhhx.mimos.api.fueling;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.MutableClock;
import io.github.noahhhx.mimos.api.support.TestUser;
import io.github.noahhhx.mimos.intervalsicu.IntervalsIcuScheduledSync;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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

/** The scheduled sync (ADR-0022), driven by calling the job directly on a pinned clock. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntervalsIcuScheduledSyncTests extends ApiIntegrationTestSupport {

    private static final String CONNECTION = "/api/v1/me/intervals-icu";

    /** Plays intervals.icu for any athlete and key: one planned ride today. Logs every read's path. */
    private static final HttpServer stub;

    private static final List<String> requests = new CopyOnWriteArrayList<>();

    static {
        try {
            stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        stub.createContext("/api/v1/athlete/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(path);
            String today = LocalDate.now(ZoneOffset.UTC).toString();
            String body = path.endsWith("/events")
                    ? "[{\"id\":7,\"start_date_local\":\"" + today
                            + "T00:00:00\",\"type\":\"Ride\",\"moving_time\":3600}]"
                    : path.endsWith("/activities") || path.endsWith("/wellness") ? "[]" : "{}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
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

    @Autowired
    IntervalsIcuScheduledSync job;

    @Test
    void syncsConnectedPeopleSkipsBrokenAndWaitingOnesAndMovesOnFromAFailure() {
        TestUser failing = connected("i666");
        TestUser good = connected("i101");
        TestUser broken = connected("i102");
        TestUser waiting = connected("i103");
        // The failing person comes first: never synced, where the good one synced long ago.
        jdbc.update(
                "update intervals_icu_connection set last_sync_at = '2000-01-01T00:00:00Z' where profile_id = ?",
                good.profileId);
        jdbc.update("update intervals_icu_connection set status = 'BROKEN' where profile_id = ?", broken.profileId);
        jdbc.update(
                "update intervals_icu_connection set retry_at = ? where profile_id = ?",
                Timestamp.from(clock.instant().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MICROS)),
                waiting.profileId);
        jdbc.execute("""
                create function refuse_i666() returns trigger language plpgsql as $$
                begin
                    raise exception 'refused for the test';
                end $$;
                create trigger refuse_i666 before update on intervals_icu_connection
                    for each row when (new.athlete_id = 'i666') execute function refuse_i666();
                """);
        requests.clear();
        try {
            job.syncEveryone();
        } finally {
            jdbc.execute("drop trigger refuse_i666 on intervals_icu_connection; drop function refuse_i666();");
        }

        assertThat(status(good).has("lastSyncAt")).isTrue();
        assertThat(syncedSessions(good)).isEqualTo(1);
        assertThat(requests).contains("/api/v1/athlete/i101/events");
        assertThat(requests)
                .as("broken, waiting, and failing people never reach intervals.icu")
                .noneMatch(path -> path.contains("/i102/") || path.contains("/i103/") || path.contains("/i666/"));
        assertThat(syncedSessions(broken)).isZero();
        assertThat(syncedSessions(waiting)).isZero();

        clock.advance(Duration.ofMinutes(5));
        requests.clear();
        job.syncEveryone();
        assertThat(requests).as("the wait is over").contains("/api/v1/athlete/i103/events");
        assertThat(syncedSessions(waiting)).isEqualTo(1);
        assertThat(syncedSessions(failing))
                .as("it syncs once nothing refuses it")
                .isEqualTo(1);
    }

    private TestUser connected(String athleteId) {
        TestUser user = new TestUser(port, objectMapper, jdbc);
        user.ok(HttpMethod.PUT, CONNECTION, Map.of("athleteId", athleteId, "apiKey", "any-key"));
        return user;
    }

    private JsonNode status(TestUser user) {
        return user.ok(HttpMethod.GET, CONNECTION, null);
    }

    private int syncedSessions(TestUser user) {
        Integer count = jdbc.queryForObject(
                "select count(*) from training_session where profile_id = ? and source = 'INTERVALS_ICU'",
                Integer.class,
                user.profileId);
        return count == null ? 0 : count;
    }
}
