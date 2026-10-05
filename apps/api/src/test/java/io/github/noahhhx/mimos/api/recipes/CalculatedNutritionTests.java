package io.github.noahhhx.mimos.api.recipes;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A recipe's nutrition can be calculated from its ingredient lines' entries
 * in the shared catalog (ADR-0015). The expected values follow from the
 * seeded catalog: dried pasta 371 kcal, 13 g protein, 74.7 g carbs, 1.5 g
 * fat per 100 g; olive oil 813 kcal and 92 g fat per 100 ml; a garlic
 * clove 4 kcal, 0.2 g protein, 1 g carbs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalculatedNutritionTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void catalogIsSeededAndSearchable() {
        JsonNode all = get(accessToken(), "/api/v1/ingredients");
        assertThat(all.size()).isGreaterThanOrEqualTo(50);

        JsonNode oil = get(accessToken(), "/api/v1/ingredients?q=olive oil");
        assertThat(oil).hasSize(1);
        assertThat(oil.get(0).get("slug").asText()).isEqualTo("olive-oil");
        assertThat(oil.get(0).get("basis").asText()).isEqualTo("PER_100_ML");
        assertThat(oil.get(0).get("nutrition").get("calories").asDouble()).isEqualTo(813);
    }

    @Test
    void estimateReportsEachLineAndPerServingTotals() {
        ObjectNode input = objectMapper.createObjectNode().put("servings", 2);
        input.set("ingredients", aglioLines());

        JsonNode estimate = post(accessToken(), "/api/v1/recipes/nutrition-estimate", input);

        assertThat(estimate.get("lines").toString())
                .isEqualTo(
                        "[\"COUNTED\",\"COUNTED\",\"COUNTED\",\"UNMEASURED\",\"NOT_LINKED\",\"UNIT_NOT_SUPPORTED\"]");
        assertAglioNutrition(estimate.get("nutrition"));
    }

    @Test
    void estimateWithNothingCountedIsUnknown() {
        ObjectNode input = objectMapper.createObjectNode().put("servings", 2);
        input.set("ingredients", objectMapper.createArrayNode().add(line(2, "cups", "flour", "all-purpose-flour")));

        JsonNode estimate = post(accessToken(), "/api/v1/recipes/nutrition-estimate", input);

        assertThat(estimate.get("lines").toString()).isEqualTo("[\"UNIT_NOT_SUPPORTED\"]");
        assertThat(estimate.get("nutrition").has("calories")).isFalse();
    }

    @Test
    void calculatedRecipeIgnoresSubmittedNutritionAndFeedsLogging() {
        String token = accessToken(createUser());
        JsonNode created = post(token, "/api/v1/recipes", aglioRecipe());

        assertThat(created.get("nutritionSource").asText()).isEqualTo("INGREDIENTS");
        assertAglioNutrition(created.get("nutrition"));
        assertThat(created.get("ingredients").get(1).get("catalogSlug").asText())
                .isEqualTo("olive-oil");
        assertThat(created.get("ingredients").get(4).has("catalogSlug")).isFalse();
        assertAglioNutrition(
                get(token, "/api/v1/recipes/" + created.get("id").asText()).get("nutrition"));

        ObjectNode log = objectMapper
                .createObjectNode()
                .put("date", "2026-10-05")
                .put("mealType", "DINNER")
                .put("recipeId", created.get("id").asText())
                .put("servings", 2);
        JsonNode logged = post(token, "/api/v1/logs", log);
        assertThat(logged.get("nutrition").get("calories").asDouble()).isEqualTo(994);
    }

    @Test
    void calculatedNutritionFollowsTheCatalogWhenItChanges() {
        String token = accessToken(createUser());
        ObjectNode input = aglioRecipe();
        input.set("ingredients", objectMapper.createArrayNode().add(line(100, "g", "rice", "white-rice")));
        input.put("servings", 1);
        String id = post(token, "/api/v1/recipes", input).get("id").asText();
        assertThat(get(token, "/api/v1/recipes/" + id)
                        .get("nutrition")
                        .get("calories")
                        .asDouble())
                .isEqualTo(365);

        jdbc.update("update catalog_ingredient set calories = 360 where slug = 'white-rice'");
        try {
            assertThat(get(token, "/api/v1/recipes/" + id)
                            .get("nutrition")
                            .get("calories")
                            .asDouble())
                    .isEqualTo(360);
        } finally {
            jdbc.update("update catalog_ingredient set calories = 365 where slug = 'white-rice'");
        }
    }

    @Test
    void unknownCatalogSlugIsABadRequest() {
        ObjectNode input = aglioRecipe();
        input.set("ingredients", objectMapper.createArrayNode().add(line(1, "kg", "unobtainium", "unobtainium")));

        String problem = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .post()
                .uri("/api/v1/recipes")
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(input)
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(400);
                    return new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                });
        assertThat(problem).contains("unobtainium");
    }

    @Test
    void importKeepsExportedNutritionWhenACatalogEntryIsMissing() {
        String token = accessToken(createUser());
        ObjectNode recipe = aglioRecipe();
        recipe.put("id", "0b7c4f52-6a43-4f7e-8d55-3c1f0a9d2e01");
        recipe.set("nutrition", objectMapper.createObjectNode().put("calories", 512));
        ((ArrayNode) recipe.get("ingredients")).add(line(1, null, "dragon fruit", "dragon-fruit"));
        recipe.put("createdAt", "2026-10-01T10:00:00Z");
        recipe.put("updatedAt", "2026-10-01T10:00:00Z");
        ObjectNode document = objectMapper
                .createObjectNode()
                .put("format", "mimos.export")
                .put("version", 2)
                .put("exportedAt", "2026-10-02T09:00:00Z");
        document.set("recipes", objectMapper.createArrayNode().add(recipe));
        document.set("mealPlans", objectMapper.createArrayNode());
        document.set("shoppingLists", objectMapper.createArrayNode());
        document.set("mealLogs", objectMapper.createArrayNode());

        JsonNode report = post(token, "/api/v1/account/import", document);

        assertThat(report.get("warnings").toString()).contains("dragon-fruit").contains("Spaghetti Aglio");
        JsonNode imported = get(token, "/api/v1/recipes").get(0);
        JsonNode detail = get(token, "/api/v1/recipes/" + imported.get("id").asText());
        assertThat(detail.get("nutritionSource").asText()).isEqualTo("MANUAL");
        assertThat(detail.get("nutrition").get("calories").asDouble()).isEqualTo(512);
        assertThat(detail.get("ingredients").get(1).get("catalogSlug").asText()).isEqualTo("olive-oil");
        assertThat(detail.get("ingredients").get(6).has("catalogSlug")).isFalse();
    }

    private static void assertAglioNutrition(JsonNode nutrition) {
        // (742 + 243.9 + 8) kcal over 2 servings; spaghetti and garlic give the protein and carbs, oil the fat.
        assertThat(nutrition.get("calories").asDouble()).isEqualTo(497);
        assertThat(nutrition.get("proteinG").asDouble()).isEqualTo(13.2);
        assertThat(nutrition.get("carbsG").asDouble()).isEqualTo(75.7);
        assertThat(nutrition.get("fatG").asDouble()).isEqualTo(15.3);
    }

    /** Counted: 0.2 kg pasta, 2 TBSP oil (30 ml), 2 garlic cloves. Then one line of each other status. */
    private ArrayNode aglioLines() {
        return objectMapper
                .createArrayNode()
                .add(line(0.2, "kg", "spaghetti", "pasta"))
                .add(line(2, "TBSP", "olive oil", "olive-oil"))
                .add(line(2, null, "garlic cloves, sliced", "garlic-clove"))
                .add(objectMapper.createObjectNode().put("name", "salt").put("catalogSlug", "salt"))
                .add(line(20, "g", "parmesan, to serve", null))
                .add(line(1, "cup", "olive oil, for frying", "olive-oil"));
    }

    private ObjectNode aglioRecipe() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", "Spaghetti Aglio Calculated");
        input.put("description", "Calculated from its ingredients.");
        input.put("servings", 2);
        input.set("tags", objectMapper.createArrayNode());
        input.set("nutrition", objectMapper.createObjectNode().put("calories", 1));
        input.put("nutritionSource", "INGREDIENTS");
        input.set("ingredients", aglioLines());
        input.set(
                "steps",
                objectMapper
                        .createArrayNode()
                        .add(objectMapper.createObjectNode().put("instruction", "Cook.")));
        return input;
    }

    private ObjectNode line(double quantity, @Nullable String unit, String name, @Nullable String catalogSlug) {
        ObjectNode node =
                objectMapper.createObjectNode().put("quantity", quantity).put("name", name);
        if (unit != null) {
            node.put("unit", unit);
        }
        if (catalogSlug != null) {
            node.put("catalogSlug", catalogSlug);
        }
        return node;
    }

    private JsonNode get(String token, String uri) {
        return requireNonNull(
                RestClient.builder()
                        .baseUrl("http://localhost:" + port)
                        .build()
                        .get()
                        .uri(uri)
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "GET " + uri + " returned no body");
    }

    private JsonNode post(String token, String uri, JsonNode body) {
        return requireNonNull(
                RestClient.builder()
                        .baseUrl("http://localhost:" + port)
                        .build()
                        .post()
                        .uri(uri)
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class),
                "POST " + uri + " returned no body");
    }
}
