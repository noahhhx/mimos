package io.github.noahhhx.mimos.api.planning;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Shopping list generation end to end: quantities aggregate across the
 * week's planned recipes (scaled by servings), items are grouped into
 * aisles, and checked-off state survives regeneration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShoppingListsEndpointTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void generatedListAggregatesAndGroups() {
        RestClient api = api();
        String token = accessToken();
        LocalDate monday = LocalDate.of(2026, 2, 2); // a Monday

        // Two recipes sharing an ingredient, with different batch sizes.
        JsonNode pancakes = createRecipe(api, token, "Aggregation Pancakes", 4, 2, "cups", "flour");
        JsonNode gravy = createRecipe(api, token, "Aggregation Gravy", 2, 1.5, "cups", "flour");

        plan(
                api,
                token,
                monday,
                monday.plusDays(1),
                "BREAKFAST",
                pancakes.get("id").asText(),
                4);
        plan(api, token, monday, monday.plusDays(2), "DINNER", gravy.get("id").asText(), 2);

        // Pancakes: 4 servings of a 4-serving recipe -> 2 cups flour.
        // Gravy: 2 servings of a 2-serving recipe -> 1.5 cups flour.
        // Total: 3.5 cups flour (aggregated), plus the eggs lines separately.
        JsonNode list = generate(api, token, monday);
        assertThat(list.get("items")).isNotEmpty();

        JsonNode flour = findItem(list, "flour");
        assertThat(flour).isNotNull();
        flour = requireNonNull(flour, "flour must be on the list");
        assertThat(flour.get("quantity").asDouble()).isEqualTo(3.5);
        assertThat(flour.get("unit").asText()).isEqualTo("cups");
        assertThat(flour.get("category").asText()).isEqualTo("Pantry");

        // Eggs appear as a separate (unit-less) line.
        JsonNode eggs = findItem(list, "eggs");
        assertThat(eggs).isNotNull();
        eggs = requireNonNull(eggs, "eggs must be on the list");
        assertThat(eggs.get("category").asText()).isEqualTo("Dairy & Eggs");
        // One egg per recipe, both planned at full batch: 1 + 1 = 2.
        assertThat(eggs.get("quantity").asDouble()).isEqualTo(2);

        // GET returns the same list without regenerating.
        JsonNode fetched = requireNonNull(
                api.get()
                        .uri("/api/v1/plans/{start}/shopping-list", monday.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "shopping list GET returned no body");
        assertThat(fetched.get("items")).hasSize(list.get("items").size());
    }

    @Test
    void unmeasuredIngredientsAddALineButNothingToItsTotal() {
        RestClient api = api();
        String token = accessToken();
        LocalDate monday = LocalDate.of(2026, 4, 6); // a Monday

        // ADR-0007: "lemon" is measured once and unmeasured once; "sea salt" never is.
        JsonNode fish = createRecipe(
                api,
                token,
                "Unmeasured Fish",
                2,
                objectMapper
                        .createArrayNode()
                        .add(objectMapper.createObjectNode().put("quantity", 1).put("name", "lemon"))
                        .add(objectMapper.createObjectNode().put("name", "sea salt")));
        JsonNode salad = createRecipe(
                api,
                token,
                "Unmeasured Salad",
                2,
                objectMapper
                        .createArrayNode()
                        .add(objectMapper.createObjectNode().put("name", "lemon")));
        plan(api, token, monday, monday, "DINNER", fish.get("id").asText(), 2);
        plan(api, token, monday, monday.plusDays(1), "LUNCH", salad.get("id").asText(), 2);

        JsonNode list = generate(api, token, monday);

        JsonNode lemon = requireNonNull(findItem(list, "lemon"), "lemon must be on the list");
        assertThat(lemon.get("quantity").asDouble()).isEqualTo(1);
        JsonNode salt = requireNonNull(findItem(list, "sea salt"), "sea salt must be on the list");
        assertThat(salt.has("quantity")).isFalse();
    }

    @Test
    void countedIngredientsRoundUpToWholeItems() {
        RestClient api = api();
        // Its own account: SuggestionsEndpointTests plans this week for `test` and counts the slots.
        String token = accessToken(createUser());
        LocalDate monday = LocalDate.of(2026, 5, 4); // a Monday

        // An 8-serving soup with 1 onion and 2 cups stock, planned twice at 1 serving.
        JsonNode soup = createRecipe(
                api,
                token,
                "Counted Soup",
                8,
                objectMapper
                        .createArrayNode()
                        .add(objectMapper.createObjectNode().put("quantity", 1).put("name", "onion"))
                        .add(objectMapper
                                .createObjectNode()
                                .put("quantity", 2)
                                .put("unit", "cups")
                                .put("name", "stock")));
        plan(api, token, monday, monday, "LUNCH", soup.get("id").asText(), 1);
        plan(api, token, monday, monday.plusDays(1), "LUNCH", soup.get("id").asText(), 1);

        JsonNode list = generate(api, token, monday);

        // 1/8 + 1/8 = 0.25 onion: totaled first, then one whole onion (not 0.25, and not 2).
        JsonNode onion = requireNonNull(findItem(list, "onion"), "onion must be on the list");
        assertThat(onion.get("quantity").asDouble()).isEqualTo(1);
        // Measured amounts are not rounded: 2/8 + 2/8 cups.
        JsonNode stock = requireNonNull(findItem(list, "stock"), "stock must be on the list");
        assertThat(stock.get("quantity").asDouble()).isEqualTo(0.5);
    }

    @Test
    void recipesUsedAsIngredientsAddTheirOwnIngredients() {
        RestClient api = api();
        String token = accessToken(createUser());
        LocalDate monday = LocalDate.of(2026, 6, 1); // a Monday

        JsonNode dough = createRecipe(
                api, token, "Chain Dough", 2, objectMapper.createArrayNode().add(line(300, "g", "bread flour")));
        JsonNode focaccia = createRecipe(
                api,
                token,
                "Chain Focaccia",
                4,
                objectMapper
                        .createArrayNode()
                        .add(line(1, "recipe", "Chain Dough")
                                .put("recipeId", dough.get("id").asText()))
                        .add(line(40, "ml", "olive oil")));
        JsonNode sandwich = createRecipe(
                api,
                token,
                "Chain Sandwich",
                2,
                objectMapper
                        .createArrayNode()
                        .add(line(2, "servings", "Chain Focaccia")
                                .put("recipeId", focaccia.get("id").asText()))
                        .add(objectMapper.createObjectNode().put("quantity", 1).put("name", "tomato")));
        plan(api, token, monday, monday, "LUNCH", sandwich.get("id").asText(), 3);

        JsonNode list = generate(api, token, monday);

        // 3 of 2 sandwich servings is 3 of 4 focaccia servings, which is 3/4 of the whole dough.
        assertThat(findItem(list, "Chain Focaccia")).isNull();
        assertThat(findItem(list, "Chain Dough")).isNull();
        JsonNode flour = requireNonNull(findItem(list, "bread flour"), "flour must be on the list");
        assertThat(flour.get("quantity").asDouble()).isEqualTo(225);
        assertThat(flour.get("unit").asText()).isEqualTo("g");
        JsonNode oil = requireNonNull(findItem(list, "olive oil"), "oil must be on the list");
        assertThat(oil.get("quantity").asDouble()).isEqualTo(30);
        JsonNode tomato = requireNonNull(findItem(list, "tomato"), "tomato must be on the list");
        assertThat(tomato.get("quantity").asDouble()).isEqualTo(2);
        assertThat(list.get("items")).hasSize(3);
    }

    private ObjectNode line(double quantity, String unit, String name) {
        return objectMapper
                .createObjectNode()
                .put("quantity", quantity)
                .put("unit", unit)
                .put("name", name);
    }

    @Test
    void checkedStateSurvivesRegeneration() {
        RestClient api = api();
        String token = accessToken();
        LocalDate monday = LocalDate.of(2026, 2, 9); // a Monday
        JsonNode soup = createRecipe(api, token, "Regeneration Soup", 4, 2, "cups", "lentils");
        plan(api, token, monday, monday, "DINNER", soup.get("id").asText(), 4);

        JsonNode list = generate(api, token, monday);
        JsonNode lentils = requireNonNull(findItem(list, "lentils"), "lentils must be on the list");
        assertThat(lentils.get("checked").asBoolean()).isFalse();

        // Check it off.
        ObjectNode patch = objectMapper.createObjectNode().put("checked", true);
        JsonNode checked = requireNonNull(
                api.patch()
                        .uri(builder -> builder.path("/api/v1/plans/{start}/shopping-list/items/{id}")
                                .build(monday.toString(), lentils.get("id").asText()))
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(patch)
                        .retrieve()
                        .body(JsonNode.class),
                "patch returned no body");
        assertThat(checked.get("checked").asBoolean()).isTrue();

        // Regenerate: the (unchanged) line keeps its checked state.
        JsonNode regenerated = generate(api, token, monday);
        JsonNode lentilsAfter = requireNonNull(findItem(regenerated, "lentils"), "lentils must survive regeneration");
        assertThat(lentilsAfter.get("checked").asBoolean()).isTrue();
    }

    @Test
    void neverGeneratedListIs404AndEmptyPlanYieldsEmptyList() {
        RestClient api = api();
        String token = accessToken();
        LocalDate monday = LocalDate.of(2026, 2, 16); // a Monday

        api.get()
                .uri("/api/v1/plans/{start}/shopping-list", monday.toString())
                .headers(headers -> headers.setBearerAuth(token))
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(404);
                    return null;
                });

        // An empty plan generates an empty (but existing) list.
        JsonNode list = generate(api, token, monday);
        assertThat(list.get("items")).isEmpty();
    }

    @Test
    void itemsAddedByHandSurviveRegenerationAndCanBeRemoved() {
        RestClient api = api();
        String token = accessToken(createUser());
        LocalDate monday = LocalDate.of(2026, 7, 6); // a Monday
        JsonNode soup = createRecipe(api, token, "Added Soup", 4, 2, "cups", "lentils");
        plan(api, token, monday, monday, "DINNER", soup.get("id").asText(), 4);
        generate(api, token, monday);

        JsonNode binBags = addItem(
                api,
                token,
                monday,
                objectMapper
                        .createObjectNode()
                        .put("name", "  bin bags ")
                        .put("quantity", 1)
                        .put("unit", "pack"));
        assertThat(binBags.get("name").asText()).isEqualTo("bin bags");
        assertThat(binBags.get("quantity").asDouble()).isEqualTo(1);
        assertThat(binBags.get("unit").asText()).isEqualTo("pack");
        assertThat(binBags.get("added").asBoolean()).isTrue();
        assertThat(binBags.get("checked").asBoolean()).isFalse();
        // Same name as a generated line: a line of its own, never merged with the plan's.
        JsonNode extraLentils =
                addItem(api, token, monday, objectMapper.createObjectNode().put("name", "lentils"));
        assertThat(extraLentils.get("category").asText()).isEqualTo("Pantry");
        assertThat(extraLentils.has("quantity")).isFalse();
        check(api, token, monday, binBags.get("id").asText());

        JsonNode regenerated = generate(api, token, monday);

        assertThat(regenerated.get("items")).hasSize(4); // lentils and eggs from the plan, plus two added
        JsonNode keptBags = requireNonNull(findItem(regenerated, "bin bags"), "bin bags must survive regeneration");
        assertThat(keptBags.get("added").asBoolean()).isTrue();
        assertThat(keptBags.get("checked").asBoolean()).isTrue();
        assertThat(keptBags.get("quantity").asDouble()).isEqualTo(1);
        JsonNode plannedLentils = null;
        for (JsonNode item : regenerated.get("items")) {
            if (item.get("name").asText().equals("lentils")
                    && !item.get("added").asBoolean()) {
                plannedLentils = item;
            }
        }
        plannedLentils = requireNonNull(plannedLentils, "the plan's lentils must be on the list");
        assertThat(plannedLentils.get("quantity").asDouble()).isEqualTo(2);

        // The plan's items are not removable: they would come back on regeneration.
        assertThat(remove(api, token, monday, plannedLentils.get("id").asText()))
                .isEqualTo(400);
        assertThat(remove(api, token, monday, keptBags.get("id").asText())).isEqualTo(204);
        assertThat(remove(api, token, monday, keptBags.get("id").asText())).isEqualTo(404);
        assertThat(findItem(generate(api, token, monday), "bin bags")).isNull();
    }

    @Test
    void addingToAWeekWithNoListGeneratesItFromThePlanFirst() {
        RestClient api = api();
        String token = accessToken(createUser());
        LocalDate monday = LocalDate.of(2026, 7, 13); // a Monday
        JsonNode soup = createRecipe(api, token, "First Add Soup", 4, 2, "cups", "barley");
        plan(api, token, monday, monday, "DINNER", soup.get("id").asText(), 4);

        addItem(
                api,
                token,
                monday,
                objectMapper
                        .createObjectNode()
                        .put("name", "milk")
                        .put("quantity", 2)
                        .put("unit", "l"));

        JsonNode list = requireNonNull(
                api.get()
                        .uri("/api/v1/plans/{start}/shopping-list", monday.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "shopping list GET returned no body");
        assertThat(findItem(list, "barley")).isNotNull();
        JsonNode milk = requireNonNull(findItem(list, "milk"), "milk must be on the list");
        assertThat(milk.get("category").asText()).isEqualTo("Dairy & Eggs");
        assertThat(milk.get("added").asBoolean()).isTrue();
    }

    @Test
    void anAddedItemNeedsANameAndASensibleAmount() {
        RestClient api = api();
        String token = accessToken(createUser());
        LocalDate monday = LocalDate.of(2026, 7, 20); // a Monday

        assertThat(addStatus(api, token, monday, objectMapper.createObjectNode().put("name", " ")))
                .isEqualTo(400);
        assertThat(addStatus(
                        api,
                        token,
                        monday,
                        objectMapper.createObjectNode().put("name", "milk").put("unit", "l")))
                .isEqualTo(400);
        assertThat(addStatus(
                        api,
                        token,
                        monday,
                        objectMapper.createObjectNode().put("name", "milk").put("quantity", 0)))
                .isEqualTo(400);
        assertThat(addStatus(
                        api,
                        token,
                        monday,
                        objectMapper.createObjectNode().put("name", "milk").put("quantity", 10_001)))
                .isEqualTo(400);
        assertThat(addStatus(
                        api,
                        token,
                        monday.plusDays(1),
                        objectMapper.createObjectNode().put("name", "milk")))
                .isEqualTo(400);
    }

    private JsonNode addItem(RestClient api, String token, LocalDate startDate, ObjectNode input) {
        return requireNonNull(
                api.post()
                        .uri("/api/v1/plans/{start}/shopping-list/items", startDate.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(input)
                        .exchange((req, res) -> {
                            assertThat(res.getStatusCode().value()).isEqualTo(201);
                            return requireNonNull(res.bodyTo(JsonNode.class), "add returned no body");
                        }),
                "add returned no body");
    }

    private int addStatus(RestClient api, String token, LocalDate startDate, ObjectNode input) {
        return requireNonNull(api.post()
                .uri("/api/v1/plans/{start}/shopping-list/items", startDate.toString())
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(input)
                .exchange((req, res) -> res.getStatusCode().value()));
    }

    private int remove(RestClient api, String token, LocalDate startDate, String itemId) {
        return requireNonNull(api.delete()
                .uri("/api/v1/plans/{start}/shopping-list/items/{id}", startDate.toString(), itemId)
                .headers(headers -> headers.setBearerAuth(token))
                .exchange((req, res) -> res.getStatusCode().value()));
    }

    private void check(RestClient api, String token, LocalDate startDate, String itemId) {
        api.patch()
                .uri("/api/v1/plans/{start}/shopping-list/items/{id}", startDate.toString(), itemId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.createObjectNode().put("checked", true))
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode generate(RestClient api, String token, LocalDate startDate) {
        return requireNonNull(
                api.post()
                        .uri("/api/v1/plans/{start}/shopping-list", startDate.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "generate returned no body");
    }

    private void plan(
            RestClient api,
            String token,
            LocalDate startDate,
            LocalDate date,
            String mealType,
            String recipeId,
            double servings) {
        api.post()
                .uri("/api/v1/plans/{start}/entries", startDate.toString())
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper
                        .createObjectNode()
                        .put("date", date.toString())
                        .put("mealType", mealType)
                        .put("recipeId", recipeId)
                        .put("servings", servings))
                .retrieve()
                .body(JsonNode.class);
    }

    /** A two-ingredient recipe: `amount unit ingredient` plus one egg. */
    private JsonNode createRecipe(
            RestClient api, String token, String title, int servings, double amount, String unit, String ingredient) {
        return createRecipe(
                api,
                token,
                title,
                servings,
                objectMapper
                        .createArrayNode()
                        .add(objectMapper
                                .createObjectNode()
                                .put("quantity", amount)
                                .put("unit", unit)
                                .put("name", ingredient))
                        .add(objectMapper.createObjectNode().put("quantity", 1).put("name", "eggs")));
    }

    private JsonNode createRecipe(RestClient api, String token, String title, int servings, ArrayNode ingredients) {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", title);
        input.put("description", "Test recipe.");
        input.put("servings", servings);
        input.set("tags", objectMapper.createArrayNode().add("test"));
        input.set(
                "nutrition",
                objectMapper
                        .createObjectNode()
                        .put("calories", 300)
                        .put("proteinG", 12)
                        .put("carbsG", 30)
                        .put("fatG", 10));
        input.put("nutritionSource", "MANUAL");
        input.set("ingredients", ingredients);
        input.set(
                "steps",
                objectMapper
                        .createArrayNode()
                        .add(objectMapper.createObjectNode().put("instruction", "Cook.")));
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

    private static @Nullable JsonNode findItem(JsonNode list, String name) {
        for (JsonNode item : list.get("items")) {
            if (item.get("name").asText().equalsIgnoreCase(name)) {
                return item;
            }
        }
        return null;
    }
}
