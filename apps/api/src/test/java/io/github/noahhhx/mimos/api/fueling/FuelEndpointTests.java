package io.github.noahhhx.mimos.api.fueling;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestUser;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FuelEndpointTests extends ApiIntegrationTestSupport {

    private static final String PROFILE = "/api/v1/me/fuel-profile";
    private static final String TRAINING = "/api/v1/me/training";
    private static final String FUEL = "/api/v1/me/fuel";
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    private TestUser user() {
        return new TestUser(port, objectMapper, jdbc);
    }

    @Test
    void aProfileNeverSavedReadsAsTargetsOff() {
        JsonNode profile = user().ok(HttpMethod.GET, PROFILE, null);

        assertThat(profile.get("enabled").asBoolean()).isFalse();
        assertThat(profile.get("goal").asText()).isEqualTo("FUEL");
        assertThat(profile.get("sex").asText()).isEqualTo("UNSPECIFIED");
        assertThat(profile.get("proteinGPerKg").asDouble()).isEqualTo(1.6);
        assertThat(profile.has("weightKg")).isFalse();
    }

    @Test
    void savingReplacesTheWholeProfile() {
        TestUser user = user();

        JsonNode saved = user.ok(HttpMethod.PUT, PROFILE, profile(70, "MALE", 280, "FUEL"));
        assertThat(saved.get("proteinGPerKg").asDouble())
                .as("protein left out takes the goal's default")
                .isEqualTo(1.6);
        assertThat(user.ok(HttpMethod.GET, PROFILE, null)).isEqualTo(saved);
        assertThat(saved.get("enabled").asBoolean()).isTrue();
        assertThat(saved.get("weightKg").asDouble()).isEqualTo(70);
        assertThat(saved.get("sex").asText()).isEqualTo("MALE");
        assertThat(saved.get("ftpWatts").asInt()).isEqualTo(280);

        Map<String, Object> replacement = Map.of(
                "enabled",
                false,
                "weightKg",
                64.5,
                "sex",
                "FEMALE",
                "bodyFatPercent",
                22,
                "goal",
                "FASTER_LOSS",
                "proteinGPerKg",
                2.4);
        JsonNode replaced = user.ok(HttpMethod.PUT, PROFILE, replacement);

        assertThat(user.ok(HttpMethod.GET, PROFILE, null)).isEqualTo(replaced);
        assertThat(replaced.get("enabled").asBoolean()).isFalse();
        assertThat(replaced.get("weightKg").asDouble()).isEqualTo(64.5);
        assertThat(replaced.get("bodyFatPercent").asDouble()).isEqualTo(22);
        assertThat(replaced.has("ftpWatts")).as("a value left out is cleared").isFalse();
        assertThat(replaced.get("goal").asText()).isEqualTo("FASTER_LOSS");
        assertThat(replaced.get("proteinGPerKg").asDouble()).isEqualTo(2.4);
    }

    @Test
    void valuesOutOfBoundsAreAllNamedAndNothingIsSaved() {
        TestUser user = user();
        JsonNode saved = user.ok(HttpMethod.PUT, PROFILE, profile(70, "MALE", 280, "FUEL"));

        TestUser.Response refused = user.send(
                HttpMethod.PUT,
                PROFILE,
                Map.of(
                        "enabled",
                        true,
                        "weightKg",
                        10,
                        "sex",
                        "MALE",
                        "bodyFatPercent",
                        80,
                        "goal",
                        "FUEL",
                        "ftpWatts",
                        601,
                        "proteinGPerKg",
                        4));

        assertThat(refused.status()).isEqualTo(400);
        assertThat(requireNonNull(refused.body()).get("detail").asText())
                .as("the Fuel page reads field names from this text; apps/web/test/fuel-fields.test.ts pins it too")
                .isEqualTo("weightKg must be between 30 and 250; bodyFatPercent must be between 3 and 60;"
                        + " ftpWatts must be between 50 and 600; proteinGPerKg must be between 1.2 and 3.0");
        assertThat(user.status(HttpMethod.PUT, PROFILE, Map.of("enabled", true, "sex", "MALE", "goal", "FUEL")))
                .as("weight is required")
                .isEqualTo(400);
        assertThat(user.ok(HttpMethod.GET, PROFILE, null)).isEqualTo(saved);
    }

    @Test
    void sessionsAreAddedAndReadByDateRange() {
        TestUser user = user();

        JsonNode ride = user.ok(HttpMethod.POST, TRAINING, session(MONDAY, "RIDE", 90, "ENDURANCE"));
        assertThat(ride.get("source").asText()).isEqualTo("MANUAL");
        assertThat(ride.get("kind").asText()).as("kind defaults to PLANNED").isEqualTo("PLANNED");
        assertThat(ride.get("durationMinutes").asInt()).isEqualTo(90);
        assertThat(ride.has("workKj")).isFalse();
        user.ok(
                HttpMethod.POST,
                TRAINING,
                Map.of(
                        "date",
                        MONDAY.plusDays(5).toString(),
                        "kind",
                        "COMPLETED",
                        "sport",
                        "RIDE",
                        "durationMinutes",
                        240,
                        "intensity",
                        "ENDURANCE",
                        "workKj",
                        2742));
        user.ok(HttpMethod.POST, TRAINING, session(MONDAY.plusDays(7), "RUN", 45, "HARD"));

        JsonNode week = user.ok(HttpMethod.GET, range(MONDAY, MONDAY.plusDays(6)), null);
        assertThat(describe(week)).containsExactly("2026-10-05 RIDE 90", "2026-10-10 RIDE 240");
        assertThat(week.get(1).get("workKj").asDouble()).isEqualTo(2742);
        assertThat(user.ok(HttpMethod.GET, TRAINING + "/" + ride.get("id").asText(), null))
                .isEqualTo(ride);
    }

    @Test
    void refusesASessionOutOfBoundsAndARangeTooLong() {
        TestUser user = user();

        assertThat(user.status(HttpMethod.POST, TRAINING, session(MONDAY, "RIDE", 0, "EASY")))
                .isEqualTo(400);
        assertThat(user.status(HttpMethod.POST, TRAINING, session(MONDAY, "RIDE", 1441, "EASY")))
                .isEqualTo(400);
        assertThat(user.status(
                        HttpMethod.POST,
                        TRAINING,
                        Map.of("date", MONDAY.toString(), "sport", "RIDE", "durationMinutes", 60)))
                .as("a hand-entered session needs an intensity")
                .isEqualTo(400);
        assertThat(user.status(HttpMethod.GET, range(MONDAY, MONDAY.plusDays(61)), null))
                .as("62 days, counting both ends")
                .isEqualTo(200);
        assertThat(user.status(HttpMethod.GET, range(MONDAY, MONDAY.plusDays(62)), null))
                .as("63 days, counting both ends")
                .isEqualTo(400);
        assertThat(user.status(HttpMethod.GET, range(MONDAY, MONDAY.minusDays(1)), null))
                .isEqualTo(400);
        assertThat(user.ok(HttpMethod.GET, range(MONDAY, MONDAY), null)).isEmpty();
    }

    @Test
    void refusesWorkNoDayReachesAndStoresNothing() {
        TestUser user = user();

        for (BigDecimal workKj : List.of(new BigDecimal("1e400"), new BigDecimal("51840.1"))) {
            TestUser.Response refused = user.send(HttpMethod.POST, TRAINING, allDayRide(workKj));
            assertThat(refused.status()).as("workKj %s", workKj).isEqualTo(400);
            assertThat(requireNonNull(refused.body()).get("detail").asText())
                    .isEqualTo("workKj must be between 0 and 51840");
        }
        user.ok(HttpMethod.POST, TRAINING, allDayRide(new BigDecimal(51840)));

        assertThat(describe(user.ok(HttpMethod.GET, range(MONDAY, MONDAY), null)))
                .containsExactly("2026-10-05 RIDE 1440");
        assertThat(user.status(HttpMethod.GET, "/api/v1/account/export", null)).isEqualTo(200);
    }

    @Test
    void refusesADateOutsideTheStoredRange() {
        TestUser user = user();

        for (String date : List.of("-5000-01-01", "+9999999-01-01", "1899-12-31", "+10000-01-01")) {
            TestUser.Response refused = user.send(
                    HttpMethod.POST,
                    TRAINING,
                    Map.of("date", date, "sport", "RIDE", "durationMinutes", 60, "intensity", "EASY"));
            assertThat(refused.status()).as("date %s", date).isEqualTo(400);
            assertThat(requireNonNull(refused.body()).get("detail").asText())
                    .isEqualTo("date must be between 1900-01-01 and 9999-12-31");
            assertThat(user.status(HttpMethod.GET, TRAINING + "?from=" + date + "&to=" + date, null))
                    .as("range at %s", date)
                    .isEqualTo(400);
        }
        assertThat(user.status(HttpMethod.POST, TRAINING, session(LocalDate.of(1900, 1, 1), "RUN", 30, "EASY")))
                .isEqualTo(201);
        assertThat(user.status(HttpMethod.POST, TRAINING, session(LocalDate.of(9999, 12, 31), "RUN", 30, "EASY")))
                .isEqualTo(201);
    }

    @Test
    void aSessionThatDoesNotExistIsNotFound() {
        TestUser user = user();
        String path = TRAINING + "/" + UUID.randomUUID();

        assertThat(user.status(HttpMethod.GET, path, null)).isEqualTo(404);
        assertThat(user.status(HttpMethod.DELETE, path, null)).isEqualTo(404);
    }

    @Test
    void deletesAHandEnteredSessionButNotASyncedOne() {
        TestUser user = user();
        String manual = user.ok(HttpMethod.POST, TRAINING, session(MONDAY, "SWIM", 30, "EASY"))
                .get("id")
                .asText();
        UUID synced = UUID.randomUUID();
        jdbc.update("""
                insert into training_session (id, profile_id, session_date, source, kind, sport, duration_minutes,
                                              external_id)
                values (?, ?, ?, 'INTERVALS_ICU', 'PLANNED', 'RIDE', 60, 'event-1')
                """, synced, user.profileId, Date.valueOf(MONDAY));

        assertThat(user.status(HttpMethod.DELETE, TRAINING + "/" + manual, null))
                .isEqualTo(204);
        assertThat(user.status(HttpMethod.GET, TRAINING + "/" + manual, null)).isEqualTo(404);
        assertThat(user.status(HttpMethod.DELETE, TRAINING + "/" + manual, null))
                .isEqualTo(404);

        TestUser.Response refused = user.send(HttpMethod.DELETE, TRAINING + "/" + synced, null);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(user.ok(HttpMethod.GET, range(MONDAY, MONDAY), null)
                        .get(0)
                        .get("source")
                        .asText())
                .isEqualTo("INTERVALS_ICU");
    }

    @Test
    void anotherPersonCannotReadOrDeleteASession() {
        RestClient api =
                RestClient.builder().baseUrl("http://localhost:" + port).build();
        String owner = accessToken("test");
        String other = accessToken("test2");
        String path = TRAINING + "/"
                + call(api, owner, HttpMethod.POST, TRAINING, session(MONDAY, "RIDE", 60, "EASY"), 201)
                        .get("id")
                        .asText();
        try {
            call(api, other, HttpMethod.GET, path, null, 404);
            call(api, other, HttpMethod.DELETE, path, null, 404);
            call(api, owner, HttpMethod.GET, path, null, 200);
        } finally {
            call(api, owner, HttpMethod.DELETE, path, null, 204);
        }
    }

    @Test
    void eachHouseholdMemberKeepsTheirOwnProfileThroughJoinAndLeave() {
        TestUser host = user();
        TestUser guest = user();
        JsonNode hostProfile = host.ok(HttpMethod.PUT, PROFILE, profile(82, "MALE", 300, "GAIN"));
        JsonNode guestProfile = guest.ok(HttpMethod.PUT, PROFILE, profile(58, "FEMALE", 210, "GRADUAL_LOSS"));
        guest.ok(HttpMethod.POST, TRAINING, session(MONDAY, "RUN", 40, "HARD"));

        guest.join(host);
        assertThat(host.ok(HttpMethod.GET, PROFILE, null)).isEqualTo(hostProfile);
        assertThat(guest.ok(HttpMethod.GET, PROFILE, null)).isEqualTo(guestProfile);
        assertThat(host.ok(HttpMethod.GET, range(MONDAY, MONDAY), null)).isEmpty();
        assertThat(guest.ok(HttpMethod.GET, range(MONDAY, MONDAY), null)).hasSize(1);

        guest.ok(HttpMethod.POST, "/api/v1/household/leave", null);
        assertThat(guest.ok(HttpMethod.GET, PROFILE, null)).isEqualTo(guestProfile);
        assertThat(guest.ok(HttpMethod.GET, range(MONDAY, MONDAY), null)).hasSize(1);
    }

    @Test
    void fuelGivesEachDayItsTargetFromTheProfileAndTraining() {
        TestUser user = user();
        user.ok(HttpMethod.PUT, PROFILE, sketchProfile(true));
        String ride = user.ok(HttpMethod.POST, TRAINING, session(MONDAY.plusDays(5), "RIDE", 240, "ENDURANCE"))
                .get("id")
                .asText();

        JsonNode fuel = user.ok(HttpMethod.GET, fuel(MONDAY, MONDAY.plusDays(6)), null);

        assertThat(fuel.get("from").asText()).isEqualTo("2026-10-05");
        assertThat(fuel.get("days")).hasSize(7);
        JsonNode monday = fuel.get("days").get(0);
        assertThat(monday.get("date").asText()).isEqualTo("2026-10-05");
        assertThat(monday.get("target").get("calories").asInt()).isEqualTo(2678);
        assertThat(monday.get("target").get("carbsG").asInt()).isEqualTo(350);
        assertThat(monday.get("target").get("proteinG").asInt()).isEqualTo(112);
        assertThat(monday.get("target").get("basis").get("loadBand").asText()).isEqualTo("LIGHT");
        assertThat(monday.get("target").get("basis").get("fatFreeMassKg").asDouble())
                .isEqualTo(59.5);
        assertThat(monday.get("target").get("basis").get("bodyFatSource").asText())
                .isEqualTo("PROFILE");
        assertThat(monday.get("sessions")).isEmpty();
        JsonNode saturday = fuel.get("days").get(5);
        assertThat(saturday.get("target").get("calories").asInt()).isEqualTo(5419);
        assertThat(saturday.get("target").get("carbsG").asInt()).isEqualTo(840);
        JsonNode basis = saturday.get("target").get("basis");
        assertThat(basis.get("loadBand").asText()).isEqualTo("VERY_HIGH");
        assertThat(basis.get("exerciseKcal").asInt()).isEqualTo(2742);
        assertThat(basis.get("sessions").get(0).get("sessionId").asText()).isEqualTo(ride);
        assertThat(basis.get("sessions").get(0).get("method").asText()).isEqualTo("FTP_INTENSITY");
        assertThat(saturday.get("sessions").get(0).get("id").asText()).isEqualTo(ride);
    }

    @Test
    void withTargetsOffEveryDayStillHasItsPlannedLoggedAndTraining() {
        TestUser user = user();
        assertThat(user.ok(HttpMethod.GET, fuel(MONDAY, MONDAY), null)
                        .get("days")
                        .get(0)
                        .has("target"))
                .as("a profile never saved has targets off")
                .isFalse();
        user.ok(HttpMethod.PUT, PROFILE, sketchProfile(false));
        user.ok(HttpMethod.POST, TRAINING, session(MONDAY, "RUN", 30, "EASY"));
        UUID recipe = user.recipe("Off Day Stew");
        user.ok(
                HttpMethod.POST,
                "/api/v1/plans/" + MONDAY + "/entries",
                Map.of("date", MONDAY.toString(), "mealType", "DINNER", "recipeId", recipe, "servings", 2));
        user.ok(
                HttpMethod.POST,
                "/api/v1/logs",
                Map.of("date", MONDAY.toString(), "mealType", "DINNER", "recipeId", recipe, "servings", 2));

        JsonNode day = user.ok(HttpMethod.GET, fuel(MONDAY, MONDAY.plusDays(1)), null)
                .get("days")
                .get(0);

        assertThat(day.has("target")).isFalse();
        assertThat(day.get("sessions")).hasSize(1);
        assertThat(day.get("planned").get("meals").asInt()).isEqualTo(1);
        assertThat(day.get("planned").get("total").get("calories").asDouble()).isEqualTo(800);
        assertThat(day.get("logged")).isEqualTo(day.get("planned").get("total"));
        JsonNode tuesday = user.ok(HttpMethod.GET, fuel(MONDAY, MONDAY.plusDays(1)), null)
                .get("days")
                .get(1);
        assertThat(tuesday.get("logged").get("calories").asDouble()).isZero();
        assertThat(tuesday.get("planned").get("meals").asInt()).isZero();
    }

    @Test
    void fuelCoversAtMostSixtyTwoDaysAcrossWeeks() {
        TestUser user = user();
        user.ok(HttpMethod.PUT, PROFILE, sketchProfile(true));
        LocalDate wednesday = MONDAY.plusDays(2);

        JsonNode longest = user.ok(HttpMethod.GET, fuel(wednesday, wednesday.plusDays(61)), null);
        assertThat(longest.get("days")).hasSize(62);
        assertThat(longest.get("days").get(0).get("date").asText()).isEqualTo(wednesday.toString());
        assertThat(longest.get("days").get(61).get("date").asText())
                .isEqualTo(wednesday.plusDays(61).toString());
        assertThat(longest.get("days").get(61).get("planned").get("date").asText())
                .isEqualTo(wednesday.plusDays(61).toString());

        TestUser.Response tooLong = user.send(HttpMethod.GET, fuel(wednesday, wednesday.plusDays(62)), null);
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(requireNonNull(tooLong.body()).get("detail").asText()).isEqualTo("range must be at most 62 days");
        assertThat(user.status(HttpMethod.GET, fuel(MONDAY, MONDAY.minusDays(1)), null))
                .isEqualTo(400);
    }

    @Test
    void householdMembersEachGetTheirOwnTargetsAndShareOfADinner() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        host.ok(HttpMethod.PUT, PROFILE, profile(82, "MALE", 300, "GAIN"));
        guest.ok(HttpMethod.PUT, PROFILE, profile(58, "FEMALE", 210, "GRADUAL_LOSS"));
        host.ok(HttpMethod.POST, TRAINING, session(MONDAY, "RIDE", 90, "HARD"));
        guest.ok(HttpMethod.POST, TRAINING, session(MONDAY, "RUN", 40, "EASY"));
        host.ok(
                HttpMethod.POST,
                "/api/v1/plans/" + MONDAY + "/entries",
                Map.of(
                        "date",
                        MONDAY.toString(),
                        "mealType",
                        "DINNER",
                        "recipeId",
                        host.recipe("Shared Dinner"),
                        "servings",
                        2));

        JsonNode hostDay =
                host.ok(HttpMethod.GET, fuel(MONDAY, MONDAY), null).get("days").get(0);
        JsonNode guestDay =
                guest.ok(HttpMethod.GET, fuel(MONDAY, MONDAY), null).get("days").get(0);

        assertThat(hostDay.get("target").get("calories").asInt())
                .as("50 x 69.7 kg + 300 W x 0.85 x 90 min")
                .isEqualTo(4862);
        assertThat(guestDay.get("target").get("calories").asInt())
                .as("40 x 43.5 kg + 4 METs x 58 kg x 40 min")
                .isEqualTo(1902);
        assertThat(hostDay.get("planned").get("total").get("calories").asDouble())
                .isEqualTo(400);
        assertThat(guestDay.get("planned")).isEqualTo(hostDay.get("planned"));
        assertThat(describe(hostDay.get("sessions"))).containsExactly("2026-10-05 RIDE 90");
        assertThat(describe(guestDay.get("sessions"))).containsExactly("2026-10-05 RUN 40");
    }

    private static Map<String, Object> sketchProfile(boolean enabled) {
        return Map.of(
                "enabled",
                enabled,
                "weightKg",
                70,
                "sex",
                "MALE",
                "bodyFatPercent",
                15,
                "ftpWatts",
                280,
                "goal",
                "FUEL");
    }

    private static String fuel(LocalDate from, LocalDate to) {
        return FUEL + "?from=" + from + "&to=" + to;
    }

    private static Map<String, Object> profile(double weightKg, String sex, int ftpWatts, String goal) {
        return Map.of("enabled", true, "weightKg", weightKg, "sex", sex, "ftpWatts", ftpWatts, "goal", goal);
    }

    private static Map<String, Object> session(LocalDate date, String sport, int minutes, String intensity) {
        return Map.of("date", date.toString(), "sport", sport, "durationMinutes", minutes, "intensity", intensity);
    }

    private static Map<String, Object> allDayRide(BigDecimal workKj) {
        return Map.of(
                "date",
                MONDAY.toString(),
                "sport",
                "RIDE",
                "durationMinutes",
                1440,
                "intensity",
                "HARD",
                "workKj",
                workKj);
    }

    private static String range(LocalDate from, LocalDate to) {
        return TRAINING + "?from=" + from + "&to=" + to;
    }

    private static List<String> describe(JsonNode sessions) {
        List<String> described = new ArrayList<>();
        sessions.forEach(session -> described.add(session.get("date").asText() + " "
                + session.get("sport").asText() + " "
                + session.get("durationMinutes").asInt()));
        return described;
    }

    private JsonNode call(
            RestClient api, String token, HttpMethod method, String uri, @Nullable Object body, int expectedStatus) {
        RestClient.RequestBodySpec request =
                api.method(method).uri(uri).headers(headers -> headers.setBearerAuth(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.exchange((req, res) -> {
            byte[] bytes = res.getBody().readAllBytes();
            assertThat(res.getStatusCode().value())
                    .as("%s %s answered %s", method, uri, new String(bytes))
                    .isEqualTo(expectedStatus);
            return bytes.length == 0 ? objectMapper.createObjectNode() : objectMapper.readTree(bytes);
        });
    }
}
