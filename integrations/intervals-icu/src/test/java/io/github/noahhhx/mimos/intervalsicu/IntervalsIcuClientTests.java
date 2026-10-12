package io.github.noahhhx.mimos.intervalsicu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class IntervalsIcuClientTests {

    private static final IcuCredentials CREDENTIALS = new IcuCredentials("i1", "s3cret-key");
    private static final LocalDate OLDEST = LocalDate.of(2026, 10, 5);
    private static final LocalDate NEWEST = LocalDate.of(2026, 10, 26);

    private StubIntervalsIcu stub;
    private IntervalsIcuClient client;

    @BeforeEach
    void start() throws IOException {
        stub = StubIntervalsIcu.start();
        client = new IntervalsIcuClient(stub.url());
    }

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void signsInWithBasicAuthAsApiKey() {
        stub.answer(200, Fixtures.bytes("athlete.json"), Map.of());

        client.athlete(CREDENTIALS);

        String expected =
                "Basic " + Base64.getEncoder().encodeToString("API_KEY:s3cret-key".getBytes(StandardCharsets.UTF_8));
        assertThat(lastRequest().authorization()).isEqualTo(expected);
        assertThat(lastRequest().method()).isEqualTo("GET");
        assertThat(lastRequest().uri().getPath()).isEqualTo("/api/v1/athlete/i1");
    }

    @Test
    void asksForPlannedWorkoutsInTheRange() {
        stub.answer(200, Fixtures.bytes("events.json"), Map.of());

        IcuResult<List<IcuJson.Event>> result = client.plannedWorkouts(CREDENTIALS, OLDEST, NEWEST);

        assertThat(lastRequest().uri().getPath()).isEqualTo("/api/v1/athlete/i1/events");
        assertThat(query()).isEqualTo("oldest=2026-10-05&newest=2026-10-26&category=WORKOUT");
        assertThat(result)
                .isInstanceOfSatisfying(
                        IcuResult.Ok.class,
                        ok -> assertThat((List<?>) ok.value()).hasSize(2));
    }

    @Test
    void asksForActivitiesInTheRange() {
        stub.answer(200, Fixtures.bytes("activities.json"), Map.of());

        IcuResult<List<IcuJson.Activity>> result = client.activities(CREDENTIALS, OLDEST, NEWEST);

        assertThat(lastRequest().uri().getPath()).isEqualTo("/api/v1/athlete/i1/activities");
        assertThat(query()).isEqualTo("oldest=2026-10-05&newest=2026-10-26");
        assertThat(result)
                .isInstanceOfSatisfying(
                        IcuResult.Ok.class,
                        ok -> assertThat((List<?>) ok.value()).hasSize(1));
    }

    @Test
    void asksForWellnessInTheRange() {
        stub.answer(200, Fixtures.bytes("wellness.json"), Map.of());

        IcuResult<List<IcuJson.Wellness>> result = client.wellness(CREDENTIALS, OLDEST, NEWEST);

        assertThat(lastRequest().uri().getPath()).isEqualTo("/api/v1/athlete/i1/wellness");
        assertThat(query()).isEqualTo("oldest=2026-10-05&newest=2026-10-26");
        assertThat(result)
                .isInstanceOfSatisfying(
                        IcuResult.Ok.class,
                        ok -> assertThat((List<?>) ok.value()).hasSize(3));
    }

    @Test
    void aRefusedKeyIsUnauthorized() {
        stub.answer(401, "{\"status\":401,\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8), Map.of());

        assertThat(client.plannedWorkouts(CREDENTIALS, OLDEST, NEWEST)).isInstanceOf(IcuResult.Unauthorized.class);
    }

    @Test
    void aKeyForAnotherAthleteIsUnauthorized() {
        stub.answer(403, "{\"status\":403,\"error\":\"Forbidden\"}".getBytes(StandardCharsets.UTF_8), Map.of());

        assertThat(client.athlete(CREDENTIALS)).isInstanceOf(IcuResult.Unauthorized.class);
    }

    @Test
    void aRateLimitCarriesItsRetryAfter() {
        stub.answer(429, new byte[0], Map.of("Retry-After", "120"));

        assertThat(client.activities(CREDENTIALS, OLDEST, NEWEST))
                .isEqualTo(new IcuResult.RateLimited<>(Duration.ofSeconds(120)));
    }

    @Test
    void aRateLimitWithoutSecondsHasNoRetryAfter() {
        stub.answer(429, new byte[0], Map.of("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT"));

        assertThat(client.activities(CREDENTIALS, OLDEST, NEWEST)).isEqualTo(new IcuResult.RateLimited<>(null));
    }

    @Test
    void aServerErrorFails() {
        stub.answer(503, new byte[0], Map.of());

        assertThat(client.wellness(CREDENTIALS, OLDEST, NEWEST))
                .isEqualTo(new IcuResult.Failed<>("intervals.icu answered 503"));
    }

    @Test
    void unreadableJsonFails() {
        stub.answer(200, "<html>maintenance</html>".getBytes(StandardCharsets.UTF_8), Map.of());

        assertThat(client.athlete(CREDENTIALS)).isInstanceOf(IcuResult.Failed.class);
    }

    @Test
    void noAnswerFails() {
        IntervalsIcuClient unreachable = new IntervalsIcuClient(java.net.URI.create("http://localhost:1"));

        assertThat(unreachable.athlete(CREDENTIALS)).isInstanceOf(IcuResult.Failed.class);
    }

    @Test
    void credentialsNeverPrintTheKey() {
        assertThat(CREDENTIALS.toString()).doesNotContain("s3cret-key");
    }

    private StubIntervalsIcu.Request lastRequest() {
        return Objects.requireNonNull(stub.lastRequest(), "the stub saw no request");
    }

    private String query() {
        return lastRequest().uri().getRawQuery();
    }
}
