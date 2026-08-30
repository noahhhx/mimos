package io.github.noahhhx.mimos.api.support;

import static java.util.Objects.requireNonNull;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds recipe payloads through the real API for integration tests. Every
 * recipe here is a *personal* recipe of the user whose token is passed;
 * library recipes come from the seed (see LibrarySeeder).
 */
public final class TestRecipes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestRecipes() {}

    /** Creates a personal recipe and returns the created body (with id). */
    public static JsonNode create(RestClient api, String token, String title) {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("title", title);
        input.put("description", "A test recipe for " + title + ".");
        input.put("servings", 2);
        input.put("prepMinutes", 5);
        input.put("cookMinutes", 10);
        input.set("tags", MAPPER.createArrayNode().add("test"));
        input.set(
                "nutrition",
                MAPPER.createObjectNode()
                        .put("calories", 400)
                        .put("proteinG", 20)
                        .put("carbsG", 40)
                        .put("fatG", 10));
        input.set("ingredients", ingredients(input));
        input.set(
                "steps", MAPPER.createArrayNode().add(MAPPER.createObjectNode().put("instruction", "Cook it.")));
        return requireNonNull(
                api.post()
                        .uri("/api/v1/recipes")
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(input)
                        .retrieve()
                        .body(JsonNode.class),
                "recipe creation returned no body");
    }

    private static ArrayNode ingredients(ObjectNode input) {
        // Two ingredient lines with distinct names and units.
        return MAPPER.createArrayNode()
                .add(MAPPER.createObjectNode()
                        .put("quantity", 2)
                        .put("unit", "cups")
                        .put("name", "Test Flour"))
                .add(MAPPER.createObjectNode()
                        .put("quantity", 1)
                        .put("unit", "tbsp")
                        .put("name", "Test Olive Oil"));
    }
}
