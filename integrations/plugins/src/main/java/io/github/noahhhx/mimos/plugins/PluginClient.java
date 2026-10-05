package io.github.noahhhx.mimos.plugins;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The outbound extension-API client for one registration (ADR-0006).
 * Requests are built through the tree model — the plugin-facing wire
 * format is owned here, translated at the edge — and responses are read
 * defensively: untrusted plugin JSON is never data-bound.
 */
final class PluginClient {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final PluginRegistration registration;
    private final RestClient restClient;

    PluginClient(PluginRegistration registration) {
        this.registration = registration;
        // Per-plugin timeout (default 2s): a slow plugin contributes
        // nothing rather than stalling the request (ADR-0006).
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(registration.timeout()).build();
        org.springframework.http.client.JdkClientHttpRequestFactory requestFactory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(registration.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(registration.url())
                .requestFactory(requestFactory)
                .build();
    }

    PluginRegistration registration() {
        return registration;
    }

    /** Fetches and validates {@code GET /manifest}. */
    PluginManifest fetchManifest() {
        JsonNode body = restClient
                .get()
                .uri("/manifest")
                .headers(headers -> authorize(headers))
                .retrieve()
                .body(JsonNode.class);
        if (body == null || !body.isObject()) {
            throw new PluginProtocolException("manifest at " + registration.url() + " is not a JSON object");
        }
        return PluginManifest.fromJson(body);
    }

    /** Calls {@code POST /v1/plan-suggestions} with the context snapshot. */
    List<PluginCard> fetchSuggestions(SuggestionContext context) {
        JsonNode body = restClient
                .post()
                .uri("/v1/plan-suggestions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> authorize(headers))
                .body(toJson(context))
                .retrieve()
                .body(JsonNode.class);
        if (body == null || !body.isObject()) {
            throw new PluginProtocolException(
                    "suggestions response from " + registration.url() + " is not a JSON object");
        }
        JsonNode suggestions = body.get("suggestions");
        if (suggestions == null) {
            throw new PluginProtocolException("suggestions response is missing the 'suggestions' array");
        }
        if (!suggestions.isArray()) {
            throw new PluginProtocolException("'suggestions' must be an array");
        }
        return parseCards(suggestions);
    }

    /**
     * Calls {@code POST /v1/week-panel} for one user's week (ADR-0017): a
     * render without {@code action}, an action and then a render with one.
     */
    WeekPanel fetchWeekPanel(
            PluginManifest manifest, UUID subject, LocalDate weekStartDate, @Nullable PanelAction action) {
        ObjectNode request = MAPPER.createObjectNode();
        request.put("subject", subject.toString());
        request.put("weekStartDate", weekStartDate.toString());
        if (action != null) {
            ObjectNode actionNode = request.putObject("action");
            actionNode.put("id", action.id());
            if (action.value() != null) {
                actionNode.put("value", action.value());
            }
        }
        JsonNode body = restClient
                .post()
                .uri("/v1/week-panel")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> authorize(headers))
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        return WeekPanelParser.parse(manifest, body);
    }

    private void authorize(org.springframework.http.HttpHeaders headers) {
        String secret = registration.sharedSecret();
        if (secret != null && !secret.isBlank()) {
            headers.setBearerAuth(secret);
        }
    }

    private static ObjectNode toJson(SuggestionContext context) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("subject", context.subject().toString());
        root.put("weekStartDate", context.weekStartDate().toString());
        ArrayNode slots = root.putArray("plannedSlots");
        for (PlannedSlot slot : context.plannedSlots()) {
            ObjectNode slotNode = slots.addObject();
            slotNode.put("date", slot.date().toString());
            slotNode.put("mealType", slot.mealType().name());
            slotNode.put("servings", slot.servings());
            if (slot.recipeId() != null) {
                slotNode.put("recipeId", slot.recipeId().toString());
            }
        }
        ArrayNode recipes = root.putArray("libraryRecipes");
        for (LibraryRecipe recipe : context.libraryRecipes()) {
            ObjectNode recipeNode = recipes.addObject();
            recipeNode.put("id", recipe.id().toString());
            recipeNode.put("title", recipe.title());
            recipeNode.put("servings", recipe.servings());
            ArrayNode tags = recipeNode.putArray("tags");
            for (String tag : recipe.tags()) {
                tags.add(tag);
            }
        }
        return root;
    }

    private static List<PluginCard> parseCards(JsonNode suggestions) {
        List<PluginCard> cards = new ArrayList<>();
        for (JsonNode cardNode : suggestions) {
            if (cards.size() == SuggestionService.MAX_CARDS_PER_PLUGIN) {
                break;
            }
            if (!cardNode.isObject()) {
                throw new PluginProtocolException("each suggestion must be an object");
            }
            List<PluginCardEntry> entries = new ArrayList<>();
            JsonNode entriesNode = cardNode.get("entries");
            if (entriesNode != null && entriesNode.isArray()) {
                for (JsonNode entryNode : entriesNode) {
                    if (entries.size() == SuggestionService.MAX_ENTRIES_PER_CARD) {
                        break;
                    }
                    if (!entryNode.isObject()) {
                        throw new PluginProtocolException("each suggested entry must be an object");
                    }
                    entries.add(new PluginCardEntry(
                            Json.text(entryNode, "date"),
                            Json.text(entryNode, "mealType"),
                            Json.text(entryNode, "recipeId"),
                            Json.number(entryNode, "servings")));
                }
            }
            cards.add(new PluginCard(
                    Json.text(cardNode, "title"), Json.text(cardNode, "blurb"), Json.text(cardNode, "icon"), entries));
        }
        return List.copyOf(cards);
    }
}
