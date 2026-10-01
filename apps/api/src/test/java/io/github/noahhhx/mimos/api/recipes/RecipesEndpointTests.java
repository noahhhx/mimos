package io.github.noahhhx.mimos.api.recipes;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * End-to-end recipe CRUD against real Postgres and Keycloak: personal
 * recipes are private to their author, curated library recipes are publicly
 * readable and read-only, and validation failures surface as
 * problem-details.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipesEndpointTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void personalRecipeLifecycle() {
        RestClient api = api();
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", "Test Pancakes");
        input.put("description", "Fluffy weekend pancakes.");
        input.put("servings", 4);
        input.put("prepMinutes", 10);
        input.put("cookMinutes", 15);
        input.set("tags", objectMapper.createArrayNode().add("breakfast").add("vegetarian"));
        input.set("nutrition", nutritionNode(320, 9, 48, 9));
        input.set(
                "ingredients",
                objectMapper
                        .createArrayNode()
                        .add(ingredient(2, "cups", "flour"))
                        .add(ingredient(2, null, "eggs")));
        input.set(
                "steps",
                objectMapper
                        .createArrayNode()
                        .add(step("Whisk the dry ingredients."))
                        .add(step("Fold in the eggs.")));

        JsonNode created = requireNonNull(
                api.post()
                        .uri("/api/v1/recipes")
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(input)
                        .retrieve()
                        .body(JsonNode.class),
                "create returned no body");
        UUID id = UUID.fromString(created.get("id").asText());
        assertThat(created.get("title").asText()).isEqualTo("Test Pancakes");
        assertThat(created.get("isLibrary").asBoolean()).isFalse();
        assertThat(created.get("ingredients")).hasSize(2);
        assertThat(created.get("steps")).hasSize(2);
        assertThat(created.get("nutrition").get("calories").asDouble()).isEqualTo(320);

        // Visible to its author, searchable.
        JsonNode mine = requireNonNull(
                api.get()
                        .uri("/api/v1/recipes")
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .retrieve()
                        .body(JsonNode.class),
                "list returned no body");
        assertThat(titles(mine)).contains("Test Pancakes");

        JsonNode search = requireNonNull(
                api.get()
                        .uri(builder -> builder.path("/api/v1/recipes")
                                .queryParam("q", "pancakes")
                                .build())
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .retrieve()
                        .body(JsonNode.class),
                "search returned no body");
        assertThat(titles(search)).contains("Test Pancakes");

        JsonNode foreign = requireNonNull(
                api.get()
                        .uri("/api/v1/recipes")
                        .headers(headers -> headers.setBearerAuth(accessToken("test2")))
                        .retrieve()
                        .body(JsonNode.class),
                "foreign list returned no body");
        assertThat(titles(foreign)).doesNotContain("Test Pancakes");

        // Replace: new title survives, children are replaced.
        input.put("title", "Better Test Pancakes");
        input.withArray("steps").add(step("Serve with syrup."));
        JsonNode replaced = requireNonNull(
                api.put()
                        .uri("/api/v1/recipes/{id}", id)
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(input)
                        .retrieve()
                        .body(JsonNode.class),
                "replace returned no body");
        assertThat(replaced.get("title").asText()).isEqualTo("Better Test Pancakes");
        assertThat(replaced.get("steps")).hasSize(3);

        // Another user cannot see, replace, or delete it (404, indistinguishable).
        assertThat(statusOfGet(api, id, accessToken("test2"))).isEqualTo(404);
        api.put()
                .uri("/api/v1/recipes/{id}", id)
                .headers(headers -> headers.setBearerAuth(accessToken("test2")))
                .contentType(MediaType.APPLICATION_JSON)
                .body(input)
                .exchange((req, res) -> assertThat(res.getStatusCode().value()).isEqualTo(404));
        api.delete()
                .uri("/api/v1/recipes/{id}", id)
                .headers(headers -> headers.setBearerAuth(accessToken("test2")))
                .exchange((req, res) -> assertThat(res.getStatusCode().value()).isEqualTo(404));

        // Owner deletes it.
        api.delete()
                .uri("/api/v1/recipes/{id}", id)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .exchange((req, res) -> assertThat(res.getStatusCode().value()).isEqualTo(204));
        assertThat(statusOfGet(api, id, accessToken())).isEqualTo(404);
    }

    @Test
    void invalidRecipeIsRejectedWithProblemDetails() {
        RestClient api = api();
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", "");
        input.put("description", "");
        input.put("servings", 0);
        input.set("tags", objectMapper.createArrayNode());
        input.set("nutrition", objectMapper.createObjectNode());
        input.set("ingredients", objectMapper.createArrayNode());
        input.set("steps", objectMapper.createArrayNode());

        api.post()
                .uri("/api/v1/recipes")
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(input)
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(400);
                    assertThat(res.getHeaders().getContentType())
                            .isNotNull()
                            .matches(ct -> ct.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
                    JsonNode problem = objectMapper.readTree(res.getBody().readAllBytes());
                    assertThat(problem.get("status").asInt()).isEqualTo(400);
                    assertThat(problem.get("title").asText()).isEqualTo("Invalid request");
                    return null;
                });
    }

    @Test
    void repeatedTagsAreStoredOnce() {
        RestClient api = api();
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", "Twice-Tagged Soup");
        input.put("description", "Tagged dinner twice.");
        input.put("servings", 2);
        input.set(
                "tags", objectMapper.createArrayNode().add("dinner").add("soup").add("dinner"));
        input.set("nutrition", objectMapper.createObjectNode());
        input.set("ingredients", objectMapper.createArrayNode().add(ingredient(1, "l", "stock")));
        input.set("steps", objectMapper.createArrayNode().add(step("Simmer.")));

        JsonNode created = requireNonNull(
                api.post()
                        .uri("/api/v1/recipes")
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(input)
                        .retrieve()
                        .body(JsonNode.class),
                "create returned no body");
        assertThat(created.get("tags").valueStream().map(JsonNode::asText)).containsExactly("dinner", "soup");

        JsonNode replaced = requireNonNull(
                api.put()
                        .uri("/api/v1/recipes/{id}", created.get("id").asText())
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(input)
                        .retrieve()
                        .body(JsonNode.class),
                "replace returned no body");
        assertThat(replaced.get("tags").valueStream().map(JsonNode::asText)).containsExactly("dinner", "soup");
    }

    @Test
    void unmeasuredIngredientRoundTripsWithoutAQuantity() {
        RestClient api = api();
        ObjectNode input = minimalRecipe("Seasoned Stock");
        input.set(
                "ingredients",
                objectMapper
                        .createArrayNode()
                        .add(ingredient(1, "l", "stock"))
                        .add(objectMapper.createObjectNode().put("name", "salt, to taste")));

        JsonNode created = requireNonNull(
                api.post()
                        .uri("/api/v1/recipes")
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(input)
                        .retrieve()
                        .body(JsonNode.class),
                "create returned no body");
        JsonNode fetched = requireNonNull(
                api.get()
                        .uri("/api/v1/recipes/{id}", created.get("id").asText())
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .retrieve()
                        .body(JsonNode.class),
                "get returned no body");
        JsonNode salt = fetched.get("ingredients").get(1);
        assertThat(salt.get("name").asText()).isEqualTo("salt, to taste");
        assertThat(salt.has("quantity")).isFalse();
        assertThat(fetched.get("ingredients").get(0).get("quantity").asDouble()).isEqualTo(1);
    }

    @Test
    void unauthenticatedAccessIsRejected() {
        api().get().uri("/api/v1/recipes").exchange((req, res) -> {
            assertThat(res.getStatusCode().value()).isEqualTo(401);
            assertThat(res.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
            return null;
        });
    }

    @Test
    void libraryRecipesArePubliclyReadableAndReadOnly() {
        RestClient api = api();
        UUID libraryId = insertLibraryRecipe();

        // Public list and slug detail need no token at all.
        JsonNode listed = requireNonNull(
                api.get()
                        .uri(builder -> builder.path("/api/v1/public/recipes")
                                .queryParam("q", "zz library")
                                .build())
                        .retrieve()
                        .body(JsonNode.class),
                "public list returned no body");
        assertThat(titles(listed)).contains("ZZ Library Test Recipe");

        JsonNode bySlug = requireNonNull(
                api.get()
                        .uri("/api/v1/public/recipes/zz-library-test-recipe")
                        .retrieve()
                        .body(JsonNode.class),
                "public detail returned no body");
        assertThat(bySlug.get("id").asText()).isEqualTo(libraryId.toString());
        assertThat(bySlug.get("isLibrary").asBoolean()).isTrue();

        // Visible to authenticated users too.
        assertThat(statusOfGet(api, libraryId, accessToken())).isEqualTo(200);

        // But never writable.
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", "Hijacked");
        input.put("description", "");
        input.put("servings", 1);
        input.set("tags", objectMapper.createArrayNode());
        input.set("nutrition", objectMapper.createObjectNode());
        input.set("ingredients", objectMapper.createArrayNode().add(ingredient(1, null, "anything")));
        input.set("steps", objectMapper.createArrayNode().add(step("Do nothing.")));
        api.put()
                .uri("/api/v1/recipes/{id}", libraryId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(input)
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(403);
                    JsonNode problem = objectMapper.readTree(res.getBody().readAllBytes());
                    assertThat(problem.get("title").asText()).isEqualTo("Read-only");
                    return null;
                });
        api.delete()
                .uri("/api/v1/recipes/{id}", libraryId)
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .exchange((req, res) -> assertThat(res.getStatusCode().value()).isEqualTo(403));

        // Personal recipes never appear on the public surface.
        JsonNode mine = requireNonNull(
                api.get().uri("/api/v1/public/recipes").retrieve().body(JsonNode.class),
                "public list returned no body");
        assertThat(titles(mine)).doesNotContain("Test Pancakes");
    }

    private UUID insertLibraryRecipe() {
        jdbc.update("""
                insert into recipe (owner_profile_id, slug, title, description, servings,
                                    calories, protein_g, carbs_g, fat_g)
                values (null, 'zz-library-test-recipe', 'ZZ Library Test Recipe',
                        'A seeded-looking library recipe.', 2, 100, 5, 10, 3)
                on conflict (slug) do nothing
                """);
        // The recipe row may already exist from a previous run; look it up either way.
        return requireNonNull(
                jdbc.queryForObject("select id from recipe where slug = 'zz-library-test-recipe'", UUID.class),
                "library recipe must exist");
    }

    private int statusOfGet(RestClient api, UUID recipeId, String token) {
        return api.get()
                .uri("/api/v1/recipes/{id}", recipeId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange((req, res) -> res.getStatusCode().value());
    }

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private static List<String> titles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        for (JsonNode recipe : requireNonNull((ArrayNode) page, "expected an array")) {
            titles.add(recipe.get("title").asText());
        }
        return titles;
    }

    private ObjectNode nutritionNode(int calories, int protein, int carbs, int fat) {
        return objectMapper
                .createObjectNode()
                .put("calories", calories)
                .put("proteinG", protein)
                .put("carbsG", carbs)
                .put("fatG", fat);
    }

    /** A valid recipe with one ingredient and one step, ready to vary. */
    private ObjectNode minimalRecipe(String title) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", title);
        input.put("description", "A test recipe.");
        input.put("servings", 2);
        input.set("tags", objectMapper.createArrayNode());
        input.set("nutrition", objectMapper.createObjectNode());
        input.set("ingredients", objectMapper.createArrayNode().add(ingredient(1, "l", "stock")));
        input.set("steps", objectMapper.createArrayNode().add(step("Simmer.")));
        return input;
    }

    private ObjectNode ingredient(double quantity, @Nullable String unit, String name) {
        ObjectNode node =
                objectMapper.createObjectNode().put("quantity", quantity).put("name", name);
        if (unit != null) {
            node.put("unit", unit);
        }
        return node;
    }

    private ObjectNode step(String instruction) {
        return objectMapper.createObjectNode().put("instruction", instruction);
    }
}
