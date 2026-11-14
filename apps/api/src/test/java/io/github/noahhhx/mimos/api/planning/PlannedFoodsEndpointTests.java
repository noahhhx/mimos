package io.github.noahhhx.mimos.api.planning;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestUser;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Planning a single food, an ingredient by slug, without a recipe (ADR-0026). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlannedFoodsEndpointTests extends ApiIntegrationTestSupport {

    private static final LocalDate MONDAY = LocalDate.of(2026, 5, 4);
    private static final String ENTRIES = "/api/v1/plans/" + MONDAY + "/entries";

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aFoodIsPlannedChangedCountedListedAndRemovedWithItsIngredient() {
        TestUser person = user();
        String bagel = ingredient(person, "Test bagel", "PER_100_G", 270, 10, 52, 1.5);

        JsonNode banana = person.ok(HttpMethod.POST, ENTRIES, foodEntry("SNACK", "banana", null, 2));
        JsonNode planned = person.ok(HttpMethod.POST, ENTRIES, foodEntry("BREAKFAST", bagel, "g", 85));

        assertThat(banana.has("recipeId")).isFalse();
        assertThat(banana.has("recipeTitle")).isFalse();
        assertThat(banana.get("food").get("catalogSlug").asText()).isEqualTo("banana");
        assertThat(banana.get("food").get("name").asText()).isEqualTo("Banana");
        assertThat(banana.get("food").has("unit")).isFalse();
        assertThat(banana.get("servings").asDouble()).isEqualTo(2);
        assertThat(planned.get("food").get("unit").asText()).isEqualTo("g");
        assertThat(planned.get("food").get("name").asText()).isEqualTo("Test bagel");
        assertThat(person.ok(HttpMethod.GET, "/api/v1/plans/" + MONDAY, null).get("entries"))
                .hasSize(2);

        JsonNode patched =
                person.ok(HttpMethod.PATCH, ENTRIES + "/" + planned.get("id").asText(), Map.of("servings", 100));
        assertThat(patched.get("servings").asDouble()).isEqualTo(100);
        assertThat(patched.get("food").get("catalogSlug").asText()).isEqualTo(bagel);

        JsonNode list = person.ok(HttpMethod.POST, "/api/v1/plans/" + MONDAY + "/shopping-list", null);
        JsonNode listedBanana = item(list, "Banana");
        JsonNode listedBagel = item(list, "Test bagel");
        assertThat(listedBanana.get("quantity").asDouble()).isEqualTo(2);
        assertThat(listedBanana.has("unit")).isFalse();
        assertThat(listedBagel.get("quantity").asDouble()).isEqualTo(100);
        assertThat(listedBagel.get("unit").asText()).isEqualTo("g");

        JsonNode monday = person.ok(HttpMethod.GET, "/api/v1/plans/" + MONDAY + "/my-totals", null)
                .get("days")
                .get(0);
        JsonNode total = monday.get("total");
        assertThat(monday.get("meals").asInt()).isEqualTo(2);
        assertThat(monday.get("mealsWithoutNutrition").asInt()).isZero();
        assertThat(total.get("calories").asDouble()).isEqualTo(105 * 2 + 270.0);
        assertThat(total.get("proteinG").asDouble()).isEqualTo(1.3 * 2 + 10);
        assertThat(total.get("carbsG").asDouble()).isEqualTo(27.0 * 2 + 52);
        assertThat(total.get("fatG").asDouble()).isEqualTo(0.4 * 2 + 1.5);

        assertThat(person.status(HttpMethod.DELETE, "/api/v1/ingredients/" + bagel, null))
                .isEqualTo(204);
        assertThat(person.ok(HttpMethod.GET, "/api/v1/plans/" + MONDAY, null).get("entries"))
                .extracting(entry -> entry.get("food").get("catalogSlug").asText())
                .containsExactly("banana");
    }

    @Test
    void entriesInOneSlotComeBackInTheOrderTheyWerePlanned() {
        TestUser person = user();
        for (int amount = 1; amount <= 8; amount++) {
            person.ok(HttpMethod.POST, ENTRIES, foodEntry("SNACK", "banana", null, amount));
        }

        assertThat(person.ok(HttpMethod.GET, "/api/v1/plans/" + MONDAY, null).get("entries"))
                .extracting(entry -> entry.get("servings").asInt())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void aFoodSharedByTheHouseholdCountsItsAmountDividedByItsDiners() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        Map<String, Object> shared = foodEntry("SNACK", "banana", null, 3);
        shared.put("diners", java.util.List.of(host.profileId, guest.profileId));
        host.ok(HttpMethod.POST, ENTRIES, shared);

        JsonNode monday = guest.ok(HttpMethod.GET, "/api/v1/plans/" + MONDAY + "/my-totals", null)
                .get("days")
                .get(0);

        assertThat(monday.get("total").get("calories").asDouble()).isEqualTo(157.5);
    }

    @Test
    void exactlyOneOfARecipeAndAFoodIsPlanned() {
        TestUser person = user();
        UUID recipe = person.recipe("Both Stew");
        Map<String, Object> both = foodEntry("DINNER", "banana", null, 1);
        both.put("recipeId", recipe);
        Map<String, Object> neither = new HashMap<>(foodEntry("DINNER", "banana", null, 1));
        neither.remove("food");

        assertThat(person.status(HttpMethod.POST, ENTRIES, both)).isEqualTo(400);
        assertThat(person.status(HttpMethod.POST, ENTRIES, neither)).isEqualTo(400);
        assertThat(person.ok(HttpMethod.GET, "/api/v1/plans/" + MONDAY, null).get("entries"))
                .isEmpty();
    }

    @Test
    void aFoodNeedsAVisibleIngredientAndAUnitItsBasisTakes() {
        TestUser person = user();
        TestUser neighbour = user();
        String oil = ingredient(person, "Test oil", "PER_100_ML", 800, 0, 0, 90);
        String mine = ingredient(neighbour, "Neighbour jam", "PER_100_G", 250, 0, 60, 0);

        assertThat(person.status(HttpMethod.POST, ENTRIES, foodEntry("SNACK", "banana", "g", 1)))
                .as("a piece food takes no unit")
                .isEqualTo(400);
        assertThat(person.status(HttpMethod.POST, ENTRIES, foodEntry("SNACK", oil, "g", 15)))
                .as("a per-100-ml food does not take grams")
                .isEqualTo(400);
        assertThat(person.status(HttpMethod.POST, ENTRIES, foodEntry("SNACK", oil, null, 15)))
                .as("a per-100-ml food needs a unit")
                .isEqualTo(400);
        assertThat(person.status(HttpMethod.POST, ENTRIES, foodEntry("SNACK", oil, "tbsp", 1)))
                .isEqualTo(201);
        assertThat(person.status(HttpMethod.POST, ENTRIES, foodEntry("SNACK", "no-such-food", null, 1)))
                .isEqualTo(404);
        assertThat(person.status(HttpMethod.POST, ENTRIES, foodEntry("SNACK", mine, "g", 50)))
                .as("another household's own ingredient")
                .isEqualTo(404);
        assertThat(person.status(HttpMethod.POST, ENTRIES, foodEntry("SNACK", "banana", null, 10_001)))
                .isEqualTo(400);
        assertThat(person.status(HttpMethod.POST, ENTRIES, foodEntry("SNACK", "banana", null, 0)))
                .isEqualTo(400);
    }

    @Test
    void aFoodAmountAboveItsBoundIsRefusedInAPatchToo() {
        TestUser person = user();
        JsonNode banana = person.ok(HttpMethod.POST, ENTRIES, foodEntry("SNACK", "banana", null, 1));

        assertThat(person.status(
                        HttpMethod.PATCH, ENTRIES + "/" + banana.get("id").asText(), Map.of("servings", 10_001)))
                .isEqualTo(400);
        assertThat(person.status(
                        HttpMethod.PATCH, ENTRIES + "/" + banana.get("id").asText(), Map.of("servings", 5000)))
                .isEqualTo(200);
    }

    @Test
    void aFoodWhoseIngredientChangedBasisCountsAsWithoutNutrition() {
        TestUser person = user();
        String bagel = ingredient(person, "Changing bagel", "PER_100_G", 270, 10, 52, 1.5);
        person.ok(HttpMethod.POST, ENTRIES, foodEntry("BREAKFAST", bagel, "g", 85));
        person.ok(
                HttpMethod.PUT,
                "/api/v1/ingredients/" + bagel,
                Map.of(
                        "name",
                        "Changing bagel",
                        "basis",
                        "PER_100_ML",
                        "nutrition",
                        Map.of("calories", 270, "proteinG", 10, "carbsG", 52, "fatG", 1.5)));

        JsonNode monday = person.ok(HttpMethod.GET, "/api/v1/plans/" + MONDAY + "/my-totals", null)
                .get("days")
                .get(0);

        assertThat(monday.get("meals").asInt()).isEqualTo(1);
        assertThat(monday.get("mealsWithoutNutrition").asInt()).isEqualTo(1);
        assertThat(monday.get("total").size()).isZero();
    }

    private TestUser user() {
        return new TestUser(port, objectMapper, jdbc);
    }

    private static String ingredient(
            TestUser person, String name, String basis, double calories, double protein, double carbs, double fat) {
        return person.ok(
                        HttpMethod.POST,
                        "/api/v1/ingredients",
                        Map.of(
                                "name",
                                name,
                                "basis",
                                basis,
                                "nutrition",
                                Map.of("calories", calories, "proteinG", protein, "carbsG", carbs, "fatG", fat)))
                .get("slug")
                .asText();
    }

    private static Map<String, Object> foodEntry(String mealType, String slug, @Nullable String unit, double amount) {
        Map<String, Object> food = new HashMap<>();
        food.put("catalogSlug", slug);
        if (unit != null) {
            food.put("unit", unit);
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("date", MONDAY.toString());
        entry.put("mealType", mealType);
        entry.put("food", food);
        entry.put("servings", amount);
        return entry;
    }

    private static JsonNode item(JsonNode list, String name) {
        for (JsonNode item : list.get("items")) {
            if (item.get("name").asText().equals(name)) {
                return item;
            }
        }
        throw new AssertionError("no item named " + name + " in " + list);
    }
}
