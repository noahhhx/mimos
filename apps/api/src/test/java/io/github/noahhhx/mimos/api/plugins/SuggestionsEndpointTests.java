package io.github.noahhhx.mimos.api.plugins;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestRecipes;
import java.io.IOException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Suggestions end to end over a real stub plugin (ADR-0006): fan-out,
 * validation, attribution, and the privacy rule — personal recipes
 * contribute their slot shape only, never their identity. A second,
 * unreachable registration proves failure containment. Applying a card
 * reuses the existing plan-entry endpoint — there is no other write path.
 * The shared `test` user turns the stub on first (ADR-0013: plugins are
 * opt-in); {@link PluginSettingsEndpointTests} covers the opt-in itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuggestionsEndpointTests extends ApiIntegrationTestSupport {

    // Unique weeks per test: the Testcontainers Postgres is shared across
    // test classes, so plan state must not collide.
    private static final LocalDate WEEK_A = LocalDate.of(2026, 4, 6); // a Monday
    private static final LocalDate WEEK_B = LocalDate.of(2026, 5, 4); // a Monday
    private static final LocalDate WEEK_C = LocalDate.of(2026, 6, 1); // a Monday

    private static final StubPlugin stub;

    static {
        try {
            stub = StubPlugin.start();
        } catch (IOException exception) {
            throw new IllegalStateException("stub plugin failed to start", exception);
        }
    }

    @DynamicPropertySource
    static void pluginRegistrations(DynamicPropertyRegistry registry) {
        registry.add("mimos.plugins[0].id", () -> "stub-plugin");
        registry.add("mimos.plugins[0].url", stub::url);
        registry.add("mimos.plugins[0].timeout", () -> "500ms");
        // Registered but unreachable: connection refused, contained.
        registry.add("mimos.plugins[1].url", () -> "http://localhost:1");
        registry.add("mimos.plugins[1].timeout", () -> "500ms");
    }

    @AfterAll
    static void closeStub() {
        stub.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void resetStubAndOptIn() {
        stub.suggestionsJson = "{\"suggestions\":[]}";
        api().put()
                .uri("/api/v1/me/plugins/{id}", "stub-plugin")
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body("{\"enabled\":true}")
                .retrieve()
                .toBodilessEntity();
    }

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void cardsAreFannedOutValidatedAndAttributed() {
        RestClient api = api();
        String token = accessToken();
        JsonNode library = libraryRecipe(api, token);

        stub.suggestionsJson = suggestionsPayload(library, WEEK_A);

        JsonNode body = requireNonNull(
                api.get()
                        .uri("/api/v1/plans/{start}/suggestions", WEEK_A.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "suggestions returned no body");

        // One validated card from the stub plugin; the unreachable
        // registration contributed nothing without failing the request.
        assertThat(body.get("suggestions")).hasSize(1);
        JsonNode card = body.get("suggestions").get(0);
        assertThat(card.get("pluginId").asText()).isEqualTo("stub-plugin");
        assertThat(card.get("pluginName").asText()).isEqualTo("Stub Plugin");
        assertThat(card.get("title").asText()).isEqualTo("Stub week");
        // Of four proposed entries only the valid one survived.
        assertThat(card.get("entries")).hasSize(1);
        JsonNode entry = card.get("entries").get(0);
        assertThat(entry.get("recipeId").asText()).isEqualTo(library.get("id").asText());
        // Titles are hydrated from core's own data, not the plugin.
        assertThat(entry.get("recipeTitle").asText())
                .isEqualTo(library.get("title").asText());
        assertThat(entry.get("date").asText()).isEqualTo(WEEK_A.plusDays(2).toString());
        assertThat(entry.get("mealType").asText()).isEqualTo("DINNER");
        assertThat(entry.get("servings").asDouble()).isEqualTo(2.0);
    }

    @Test
    void personalRecipesContributeTheirShapeOnly() {
        RestClient api = api();
        String token = accessToken();
        JsonNode personal = TestRecipes.create(api, token, "Private Supper");
        JsonNode library = libraryRecipe(api, token);
        plan(api, token, WEEK_B, WEEK_B, personal.get("id").asText(), "DINNER");
        plan(api, token, WEEK_B, WEEK_B.plusDays(1), library.get("id").asText(), "LUNCH");

        requireNonNull(
                api.get()
                        .uri("/api/v1/plans/{start}/suggestions", WEEK_B.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "suggestions returned no body");
        String captured = requireNonNull(stub.lastContext(), "the stub saw no context");
        JsonNode context = requireNonNull(objectMapper.readTree(captured));

        assertThat(context.get("weekStartDate").asText()).isEqualTo(WEEK_B.toString());
        assertThat(context.get("plannedSlots")).hasSize(2);
        JsonNode personalSlot = context.get("plannedSlots").get(0);
        assertThat(personalSlot.has("recipeId"))
                .as("personal recipes never leak their identity to plugins")
                .isFalse();
        assertThat(personalSlot.get("mealType").asText()).isEqualTo("DINNER");
        JsonNode librarySlot = context.get("plannedSlots").get(1);
        assertThat(librarySlot.get("recipeId").asText())
                .isEqualTo(library.get("id").asText());
        // The catalog is the library — the personal recipe is absent.
        JsonNode catalog = context.get("libraryRecipes");
        assertThat(catalog).isNotEmpty();
        assertThat(catalog.toString()).doesNotContain("Private Supper");
        assertThat(catalog.toString()).contains(library.get("title").asText());
    }

    @Test
    void applyingASuggestionReusesThePlanEntryEndpoint() {
        RestClient api = api();
        String token = accessToken();
        JsonNode library = libraryRecipe(api, token);
        stub.suggestionsJson = suggestionsPayload(library, WEEK_C);

        JsonNode body = requireNonNull(
                api.get()
                        .uri("/api/v1/plans/{start}/suggestions", WEEK_C.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "suggestions returned no body");
        JsonNode entry = body.get("suggestions").get(0).get("entries").get(0);

        ObjectNode input = objectMapper.createObjectNode();
        input.put("date", entry.get("date").asText());
        input.put("mealType", entry.get("mealType").asText());
        input.put("recipeId", entry.get("recipeId").asText());
        input.put("servings", entry.get("servings").asDouble());
        JsonNode planned = requireNonNull(
                api.post()
                        .uri("/api/v1/plans/{start}/entries", WEEK_C.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(input)
                        .retrieve()
                        .body(JsonNode.class),
                "entry creation returned no body");
        assertThat(planned.get("recipeTitle").asText())
                .isEqualTo(library.get("title").asText());

        JsonNode plan = requireNonNull(
                api.get()
                        .uri("/api/v1/plans/{start}", WEEK_C.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "plan returned no body");
        assertThat(plan.get("entries")).hasSize(1);
    }

    private JsonNode libraryRecipe(RestClient api, String token) {
        JsonNode library = requireNonNull(
                api.get()
                        .uri("/api/v1/recipes/library")
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "library returned no body");
        return requireNonNull(library.get(0), "the library seed is empty");
    }

    private void plan(RestClient api, String token, LocalDate week, LocalDate date, String recipeId, String mealType) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("date", date.toString());
        input.put("mealType", mealType);
        input.put("recipeId", recipeId);
        input.put("servings", 2);
        api.post()
                .uri("/api/v1/plans/{start}/entries", week.toString())
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(input)
                .retrieve()
                .toBodilessEntity();
    }

    /** A stub response: one good card (one valid, three invalid entries), one over-long title, one empty card. */
    private String suggestionsPayload(JsonNode library, LocalDate week) {
        String libraryId = library.get("id").asText();
        ObjectNode good = objectMapper.createObjectNode();
        good.put("title", "Stub week");
        good.put("blurb", "A valid card from the stub.");
        good.put("icon", "ST");
        ArrayNode entries = good.putArray("entries");
        entries.addObject()
                .put("date", week.plusDays(2).toString())
                .put("mealType", "DINNER")
                .put("recipeId", libraryId)
                .put("servings", 2);
        entries.addObject()
                .put("date", week.plusDays(2).toString())
                .put("mealType", "DINNER")
                .put("recipeId", java.util.UUID.randomUUID().toString())
                .put("servings", 2);
        entries.addObject()
                .put("date", week.plusWeeks(4).toString())
                .put("mealType", "DINNER")
                .put("recipeId", libraryId)
                .put("servings", 2);
        entries.addObject()
                .put("date", week.plusDays(2).toString())
                .put("mealType", "BRUNCH")
                .put("recipeId", libraryId)
                .put("servings", 0);
        ObjectNode tooLongTitle = objectMapper.createObjectNode();
        tooLongTitle.put("title", "x".repeat(81));
        tooLongTitle.putArray("entries");
        ObjectNode emptyEntries = objectMapper.createObjectNode();
        emptyEntries.put("title", "Nothing to suggest");
        emptyEntries.putArray("entries");
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("suggestions").add(good).add(tooLongTitle).add(emptyEntries);
        return root.toString();
    }
}
