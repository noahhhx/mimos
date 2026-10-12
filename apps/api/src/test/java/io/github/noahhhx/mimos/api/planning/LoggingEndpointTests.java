package io.github.noahhhx.mimos.api.planning;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestRecipes;
import java.time.LocalDate;
import java.util.List;
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
 * Calorie and macro logging end to end: planned meals become logged meals
 * with nutrition derived from the recipe, ad-hoc meals carry manual totals,
 * and daily summaries add up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoggingEndpointTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void plannedMealsBecomeLoggedMealsWithDerivedNutrition() {
        RestClient api = api();
        String token = accessToken();
        LocalDate date = LocalDate.of(2026, 3, 3);

        // TestRecipes.create: 400 kcal per serving.
        JsonNode recipe = TestRecipes.create(api, token, "Logged Lasagna");

        ObjectNode fromRecipe = objectMapper
                .createObjectNode()
                .put("date", date.toString())
                .put("mealType", "DINNER")
                .put("recipeId", recipe.get("id").asText())
                .put("servings", 2);
        JsonNode logged = requireNonNull(
                api.post()
                        .uri("/api/v1/logs")
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fromRecipe)
                        .retrieve()
                        .body(JsonNode.class),
                "log returned no body");
        assertThat(logged.get("description").asText()).isEqualTo("Logged Lasagna");
        assertThat(logged.get("nutrition").get("calories").asDouble()).isEqualTo(800); // 400 × 2
        assertThat(logged.get("nutrition").get("proteinG").asDouble()).isEqualTo(40); // 20 × 2
        assertThat(logged.get("recipeId").asText()).isEqualTo(recipe.get("id").asText());

        // Ad-hoc log with manual totals.
        ObjectNode adHoc = objectMapper
                .createObjectNode()
                .put("date", date.toString())
                .put("mealType", "SNACK")
                .put("description", "Handful of almonds")
                .put("servings", 1);
        adHoc.set(
                "nutrition",
                objectMapper
                        .createObjectNode()
                        .put("calories", 180)
                        .put("fatG", 16)
                        .put("proteinG", 6));
        JsonNode snack = requireNonNull(
                api.post()
                        .uri("/api/v1/logs")
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(adHoc)
                        .retrieve()
                        .body(JsonNode.class),
                "ad-hoc log returned no body");
        assertThat(snack.get("recipeId")).isNull();
        assertThat(snack.get("nutrition").get("calories").asDouble()).isEqualTo(180);

        // Summary for the day adds both up.
        JsonNode summary = summary(api, token, date, date);
        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).get("date").asText()).isEqualTo(date.toString());
        assertThat(summary.get(0).get("calories").asDouble()).isEqualTo(980); // 800 + 180
        assertThat(summary.get(0).get("proteinG").asDouble()).isEqualTo(46); // 40 + 6
        assertThat(summary.get(0).get("carbsG").asDouble()).isEqualTo(80); // 40×2 + 0
        assertThat(summary.get(0).get("fatG").asDouble()).isEqualTo(36); // 10×2 + 16

        // Range listing shows both entries.
        JsonNode logs = list(api, token, date, date);
        assertThat(logs).hasSize(2);

        // Deleting works and is scoped to the owner.
        UUID logId = UUID.fromString(logged.get("id").asText());
        api.delete()
                .uri("/api/v1/logs/{id}", logId.toString())
                .headers(headers -> headers.setBearerAuth(accessToken("test2")))
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(404);
                    return null;
                });
        api.delete()
                .uri("/api/v1/logs/{id}", logId.toString())
                .headers(headers -> headers.setBearerAuth(token))
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(204);
                    return null;
                });
        assertThat(list(api, token, date, date)).hasSize(1);
    }

    @Test
    void adHocLogWithoutDescriptionIsRejected() {
        RestClient api = api();
        String token = accessToken();
        LocalDate date = LocalDate.of(2026, 3, 4);

        ObjectNode invalid = objectMapper
                .createObjectNode()
                .put("date", date.toString())
                .put("mealType", "LUNCH")
                .put("servings", 1);
        api.post()
                .uri("/api/v1/logs")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalid)
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(400);
                    JsonNode problem = objectMapper.readTree(res.getBody().readAllBytes());
                    assertThat(problem.get("title").asText()).isEqualTo("Invalid request");
                    return null;
                });

        // Reversed ranges are rejected too.
        api.get()
                .uri(builder -> builder.path("/api/v1/logs")
                        .queryParam("from", date.plusDays(1).toString())
                        .queryParam("to", date.toString())
                        .build())
                .headers(headers -> headers.setBearerAuth(token))
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(400);
                    return null;
                });
    }

    @Test
    void aRangeIsAtMostSixtyTwoDaysCountingBothEnds() {
        RestClient api = api();
        String token = accessToken();
        LocalDate from = LocalDate.of(2026, 3, 4);

        for (String path : List.of("/api/v1/logs", "/api/v1/logs/summary")) {
            assertThat(rangeStatus(api, token, path, from, from.plusDays(61)))
                    .as("%s over 62 days", path)
                    .isEqualTo("200");
            assertThat(rangeStatus(api, token, path, from, from.plusDays(62)))
                    .as("%s over 63 days", path)
                    .isEqualTo("400 range must be at most 62 days");
        }
    }

    private String rangeStatus(RestClient api, String token, String path, LocalDate from, LocalDate to) {
        return api.get()
                .uri(builder -> builder.path(path)
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .build())
                .headers(headers -> headers.setBearerAuth(token))
                .exchange((req, res) -> {
                    int status = res.getStatusCode().value();
                    return status == 200
                            ? "200"
                            : status + " "
                                    + objectMapper
                                            .readTree(res.getBody().readAllBytes())
                                            .get("detail")
                                            .asText();
                });
    }

    private JsonNode list(RestClient api, String token, LocalDate from, LocalDate to) {
        return requireNonNull(
                api.get()
                        .uri(builder -> builder.path("/api/v1/logs")
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .build())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "logs returned no body");
    }

    private JsonNode summary(RestClient api, String token, LocalDate from, LocalDate to) {
        return requireNonNull(
                api.get()
                        .uri(builder -> builder.path("/api/v1/logs/summary")
                                .queryParam("from", from.toString())
                                .queryParam("to", to.toString())
                                .build())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "summary returned no body");
    }
}
