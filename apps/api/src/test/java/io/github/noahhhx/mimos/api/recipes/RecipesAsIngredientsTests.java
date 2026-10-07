package io.github.noahhhx.mimos.api.recipes;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.account.ExportUpgrader;
import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.nio.charset.StandardCharsets;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * One of your recipes can be an ingredient of another, counted in servings
 * of it (ADR-0018). The focaccia here is 500 g all-purpose flour (364 kcal,
 * 10.3 g protein, 76.3 g carbs, 1 g fat per 100 g), 50 ml olive oil (813
 * kcal, 92 g fat per 100 ml), and 1 tsp salt, in 8 servings: 278 kcal, 6.4
 * g protein, 47.7 g carbs, and 6.4 g fat each. The sandwich is 2 servings
 * of it and 2 tomatoes (22 kcal, 1.1 g protein, 4.8 g carbs, 0.2 g fat
 * each), in 2 servings.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipesAsIngredientsTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aSandwichCountsServingsOfTheFocacciaAsItIsNow() {
        String token = accessToken(createUser());
        String focaccia = create(token, focaccia(8)).get("id").asText();

        JsonNode sandwich = create(token, sandwich(focaccia));

        // (2 × 278 + 2 × 22) kcal over 2 servings, and the same for each macro.
        assertNutrition(sandwich.get("nutrition"), 300, 7.5, 52.5, 6.6);
        assertThat(sandwich.get("ingredients").get(0).get("recipeId").asText()).isEqualTo(focaccia);
        assertThat(sandwich.get("ingredients").get(0).has("catalogSlug")).isFalse();
        String sandwichId = sandwich.get("id").asText();

        update(token, focaccia, focaccia(4));

        // Halving the focaccia's servings doubles a serving of it: 557 kcal now.
        JsonNode reread = get(token, "/api/v1/recipes/" + sandwichId);
        assertThat(reread.get("nutrition").get("calories").asDouble()).isEqualTo(579);
        JsonNode listed = get(token, "/api/v1/recipes?q=sandwich").get(0);
        assertThat(listed.get("nutrition").get("calories").asDouble()).isEqualTo(579);
    }

    @Test
    void aTypedRecipeCountsWhatItsAuthorTyped() {
        String token = accessToken(createUser());
        ObjectNode pesto = recipe("Jar of pesto", 4, "MANUAL", lines(line(1, null, "pesto", null, null)));
        pesto.set(
                "nutrition",
                objectMapper
                        .createObjectNode()
                        .put("calories", 120)
                        .put("proteinG", 1.5)
                        .put("carbsG", 2)
                        .put("fatG", 12));
        String pestoId = create(token, pesto).get("id").asText();

        JsonNode estimate = estimate(token, 1, lines(line(3, "servings", "pesto", null, pestoId)));

        assertThat(estimate.get("lines").toString()).isEqualTo("[\"COUNTED\"]");
        assertNutrition(estimate.get("nutrition"), 360, 4.5, 6, 36);
    }

    @Test
    void theEstimateSaysHowEachRecipeLineCounts() {
        String token = accessToken(createUser());
        String focaccia = create(token, focaccia(8)).get("id").asText();
        ObjectNode partial = recipe("Half-typed bread", 1, "MANUAL", lines(line(1, null, "bread", null, null)));
        partial.set("nutrition", objectMapper.createObjectNode().put("calories", 200));
        String typedBlanks = create(token, partial).get("id").asText();
        String nothingCounts = create(
                        token,
                        recipe(
                                "Cup bread",
                                1,
                                "INGREDIENTS",
                                lines(line(2, "cups", "flour", "all-purpose-flour", null))))
                .get("id")
                .asText();

        JsonNode estimate = estimate(
                token,
                2,
                lines(
                        line(2, "servings", "focaccia", null, focaccia),
                        line(2, " Serving ", "focaccia", null, focaccia),
                        line(100, "g", "focaccia", null, focaccia),
                        line(1, null, "focaccia", null, focaccia),
                        objectMapper.createObjectNode().put("name", "focaccia").put("recipeId", focaccia),
                        line(1, "servings", "flour", "all-purpose-flour", null),
                        line(1, "servings", "bread", null, typedBlanks),
                        line(1, "servings", "bread", null, nothingCounts)));

        assertThat(estimate.get("lines").toString())
                .isEqualTo("[\"COUNTED\",\"COUNTED\",\"UNIT_NOT_SUPPORTED\",\"UNIT_NOT_SUPPORTED\",\"UNMEASURED\","
                        + "\"UNIT_NOT_SUPPORTED\",\"NUTRITION_UNKNOWN\",\"NUTRITION_UNKNOWN\"]");
        // Four servings of focaccia over two.
        assertThat(estimate.get("nutrition").get("calories").asDouble()).isEqualTo(556);
    }

    @Test
    void deletingTheFocacciaLeavesTheSandwichLineUnlinked() {
        String token = accessToken(createUser());
        String focaccia = create(token, focaccia(8)).get("id").asText();
        String sandwich = create(token, sandwich(focaccia)).get("id").asText();

        assertThat(send(token, "DELETE", "/api/v1/recipes/" + focaccia, null).status())
                .isEqualTo(204);

        JsonNode reread = get(token, "/api/v1/recipes/" + sandwich);
        JsonNode line = reread.get("ingredients").get(0);
        assertThat(line.get("name").asText()).isEqualTo("focaccia");
        assertThat(line.has("recipeId")).isFalse();
        assertNutrition(reread.get("nutrition"), 22, 1.1, 4.8, 0.2);
        assertThat(estimate(token, 2, (ArrayNode) reread.get("ingredients"))
                        .get("lines")
                        .toString())
                .isEqualTo("[\"NOT_LINKED\",\"COUNTED\"]");
    }

    @Test
    void onlyYourOwnRecipesCanBeLinked() {
        String owner = accessToken(createUser());
        String other = accessToken(createUser());
        String focaccia = create(owner, focaccia(8)).get("id").asText();
        String library =
                get(owner, "/api/v1/public/recipes/shakshuka").get("id").asText();
        String nowhere = UUID.randomUUID().toString();

        for (String linked : new String[] {library, nowhere}) {
            Response response = send(owner, "POST", "/api/v1/recipes", sandwich(linked));
            assertThat(response.status()).as("linking %s", linked).isEqualTo(400);
            assertThat(response.body()).contains("you have no recipe with id " + linked);
        }
        Response stolen = send(other, "POST", "/api/v1/recipes", sandwich(focaccia));
        assertThat(stolen.status()).isEqualTo(400);
        assertThat(stolen.body()).contains("you have no recipe with id " + focaccia);
        ObjectNode estimate = objectMapper.createObjectNode().put("servings", 1);
        estimate.set("ingredients", lines(line(1, "servings", "focaccia", null, focaccia)));
        assertThat(send(other, "POST", "/api/v1/recipes/nutrition-estimate", estimate)
                        .status())
                .isEqualTo(400);
    }

    @Test
    void aLineLinksAtMostOneThing() {
        String token = accessToken(createUser());
        String focaccia = create(token, focaccia(8)).get("id").asText();
        ObjectNode both = sandwich(focaccia);
        ((ObjectNode) both.get("ingredients").get(0)).put("catalogSlug", "all-purpose-flour");

        Response response = send(token, "POST", "/api/v1/recipes", both);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).contains("links both a catalog entry and a recipe");
    }

    @Test
    void aRecipeCannotUseItselfDirectlyOrThroughAnother() {
        String token = accessToken(createUser());
        String focaccia = create(token, focaccia(8)).get("id").asText();
        create(token, sandwich(focaccia));

        ObjectNode selfish = focaccia(8);
        ((ArrayNode) selfish.get("ingredients")).add(line(1, "servings", "more focaccia", null, focaccia));
        Response self = send(token, "PUT", "/api/v1/recipes/" + focaccia, selfish);
        assertThat(self.status()).isEqualTo(400);
        assertThat(self.body()).contains("a recipe can't use itself as an ingredient");

        String sandwichId =
                get(token, "/api/v1/recipes?q=sandwich").get(0).get("id").asText();
        ObjectNode circular = focaccia(8);
        ((ArrayNode) circular.get("ingredients")).add(line(1, "servings", "sandwich", null, sandwichId));
        Response cycle = send(token, "PUT", "/api/v1/recipes/" + focaccia, circular);
        assertThat(cycle.status()).isEqualTo(400);
        assertThat(cycle.body()).contains("\\\"Focaccia sandwich\\\" uses this recipe, so this recipe can't use it");
    }

    @Test
    void readingACycleThatSlippedPastSavingStillAnswers() {
        String token = accessToken(createUser());
        String focaccia = create(token, focaccia(8)).get("id").asText();
        String sandwich = create(token, sandwich(focaccia)).get("id").asText();
        // Two saves at once could do this; saving one at a time refuses it.
        jdbc.update(
                "update recipe_ingredient set catalog_slug = null, linked_recipe_id = ?, unit = 'servings',"
                        + " quantity = 1 where recipe_id = ? and position = 0",
                UUID.fromString(sandwich),
                UUID.fromString(focaccia));

        JsonNode focacciaNow = get(token, "/api/v1/recipes/" + focaccia);
        JsonNode sandwichNow = get(token, "/api/v1/recipes/" + sandwich);

        assertThat(focacciaNow.get("ingredients").get(0).get("recipeId").asText())
                .isEqualTo(sandwich);
        assertThat(sandwichNow.get("nutrition").get("calories").isNumber()).isTrue();
        assertThat(get(token, "/api/v1/recipes")).hasSize(2);
    }

    @Test
    void anExportKeepsTheLinkWhenImportedIntoAnotherAccount() {
        String owner = accessToken(createUser());
        String focaccia = create(owner, focaccia(8)).get("id").asText();
        create(owner, sandwich(focaccia));
        JsonNode export = get(owner, "/api/v1/account/export");
        assertThat(export.get("version").asInt()).isEqualTo(ExportUpgrader.CURRENT_VERSION);
        // Newest first, so the sandwich comes before the recipe it uses.
        assertThat(export.get("recipes")
                        .get(0)
                        .get("ingredients")
                        .get(0)
                        .get("recipeId")
                        .asText())
                .isEqualTo(focaccia);

        String other = accessToken(createUser());
        ok(send(other, "POST", "/api/v1/account/import", export), 200);

        JsonNode recipes = get(other, "/api/v1/recipes");
        String importedFocaccia = recipes.get(1).get("id").asText();
        JsonNode sandwich =
                get(other, "/api/v1/recipes/" + recipes.get(0).get("id").asText());
        assertThat(sandwich.get("ingredients").get(0).get("recipeId").asText())
                .isEqualTo(importedFocaccia)
                .isNotEqualTo(focaccia);
        assertNutrition(sandwich.get("nutrition"), 300, 7.5, 52.5, 6.6);
    }

    @Test
    void anImportRefusesALinkOutsideTheDocumentOrACycleAndWritesNothing() {
        String owner = accessToken(createUser());
        String focaccia = create(owner, focaccia(8)).get("id").asText();
        create(owner, sandwich(focaccia));
        String other = accessToken(createUser());

        ObjectNode dangling = (ObjectNode) get(owner, "/api/v1/account/export");
        String nowhere = UUID.randomUUID().toString();
        ((ObjectNode) dangling.get("recipes").get(0).get("ingredients").get(0)).put("recipeId", nowhere);
        Response outside = send(other, "POST", "/api/v1/account/import", dangling);
        assertThat(outside.status()).isEqualTo(400);
        assertThat(outside.body())
                .contains("recipes[0].ingredients[0]: recipeId " + nowhere + " is not one of the export's recipes");

        ObjectNode circular = (ObjectNode) get(owner, "/api/v1/account/export");
        String sandwichId = circular.get("recipes").get(0).get("id").asText();
        ObjectNode flour =
                (ObjectNode) circular.get("recipes").get(1).get("ingredients").get(0);
        flour.remove("catalogSlug");
        flour.put("unit", "servings").put("recipeId", sandwichId);
        Response cycle = send(other, "POST", "/api/v1/account/import", circular);
        assertThat(cycle.status()).isEqualTo(400);
        assertThat(cycle.body()).contains("recipes[0]: \\\"Focaccia sandwich\\\" uses itself as an ingredient");

        assertThat(get(other, "/api/v1/recipes")).isEmpty();
    }

    private ObjectNode focaccia(int servings) {
        return recipe(
                "Focaccia",
                servings,
                "INGREDIENTS",
                lines(
                        line(500, "g", "flour", "all-purpose-flour", null),
                        line(50, "ml", "olive oil", "olive-oil", null),
                        line(1, "tsp", "salt", "salt", null)));
    }

    private ObjectNode sandwich(String focacciaId) {
        return recipe(
                "Focaccia sandwich",
                2,
                "INGREDIENTS",
                lines(line(2, "servings", "focaccia", null, focacciaId), line(2, null, "tomatoes", "tomato", null)));
    }

    private ObjectNode recipe(String title, int servings, String source, ArrayNode lines) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", title);
        input.put("description", "A recipe for the recipes-as-ingredients tests.");
        input.put("servings", servings);
        input.set("tags", objectMapper.createArrayNode());
        input.set("nutrition", objectMapper.createObjectNode());
        input.put("nutritionSource", source);
        input.set("ingredients", lines);
        input.set(
                "steps",
                objectMapper
                        .createArrayNode()
                        .add(objectMapper.createObjectNode().put("instruction", "Make it.")));
        return input;
    }

    private ArrayNode lines(JsonNode... lines) {
        ArrayNode array = objectMapper.createArrayNode();
        for (JsonNode line : lines) {
            array.add(line);
        }
        return array;
    }

    private ObjectNode line(
            double quantity,
            @Nullable String unit,
            String name,
            @Nullable String catalogSlug,
            @Nullable String recipeId) {
        ObjectNode node =
                objectMapper.createObjectNode().put("quantity", quantity).put("name", name);
        if (unit != null) {
            node.put("unit", unit);
        }
        if (catalogSlug != null) {
            node.put("catalogSlug", catalogSlug);
        }
        if (recipeId != null) {
            node.put("recipeId", recipeId);
        }
        return node;
    }

    private static void assertNutrition(
            JsonNode nutrition, double calories, double proteinG, double carbsG, double fatG) {
        assertThat(nutrition.get("calories").asDouble()).isEqualTo(calories);
        assertThat(nutrition.get("proteinG").asDouble()).isEqualTo(proteinG);
        assertThat(nutrition.get("carbsG").asDouble()).isEqualTo(carbsG);
        assertThat(nutrition.get("fatG").asDouble()).isEqualTo(fatG);
    }

    private JsonNode estimate(String token, int servings, ArrayNode lines) {
        ObjectNode input = objectMapper.createObjectNode().put("servings", servings);
        input.set("ingredients", lines);
        return ok(send(token, "POST", "/api/v1/recipes/nutrition-estimate", input), 200);
    }

    private JsonNode create(String token, JsonNode recipe) {
        return ok(send(token, "POST", "/api/v1/recipes", recipe), 201);
    }

    private void update(String token, String id, JsonNode recipe) {
        ok(send(token, "PUT", "/api/v1/recipes/" + id, recipe), 200);
    }

    private JsonNode get(String token, String uri) {
        return ok(send(token, "GET", uri, null), 200);
    }

    private JsonNode ok(Response response, int expectedStatus) {
        assertThat(response.status()).as(response.body()).isEqualTo(expectedStatus);
        return requireNonNull(objectMapper.readTree(response.body()));
    }

    private record Response(int status, String body) {}

    private Response send(String token, String method, String uri, @Nullable JsonNode body) {
        RestClient.RequestBodySpec request = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .method(HttpMethod.valueOf(method))
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.exchange((req, res) -> new Response(
                res.getStatusCode().value(), new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }
}
