package io.github.noahhhx.mimos.api.account;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestRecipes;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Account export and import end to end (ADR-0011): a user's data survives
 * a round trip into a fresh account, import is refused for an account that
 * has data, and a bad document writes nothing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountEndpointTests extends ApiIntegrationTestSupport {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void roundTripRestoresEverythingInAFreshAccount() {
        RestClient api = api();
        String source = accessToken(createUser());
        String soupId = TestRecipes.create(api, source, "Export Soup").get("id").asText();
        String toastId =
                TestRecipes.create(api, source, "Export Toast").get("id").asText();
        String shakshukaId =
                get(api, null, "/api/v1/public/recipes/shakshuka").get("id").asText();

        plan(api, source, MONDAY, "DINNER", soupId, 2);
        plan(api, source, MONDAY.plusDays(1), "BREAKFAST", shakshukaId, 1);
        plan(api, source, MONDAY.plusDays(2), "LUNCH", toastId, 1.5);
        JsonNode list = send(api, source, "POST", "/api/v1/plans/" + MONDAY + "/shopping-list", null);
        String firstItem = list.get("items").get(0).get("id").asText();
        send(
                api,
                source,
                "PATCH",
                "/api/v1/plans/" + MONDAY + "/shopping-list/items/" + firstItem,
                objectMapper.createObjectNode().put("checked", true));
        log(api, source, objectMapper.createObjectNode().put("recipeId", soupId).put("servings", 2), "DINNER", 0);
        log(api, source, objectMapper.createObjectNode().put("recipeId", shakshukaId), "BREAKFAST", 1);
        ObjectNode adHoc = objectMapper.createObjectNode().put("description", "Handful of almonds");
        adHoc.set("nutrition", objectMapper.createObjectNode().put("calories", 180));
        log(api, source, adHoc, "SNACK", 1);

        JsonNode exported = exportFrom(api, source);
        assertThat(exported.get("format").asText()).isEqualTo("mimos.export");
        assertThat(exported.get("version").asInt()).isEqualTo(ExportUpgrader.CURRENT_VERSION);
        assertThat(exported.get("recipes")).hasSize(2);
        JsonNode entries = exported.get("mealPlans").get(0).get("entries");
        assertThat(entries).hasSize(3);
        // Library recipes travel by slug, never by this instance's id.
        assertThat(entries.get(1).get("librarySlug").asText()).isEqualTo("shakshuka");
        assertThat(entries.get(1).has("recipeId")).isFalse();
        assertThat(exported.get("shoppingLists")
                        .get(0)
                        .get("items")
                        .get(0)
                        .get("checked")
                        .asBoolean())
                .isTrue();
        assertThat(exported.get("mealLogs")).hasSize(3);

        String target = accessToken(createUser());
        JsonNode report = importInto(api, target, exported, 200);
        assertThat(report.get("sourceVersion").asInt()).isEqualTo(ExportUpgrader.CURRENT_VERSION);
        assertThat(report.get("recipes").asInt()).isEqualTo(2);
        assertThat(report.get("plannedMeals").asInt()).isEqualTo(3);
        assertThat(report.get("shoppingLists").asInt()).isEqualTo(1);
        assertThat(report.get("mealLogs").asInt()).isEqualTo(3);
        assertThat(report.get("warnings")).isEmpty();

        JsonNode reexported = exportFrom(api, target);
        assertThat(comparable(reexported)).isEqualTo(comparable(exported));
        List<String> importedIds = new ArrayList<>();
        reexported
                .get("recipes")
                .forEach(recipe -> importedIds.add(recipe.get("id").asText()));
        assertThat(importedIds).doesNotContain(soupId, toastId);
    }

    @Test
    void refusesToImportIntoAnAccountWithData() {
        RestClient api = api();
        String token = accessToken(createUser());
        TestRecipes.create(api, token, "Already here");
        JsonNode exported = exportFrom(api, token);

        JsonNode problem = importInto(api, token, exported, 409);

        assertThat(problem.get("title").asText()).isEqualTo("Account not empty");
        assertThat(get(api, token, "/api/v1/recipes")).hasSize(1);
    }

    @Test
    void anEmptyPlanOrListDoesNotCountAsData() {
        RestClient api = api();
        String token = accessToken(createUser());
        // Viewing a week creates its (empty) plan; generating from it creates an empty list.
        get(api, token, "/api/v1/plans/" + MONDAY);
        send(api, token, "POST", "/api/v1/plans/" + MONDAY + "/shopping-list", null);

        JsonNode report = importInto(api, token, fixture(), 200);

        assertThat(report.get("shoppingLists").asInt()).isEqualTo(1);
        assertThat(get(api, token, "/api/v1/plans/" + MONDAY + "/shopping-list").get("items"))
                .hasSize(2);
    }

    @Test
    void anInvalidItemIsNamedAndNothingIsWritten() {
        RestClient api = api();
        String token = accessToken(createUser());
        ObjectNode document = fixture();
        ((ObjectNode) document.get("recipes").get(1)).put("servings", 0);

        JsonNode problem = importInto(api, token, document, 400);

        assertThat(problem.get("detail").asText()).startsWith("recipes[1]: servings must be between");
        assertThat(get(api, token, "/api/v1/recipes")).isEmpty();
        // The rolled-back attempt left the account empty, so a good import still works.
        importInto(api, token, fixture(), 200);
    }

    @Test
    void namesNestedFailuresAndMalformedValues() {
        RestClient api = api();
        String token = accessToken(createUser());

        ObjectNode unknownRecipe = fixture();
        ((ObjectNode) unknownRecipe.get("mealPlans").get(0).get("entries").get(0))
                .put("recipeId", "00000000-0000-0000-0000-000000000000");
        assertThat(importInto(api, token, unknownRecipe, 400).get("detail").asText())
                .isEqualTo("mealPlans[0].entries[0]: recipeId 00000000-0000-0000-0000-000000000000"
                        + " is not one of the export's recipes");

        ObjectNode wrongType = fixture();
        ((ObjectNode) wrongType.get("recipes").get(0)).put("servings", "lots");
        assertThat(importInto(api, token, wrongType, 400).get("detail").asText())
                .startsWith("The export is malformed at recipes[0].servings");

        ObjectNode missing = fixture();
        ((ObjectNode) missing.get("mealLogs").get(2)).remove("loggedAt");
        assertThat(importInto(api, token, missing, 400).get("detail").asText())
                .isEqualTo("mealLogs[2]: loggedAt is missing");
    }

    @Test
    void refusesNewerVersionsAndOtherDocuments() {
        RestClient api = api();
        String token = accessToken(createUser());

        ObjectNode newer = fixture().put("version", ExportUpgrader.CURRENT_VERSION + 1);
        assertThat(importInto(api, token, newer, 400).get("detail").asText()).contains("newer version of Mimos");

        ObjectNode other = objectMapper.createObjectNode().put("hello", "world");
        assertThat(importInto(api, token, other, 400).get("detail").asText()).contains("not a Mimos export");
    }

    @Test
    void aLibraryRecipeMissingHereSkipsItsPlannedMealsAndUnlinksItsLogs() {
        RestClient api = api();
        String token = accessToken(createUser());
        ObjectNode document = fixture();
        ((ObjectNode) document.get("mealPlans").get(0).get("entries").get(1)).put("librarySlug", "retired-recipe");
        ((ObjectNode) document.get("mealLogs").get(1)).put("librarySlug", "retired-recipe");

        JsonNode report = importInto(api, token, document, 200);

        assertThat(report.get("plannedMeals").asInt()).isEqualTo(1);
        assertThat(report.get("mealLogs").asInt()).isEqualTo(3);
        assertThat(report.get("warnings").get(0).asText())
                .isEqualTo("Library recipe \"retired-recipe\" is not in this instance's library:"
                        + " 1 planned meal skipped, 1 logged meal kept without the recipe link.");
        JsonNode logs = get(api, token, "/api/v1/logs?from=" + MONDAY.plusDays(1) + "&to=" + MONDAY.plusDays(1));
        JsonNode breakfast = logs.get(0);
        assertThat(breakfast.get("description").asText()).isEqualTo("Shakshuka");
        assertThat(breakfast.has("recipeId")).isFalse();
        assertThat(breakfast.get("nutrition").get("calories").asDouble()).isEqualTo(310);
    }

    /** The document minus what legitimately differs between accounts: export time and recipe ids. */
    private JsonNode comparable(JsonNode exported) {
        ObjectNode copy = (ObjectNode) exported.deepCopy();
        copy.remove("exportedAt");
        Map<String, String> ids = new HashMap<>();
        ArrayNode recipes = (ArrayNode) copy.get("recipes");
        for (int i = 0; i < recipes.size(); i++) {
            ObjectNode recipe = (ObjectNode) recipes.get(i);
            ids.put(recipe.get("id").asText(), "recipe-" + i);
            recipe.put("id", "recipe-" + i);
        }
        copy.get("mealPlans").forEach(plan -> plan.get("entries").forEach(entry -> relink(entry, ids)));
        copy.get("mealLogs").forEach(log -> relink(log, ids));
        return copy;
    }

    private static void relink(JsonNode node, Map<String, String> ids) {
        if (node.has("recipeId")) {
            ((ObjectNode) node).put("recipeId", ids.get(node.get("recipeId").asText()));
        }
    }

    private ObjectNode fixture() {
        try (InputStream in = new ClassPathResource("export/v1.json").getInputStream()) {
            return (ObjectNode) objectMapper.readTree(in);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JsonNode exportFrom(RestClient api, String token) {
        return api.get()
                .uri("/api/v1/account/export")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange((request, response) -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(200);
                    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                            .matches("attachment; filename=\"mimos-export-\\d{4}-\\d{2}-\\d{2}\\.json\"");
                    return requireNonNull(response.bodyTo(JsonNode.class), "export returned no body");
                });
    }

    private static JsonNode importInto(RestClient api, String token, JsonNode document, int expectedStatus) {
        return api.post()
                .uri("/api/v1/account/import")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(document)
                .exchange((request, response) -> {
                    JsonNode body = requireNonNull(response.bodyTo(JsonNode.class), "import returned no body");
                    assertThat(response.getStatusCode().value())
                            .as("import response %s", body)
                            .isEqualTo(expectedStatus);
                    return body;
                });
    }

    private void plan(RestClient api, String token, LocalDate date, String mealType, String recipeId, double servings) {
        send(
                api,
                token,
                "POST",
                "/api/v1/plans/" + MONDAY + "/entries",
                objectMapper
                        .createObjectNode()
                        .put("date", date.toString())
                        .put("mealType", mealType)
                        .put("recipeId", recipeId)
                        .put("servings", servings));
    }

    private void log(RestClient api, String token, ObjectNode input, String mealType, int dayOfWeek) {
        send(
                api,
                token,
                "POST",
                "/api/v1/logs",
                input.put("date", MONDAY.plusDays(dayOfWeek).toString()).put("mealType", mealType));
    }

    private static JsonNode get(RestClient api, @Nullable String token, String uri) {
        return requireNonNull(
                api.get()
                        .uri(uri)
                        .headers(headers -> {
                            if (token != null) {
                                headers.setBearerAuth(token);
                            }
                        })
                        .retrieve()
                        .body(JsonNode.class),
                "GET " + uri + " returned no body");
    }

    private static JsonNode send(RestClient api, String token, String method, String uri, @Nullable JsonNode body) {
        RestClient.RequestBodySpec request =
                api.method(HttpMethod.valueOf(method)).uri(uri).headers(headers -> headers.setBearerAuth(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return requireNonNull(request.retrieve().body(JsonNode.class), method + " " + uri + " returned no body");
    }
}
