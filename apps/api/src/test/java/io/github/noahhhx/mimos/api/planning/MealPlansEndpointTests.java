package io.github.noahhhx.mimos.api.planning;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestRecipes;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Meal planning end to end: plans appear (empty) on first access, meals can
 * be planned from own and library recipes, and weeks are validated and
 * isolated per user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MealPlansEndpointTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void planLifecycle() {
        RestClient api = api();
        String token = accessToken();
        LocalDate monday = LocalDate.of(2026, 1, 5); // a Monday
        JsonNode recipe = TestRecipes.create(api, token, "Planned Curry");

        // First access returns an empty plan for the week.
        JsonNode empty = getPlan(api, token, monday);
        assertThat(empty.get("startDate").asText()).isEqualTo(monday.toString());
        assertThat(empty.get("entries")).isEmpty();

        // Plan a dinner from it.
        JsonNode entry = addEntry(
                api,
                token,
                monday,
                monday.plusDays(2),
                "DINNER",
                recipe.get("id").asText(),
                3);
        assertThat(entry.get("recipeTitle").asText()).isEqualTo("Planned Curry");
        assertThat(entry.get("servings").asDouble()).isEqualTo(3);

        // The plan now shows the entry.
        JsonNode plan = getPlan(api, token, monday);
        assertThat(plan.get("entries")).hasSize(1);
        assertThat(plan.get("entries").get(0).get("recipeId").asText())
                .isEqualTo(recipe.get("id").asText());

        // Change servings.
        UUID entryId = UUID.fromString(entry.get("id").asText());
        ObjectNode patch = objectMapper.createObjectNode().put("servings", 4);
        JsonNode updated = requireNonNull(
                api.patch()
                        .uri(builder -> builder.path("/api/v1/plans/{start}/entries/{id}")
                                .build(monday.toString(), entryId.toString()))
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(patch)
                        .retrieve()
                        .body(JsonNode.class),
                "patch returned no body");
        assertThat(updated.get("servings").asDouble()).isEqualTo(4);

        // Remove it again.
        api.delete()
                .uri(builder ->
                        builder.path("/api/v1/plans/{start}/entries/{id}").build(monday.toString(), entryId.toString()))
                .headers(headers -> headers.setBearerAuth(token))
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(204);
                    return null;
                });
        assertThat(getPlan(api, token, monday).get("entries")).isEmpty();
    }

    @Test
    void weekAndVisibilityRules() {
        RestClient api = api();
        String token = accessToken();
        LocalDate monday = LocalDate.of(2026, 1, 5); // a Monday
        JsonNode recipe = TestRecipes.create(api, token, "Rules Stew");

        // startDate must be a Monday.
        api.get()
                .uri("/api/v1/plans/{start}", "2026-01-06")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(400);
                    return null;
                });

        // Entry dates must fall inside the week.
        api.post()
                .uri("/api/v1/plans/{start}/entries", monday.toString())
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(entryBody(
                        objectMapper, "2026-01-12", "LUNCH", recipe.get("id").asText(), 1))
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(400);
                    return null;
                });

        // Someone else's recipe cannot be planned.
        String otherToken = accessToken("test2");
        api.post()
                .uri("/api/v1/plans/{start}/entries", monday.toString())
                .headers(headers -> headers.setBearerAuth(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(entryBody(
                        objectMapper, "2026-01-06", "LUNCH", recipe.get("id").asText(), 1))
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(404);
                    return null;
                });

        // Unknown recipe -> 404.
        api.post()
                .uri("/api/v1/plans/{start}/entries", monday.toString())
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(entryBody(
                        objectMapper, "2026-01-06", "LUNCH", UUID.randomUUID().toString(), 1))
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(404);
                    return null;
                });
    }

    private JsonNode getPlan(RestClient api, String token, LocalDate startDate) {
        return requireNonNull(
                api.get()
                        .uri("/api/v1/plans/{start}", startDate.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "plan returned no body");
    }

    private JsonNode addEntry(
            RestClient api,
            String token,
            LocalDate startDate,
            LocalDate date,
            String mealType,
            String recipeId,
            double servings) {
        return requireNonNull(
                api.post()
                        .uri("/api/v1/plans/{start}/entries", startDate.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(entryBody(objectMapper, date.toString(), mealType, recipeId, servings))
                        .retrieve()
                        .body(JsonNode.class),
                "add entry returned no body");
    }

    private static ObjectNode entryBody(
            ObjectMapper mapper, String date, String mealType, String recipeId, double servings) {
        return mapper.createObjectNode()
                .put("date", date)
                .put("mealType", mealType)
                .put("recipeId", recipeId)
                .put("servings", servings);
    }
}
