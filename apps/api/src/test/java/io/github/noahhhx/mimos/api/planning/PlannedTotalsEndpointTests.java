package io.github.noahhhx.mimos.api.planning;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestRecipes;
import io.github.noahhhx.mimos.api.support.TestUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class PlannedTotalsEndpointTests extends ApiIntegrationTestSupport {

    private static final LocalDate MONDAY = LocalDate.of(2026, 4, 6);
    private static final List<Double> STEW = List.of(400.0, 20.0, 40.0, 10.0);

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void twoMembersOfOneHouseholdEachGetTheirOwnShare() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        UUID stew = recipe(host, "Totals Stew", STEW);
        host.ok(HttpMethod.POST, entries(MONDAY), entry(MONDAY, "DINNER", stew, 4));
        host.ok(HttpMethod.POST, entries(MONDAY), entry(MONDAY, "LUNCH", stew, 1));

        JsonNode hostMonday =
                days(host.ok(HttpMethod.GET, totals(MONDAY), null)).getFirst();
        JsonNode guestMonday =
                days(guest.ok(HttpMethod.GET, totals(MONDAY), null)).getFirst();

        assertThat(hostMonday.get("date").asText()).isEqualTo(MONDAY.toString());
        assertThat(hostMonday.get("meals").asInt()).isEqualTo(2);
        assertThat(nutrition(hostMonday)).containsExactlyElementsOf(times(STEW, 3));
        assertThat(guestMonday.get("meals").asInt()).isEqualTo(1);
        assertThat(nutrition(guestMonday)).containsExactlyElementsOf(times(STEW, 2));
    }

    @Test
    void aPlannedDayEqualsTheSameDayLoggedAsPlanned() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        UUID odd = recipe(host, "Odd Stew", List.of(333.33, 12.34, 45.67, 8.91));
        UUID partial = recipeWithoutCalories(host, "Odd Broth", 0.05, 4.21, 2.33);
        host.ok(HttpMethod.POST, entries(MONDAY), entry(MONDAY, "DINNER", odd, 3));
        guest.ok(HttpMethod.POST, entries(MONDAY), entry(MONDAY, "LUNCH", odd, 1.25));
        guest.ok(HttpMethod.POST, entries(MONDAY), entry(MONDAY, "BREAKFAST", partial, 1));
        guest.ok(HttpMethod.POST, entries(MONDAY), entry(MONDAY, "SNACK", partial, 1.25));
        guest.ok(HttpMethod.POST, "/api/v1/logs", log("DINNER", odd, 1.5));
        guest.ok(HttpMethod.POST, "/api/v1/logs", log("LUNCH", odd, 1.25));
        guest.ok(HttpMethod.POST, "/api/v1/logs", log("BREAKFAST", partial, 1));
        guest.ok(HttpMethod.POST, "/api/v1/logs", log("SNACK", partial, 1.25));

        List<Double> planned =
                nutrition(days(guest.ok(HttpMethod.GET, totals(MONDAY), null)).getFirst());
        JsonNode logged = guest.ok(HttpMethod.GET, "/api/v1/logs/summary?from=" + MONDAY + "&to=" + MONDAY, null)
                .get(0);

        assertThat(planned.get(0)).isCloseTo(logged.get("calories").asDouble(), within(1e-9));
        assertThat(planned.get(1)).isCloseTo(logged.get("proteinG").asDouble(), within(1e-9));
        assertThat(planned.get(2)).isCloseTo(logged.get("carbsG").asDouble(), within(1e-9));
        assertThat(planned.get(3)).isCloseTo(logged.get("fatG").asDouble(), within(1e-9));
    }

    @Test
    void aWeekIsSevenDaysAndAnEmptyDayIsZero() {
        TestUser cook = user();
        UUID stew = recipe(cook, "Sunday Stew", STEW);
        cook.ok(HttpMethod.POST, entries(MONDAY), entry(MONDAY.plusDays(6), "DINNER", stew, 2));

        JsonNode body = cook.ok(HttpMethod.GET, totals(MONDAY), null);
        List<JsonNode> days = days(body);

        assertThat(body.get("startDate").asText()).isEqualTo(MONDAY.toString());
        assertThat(days)
                .extracting(day -> day.get("date").asText())
                .containsExactly(
                        "2026-04-06",
                        "2026-04-07",
                        "2026-04-08",
                        "2026-04-09",
                        "2026-04-10",
                        "2026-04-11",
                        "2026-04-12");
        assertThat(nutrition(days.getFirst())).containsExactly(0.0, 0.0, 0.0, 0.0);
        assertThat(days.getFirst().get("meals").asInt()).isZero();
        assertThat(nutrition(days.getLast())).containsExactlyElementsOf(times(STEW, 2));
    }

    @Test
    void aMealWithoutCaloriesIsCountedApartAndAddsOnlyItsKnownValues() {
        TestUser cook = user();
        UUID stew = recipe(cook, "Known Stew", STEW);
        UUID soupId = recipeWithoutCalories(cook, "Mystery Soup", 5, 12, 3);
        cook.ok(HttpMethod.POST, entries(MONDAY), entry(MONDAY, "DINNER", stew, 1));
        cook.ok(HttpMethod.POST, entries(MONDAY), entry(MONDAY, "LUNCH", soupId, 2));

        JsonNode monday = days(cook.ok(HttpMethod.GET, totals(MONDAY), null)).getFirst();

        assertThat(monday.get("meals").asInt()).isEqualTo(2);
        assertThat(monday.get("mealsWithoutNutrition").asInt()).isEqualTo(1);
        assertThat(nutrition(monday)).containsExactly(400.0, 30.0, 64.0, 16.0);
    }

    @Test
    void aStartDateThatIsNotAMondayIsABadRequest() {
        TestUser cook = user();

        TestUser.Response response = cook.send(HttpMethod.GET, totals(MONDAY.plusDays(1)), null);

        assertThat(response.status()).isEqualTo(400);
        assertThat(requireNonNull(response.body()).get("detail").asText())
                .isEqualTo("startDate must be the Monday of the week");
    }

    @Test
    void anotherHouseholdNeverSeesTheMeals() {
        LocalDate monday = LocalDate.of(2030, 1, 7);
        RestClient api =
                RestClient.builder().baseUrl("http://localhost:" + port).build();
        String token = accessToken("test");
        JsonNode recipe = TestRecipes.create(api, token, "Private Stew");
        api.post()
                .uri(entries(monday))
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(entry(monday, "DINNER", UUID.fromString(recipe.get("id").asText()), 2))
                .retrieve()
                .toBodilessEntity();

        JsonNode others = requireNonNull(api.get()
                .uri(totals(monday))
                .headers(headers -> headers.setBearerAuth(accessToken("test2")))
                .retrieve()
                .body(JsonNode.class));
        JsonNode own = requireNonNull(api.get()
                .uri(totals(monday))
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(JsonNode.class));

        assertThat(days(others)).extracting(day -> day.get("meals").asInt()).containsOnly(0);
        assertThat(days(own).getFirst().get("meals").asInt()).isPositive();
    }

    private TestUser user() {
        return new TestUser(port, objectMapper, jdbc);
    }

    private static UUID recipe(TestUser owner, String title, List<Double> perServing) {
        Map<String, Object> input = recipeInput(title);
        input.put("nutrition", nutritionInput(perServing));
        return UUID.fromString(
                owner.ok(HttpMethod.POST, "/api/v1/recipes", input).get("id").asText());
    }

    private static UUID recipeWithoutCalories(
            TestUser owner, String title, double proteinG, double carbsG, double fatG) {
        Map<String, Object> input = recipeInput(title);
        input.put("nutrition", Map.of("proteinG", proteinG, "carbsG", carbsG, "fatG", fatG));
        return UUID.fromString(
                owner.ok(HttpMethod.POST, "/api/v1/recipes", input).get("id").asText());
    }

    private static Map<String, Object> recipeInput(String title) {
        Map<String, Object> input = new HashMap<>();
        input.put("title", title);
        input.put("description", "Written by PlannedTotalsEndpointTests.");
        input.put("servings", 2);
        input.put("tags", List.of());
        input.put("nutritionSource", "MANUAL");
        input.put("ingredients", List.of(Map.of("quantity", 1, "unit", "l", "name", "Stock")));
        input.put("steps", List.of(Map.of("instruction", "Heat it.")));
        return input;
    }

    private static Map<String, Double> nutritionInput(List<Double> perServing) {
        return Map.of(
                "calories", perServing.get(0),
                "proteinG", perServing.get(1),
                "carbsG", perServing.get(2),
                "fatG", perServing.get(3));
    }

    private static List<Double> times(List<Double> perServing, double servings) {
        return perServing.stream().map(value -> value * servings).toList();
    }

    private static String entries(LocalDate monday) {
        return "/api/v1/plans/" + monday + "/entries";
    }

    private static String totals(LocalDate monday) {
        return "/api/v1/plans/" + monday + "/my-totals";
    }

    private static Map<String, Object> entry(LocalDate date, String mealType, UUID recipeId, double servings) {
        return Map.of("date", date.toString(), "mealType", mealType, "recipeId", recipeId, "servings", servings);
    }

    private static Map<String, Object> log(String mealType, UUID recipeId, double servings) {
        return Map.of("date", MONDAY.toString(), "mealType", mealType, "recipeId", recipeId, "servings", servings);
    }

    private static List<JsonNode> days(JsonNode body) {
        List<JsonNode> days = new ArrayList<>();
        body.get("days").forEach(days::add);
        return days;
    }

    private static List<Double> nutrition(JsonNode day) {
        JsonNode total = day.get("total");
        return List.of(
                total.get("calories").asDouble(),
                total.get("proteinG").asDouble(),
                total.get("carbsG").asDouble(),
                total.get("fatG").asDouble());
    }
}
