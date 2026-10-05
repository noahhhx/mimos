package io.github.noahhhx.mimos.api.account;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Old exports keep importing (ADR-0011). {@code export/v<N>.json} is a
 * frozen sample of every format version ever released; each one must
 * import into a fresh account on today's schema and rules. Never edit a
 * fixture: when the format changes, add the next one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExportFixturesTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void everyVersionHasAFrozenFixture() {
        for (int version = 1; version <= ExportUpgrader.CURRENT_VERSION; version++) {
            assertThat(new ClassPathResource(fixturePath(version)).exists())
                    .as("format version %d needs a frozen fixture at %s", version, fixturePath(version))
                    .isTrue();
        }
    }

    @Test
    void everyFrozenFixtureImportsCleanly() {
        RestClient api = api();
        for (int version = 1; version <= ExportUpgrader.CURRENT_VERSION; version++) {
            String token = accessToken(createUser());
            JsonNode report = importFixture(api, token, version);
            assertThat(report.get("sourceVersion").asInt()).isEqualTo(version);
            assertThat(report.get("warnings")).as("v%d warnings", version).isEmpty();
            JsonNode reexported = get(api, token, "/api/v1/account/export");
            assertThat(reexported.get("version").asInt()).isEqualTo(ExportUpgrader.CURRENT_VERSION);
        }
    }

    @Test
    void version1RestoresItsContent() {
        RestClient api = api();
        String token = accessToken(createUser());

        JsonNode report = importFixture(api, token, 1);

        assertThat(report.get("recipes").asInt()).isEqualTo(2);
        assertThat(report.get("plannedMeals").asInt()).isEqualTo(2);
        assertThat(report.get("shoppingLists").asInt()).isEqualTo(1);
        assertThat(report.get("mealLogs").asInt()).isEqualTo(3);

        JsonNode recipes = get(api, token, "/api/v1/recipes");
        assertThat(titles(recipes)).containsExactly("Fixture Toast", "Fixture Lentil Soup");
        String soupId = recipes.get(1).get("id").asText();
        JsonNode soup = get(api, token, "/api/v1/recipes/" + soupId);
        assertThat(soup.get("ingredients")).hasSize(3);
        assertThat(soup.get("ingredients").get(2).has("quantity")).isFalse(); // salt, unmeasured
        assertThat(soup.get("tags").toString()).isEqualTo("[\"soup\",\"vegan\"]");
        assertThat(soup.get("nutritionSource").asText()).isEqualTo("MANUAL");
        assertThat(soup.get("nutrition").get("calories").asDouble()).isEqualTo(320);

        JsonNode plan = get(api, token, "/api/v1/plans/2026-09-28");
        assertThat(plan.get("entries")).hasSize(2);
        assertThat(plan.get("entries").get(0).get("recipeId").asText()).isEqualTo(soupId);
        assertThat(plan.get("entries").get(1).get("recipeTitle").asText()).isEqualTo("Shakshuka");
        assertThat(plan.get("entries").get(1).get("servings").asDouble()).isEqualTo(1.5);

        JsonNode list = get(api, token, "/api/v1/plans/2026-09-28/shopping-list");
        assertThat(list.get("generatedAt").asText()).isEqualTo("2026-09-27T16:00:00Z");
        assertThat(list.get("items").get(0).get("checked").asBoolean()).isTrue();
        assertThat(list.get("items").get(1).has("quantity")).isFalse();

        JsonNode logs = get(api, token, "/api/v1/logs?from=2026-09-28&to=2026-09-29");
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0).get("recipeId").asText()).isEqualTo(soupId);
        // Logged nutrition is history: kept as exported, not recomputed from today's recipe.
        assertThat(logs.get(0).get("nutrition").get("calories").asDouble()).isEqualTo(640);
        assertThat(logs.get(1).get("recipeId").asText())
                .isEqualTo(get(api, null, "/api/v1/public/recipes/shakshuka")
                        .get("id")
                        .asText());
        assertThat(logs.get(2).has("recipeId")).isFalse();

        JsonNode exported = get(api, token, "/api/v1/account/export");
        JsonNode exportedSoup = exported.get("recipes").get(1);
        assertThat(exportedSoup.get("createdAt").asText()).isEqualTo("2026-09-01T18:30:00Z");
        assertThat(exportedSoup.get("updatedAt").asText()).isEqualTo("2026-09-05T12:00:00Z");
        assertThat(exported.get("mealLogs").get(0).get("loggedAt").asText()).isEqualTo("2026-09-28T19:45:00Z");
    }

    @Test
    void version2RestoresCalculatedNutrition() {
        RestClient api = api();
        String token = accessToken(createUser());

        importFixture(api, token, 2);

        JsonNode recipes = get(api, token, "/api/v1/recipes");
        JsonNode soup =
                get(api, token, "/api/v1/recipes/" + recipes.get(1).get("id").asText());
        assertThat(soup.get("nutritionSource").asText()).isEqualTo("INGREDIENTS");
        assertThat(soup.get("ingredients").get(0).get("catalogSlug").asText()).isEqualTo("red-lentils");
        assertThat(soup.get("ingredients").get(1).get("note").asText()).isEqualTo("diced");
        // The export's own ingredient comes back under a new slug, and the line follows it.
        JsonNode stock = soup.get("ingredients").get(2);
        assertThat(stock.get("catalogSlug").asText())
                .startsWith("homemade-stock-")
                .isNotEqualTo("homemade-stock-a1b2c3");
        JsonNode own = get(api, token, "/api/v1/ingredients?q=homemade");
        assertThat(own).hasSize(1);
        assertThat(own.get(0).get("isShared").asBoolean()).isFalse();
        assertThat(own.get(0).get("slug").asText())
                .isEqualTo(stock.get("catalogSlug").asText());
        // 250 g red lentils, 2 carrots, and 1 l of the stock (8 kcal per 100 ml) over 4 servings.
        assertThat(soup.get("nutrition").get("calories").asDouble()).isEqualTo(256);
        JsonNode toast =
                get(api, token, "/api/v1/recipes/" + recipes.get(0).get("id").asText());
        assertThat(toast.get("nutritionSource").asText()).isEqualTo("MANUAL");
        assertThat(toast.get("nutrition").get("calories").asDouble()).isEqualTo(150);
    }

    private JsonNode importFixture(RestClient api, String token, int version) {
        JsonNode document;
        try (InputStream in = new ClassPathResource(fixturePath(version)).getInputStream()) {
            document = objectMapper.readTree(in);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return requireNonNull(
                api.post()
                        .uri("/api/v1/account/import")
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(document)
                        .retrieve()
                        .body(JsonNode.class),
                "import returned no body");
    }

    private static String fixturePath(int version) {
        return "export/v" + version + ".json";
    }

    private static List<String> titles(JsonNode recipes) {
        List<String> titles = new ArrayList<>();
        recipes.forEach(recipe -> titles.add(recipe.get("title").asText()));
        return titles;
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
}
