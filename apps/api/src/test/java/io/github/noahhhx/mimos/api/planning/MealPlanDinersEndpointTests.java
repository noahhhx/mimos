package io.github.noahhhx.mimos.api.planning;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestUser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

/**
 * Who eats a planned meal (ADR-0019): the defaults when a request names no
 * diners, explicit diners, the membership rule, and changing diners later.
 * Fresh users, so `test` and `test2` stay out of every household.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MealPlanDinersEndpointTests extends ApiIntegrationTestSupport {

    private static final String MONDAY = "2026-04-06";
    private static final String PLAN = "/api/v1/plans/" + MONDAY;

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aDinnerIsForEveryMemberAndOtherMealsForTheCaller() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        UUID stew = host.recipe("Family Stew");

        JsonNode dinner = plan(host, stew, "DINNER", null);
        JsonNode lunch = plan(host, stew, "LUNCH", null);
        JsonNode breakfast = plan(guest, stew, "BREAKFAST", null);
        JsonNode snack = plan(guest, stew, "SNACK", null);

        assertThat(dinerIds(dinner)).containsExactlyInAnyOrder(host.profileId, guest.profileId);
        assertThat(dinerIds(lunch)).containsExactly(host.profileId);
        assertThat(dinerIds(breakfast)).containsExactly(guest.profileId);
        assertThat(dinerIds(snack)).containsExactly(guest.profileId);
        assertThat(dinner.get("servings").asDouble())
                .as("servings stay what was asked for")
                .isEqualTo(2);
    }

    @Test
    void thePlanNamesEachEntrysDinersAndMarksTheCaller() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        UUID stew = host.recipe("Named Stew");
        UUID dinner = id(plan(host, stew, "DINNER", null));
        UUID lunch = id(plan(host, stew, "LUNCH", null));

        Map<UUID, JsonNode> entries = entriesById(guest.ok(HttpMethod.GET, PLAN, null));

        assertThat(people(requireNonNull(entries.get(dinner))))
                .containsExactlyInAnyOrder(
                        new Diner(host.profileId, host.username, false),
                        new Diner(guest.profileId, guest.username, true));
        assertThat(people(requireNonNull(entries.get(lunch))))
                .containsExactly(new Diner(host.profileId, host.username, false));
    }

    @Test
    void explicitDinersAreKept() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        UUID stew = host.recipe("Chosen Stew");

        JsonNode guestsLunch = plan(host, stew, "LUNCH", List.of(guest.profileId));
        JsonNode hostsDinner = plan(host, stew, "DINNER", List.of(host.profileId));
        JsonNode bothLunch = plan(guest, stew, "LUNCH", List.of(host.profileId, guest.profileId, guest.profileId));

        assertThat(dinerIds(guestsLunch)).containsExactly(guest.profileId);
        assertThat(dinerIds(hostsDinner)).containsExactly(host.profileId);
        assertThat(dinerIds(bothLunch)).containsExactlyInAnyOrder(host.profileId, guest.profileId);
    }

    @Test
    void anEntryNeedsADinerFromTheHousehold() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        TestUser outsider = user();
        UUID stew = host.recipe("Guarded Stew");
        UUID dinner = id(plan(host, stew, "DINNER", null));

        TestUser.Response empty = host.send(HttpMethod.POST, PLAN + "/entries", entry(stew, "DINNER", List.of()));
        TestUser.Response outsiders =
                host.send(HttpMethod.POST, PLAN + "/entries", entry(stew, "LUNCH", List.of(outsider.profileId)));
        TestUser.Response nobody =
                host.send(HttpMethod.POST, PLAN + "/entries", entry(stew, "LUNCH", List.of(UUID.randomUUID())));
        TestUser.Response mixed = host.send(
                HttpMethod.POST, PLAN + "/entries", entry(stew, "LUNCH", List.of(host.profileId, outsider.profileId)));

        assertThat(empty.status()).isEqualTo(400);
        assertThat(outsiders.status()).isEqualTo(400);
        assertThat(nobody.status()).isEqualTo(400);
        assertThat(mixed.status()).isEqualTo(400);
        assertThat(detail(outsiders))
                .as("someone in another household reads the same as nobody at all")
                .isEqualTo(detail(nobody))
                .doesNotContain(outsider.username)
                .doesNotContain(outsider.profileId.toString());
        assertThat(entriesById(host.ok(HttpMethod.GET, PLAN, null)).keySet())
                .as("nothing refused was planned")
                .containsExactly(dinner);

        assertThat(host.status(HttpMethod.PATCH, PLAN + "/entries/" + dinner, Map.of("diners", List.of())))
                .isEqualTo(400);
        assertThat(host.status(
                        HttpMethod.PATCH, PLAN + "/entries/" + dinner, Map.of("diners", List.of(outsider.profileId))))
                .isEqualTo(400);
        assertThat(dinerIds(requireNonNull(
                        entriesById(host.ok(HttpMethod.GET, PLAN, null)).get(dinner))))
                .containsExactlyInAnyOrder(host.profileId, guest.profileId);
    }

    @Test
    void dinersChangeWithoutChangingServings() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        UUID stew = host.recipe("Changing Stew");
        UUID dinner = id(plan(host, stew, "DINNER", null));
        String entry = PLAN + "/entries/" + dinner;

        JsonNode guestOnly = guest.ok(HttpMethod.PATCH, entry, Map.of("diners", List.of(guest.profileId)));
        assertThat(dinerIds(guestOnly)).containsExactly(guest.profileId);
        assertThat(guestOnly.get("servings").asDouble()).isEqualTo(2);

        JsonNode moreServings = host.ok(HttpMethod.PATCH, entry, Map.of("servings", 3));
        assertThat(moreServings.get("servings").asDouble()).isEqualTo(3);
        assertThat(dinerIds(moreServings)).as("servings alone keep the diners").containsExactly(guest.profileId);

        JsonNode both = host.ok(HttpMethod.PATCH, entry, Map.of("servings", 4, "diners", List.of(host.profileId)));
        assertThat(both.get("servings").asDouble()).isEqualTo(4);
        assertThat(dinerIds(both)).containsExactly(host.profileId);
        assertThat(dinerIds(requireNonNull(
                        entriesById(guest.ok(HttpMethod.GET, PLAN, null)).get(dinner))))
                .containsExactly(host.profileId);

        assertThat(host.status(HttpMethod.PATCH, entry, Map.of()))
                .as("a patch changes something")
                .isEqualTo(400);
    }

    @Test
    void aHouseholdOfOneEatsEverythingItPlans() {
        TestUser alone = user();
        UUID soup = alone.recipe("Solo Soup");

        JsonNode dinner = plan(alone, soup, "DINNER", null);
        JsonNode lunch = plan(alone, soup, "LUNCH", null);

        assertThat(people(dinner)).containsExactly(new Diner(alone.profileId, alone.username, true));
        assertThat(people(lunch)).containsExactly(new Diner(alone.profileId, alone.username, true));
        assertThat(alone.ok(HttpMethod.PATCH, PLAN + "/entries/" + id(dinner), Map.of("servings", 3))
                        .get("servings")
                        .asDouble())
                .isEqualTo(3);
    }

    private TestUser user() {
        return new TestUser(port, objectMapper, jdbc);
    }

    private static JsonNode plan(TestUser user, UUID recipeId, String mealType, @Nullable List<UUID> diners) {
        return user.ok(HttpMethod.POST, PLAN + "/entries", entry(recipeId, mealType, diners));
    }

    private static Map<String, Object> entry(UUID recipeId, String mealType, @Nullable List<UUID> diners) {
        Map<String, Object> body = new HashMap<>();
        body.put("date", MONDAY);
        body.put("mealType", mealType);
        body.put("recipeId", recipeId);
        body.put("servings", 2);
        if (diners != null) {
            body.put("diners", diners);
        }
        return body;
    }

    private static UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asText());
    }

    private static Map<UUID, JsonNode> entriesById(JsonNode plan) {
        Map<UUID, JsonNode> entries = new HashMap<>();
        plan.get("entries").forEach(entry -> entries.put(id(entry), entry));
        return entries;
    }

    private static List<UUID> dinerIds(JsonNode entry) {
        return people(entry).stream().map(Diner::id).toList();
    }

    private static List<Diner> people(JsonNode entry) {
        List<Diner> diners = new ArrayList<>();
        entry.get("diners")
                .forEach(person -> diners.add(new Diner(
                        id(person),
                        person.get("displayName").asText(),
                        person.get("you").asBoolean())));
        return diners;
    }

    private static String detail(TestUser.Response response) {
        JsonNode body = response.body();
        return body == null ? "" : body.get("detail").asText();
    }

    private record Diner(UUID id, String displayName, boolean you) {}
}
