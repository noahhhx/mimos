package io.github.noahhhx.mimos.api.household;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.MutableClock;
import io.github.noahhhx.mimos.api.support.TestRecipes;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Invites, joining, and leaving a household (ADR-0019), over HTTP with
 * fresh users, so no other test's data or membership is in the way.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HouseholdEndpointTests extends ApiIntegrationTestSupport {

    private static final String MONDAY = "2026-03-02";

    @TestConfiguration
    static class PinnedClock {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MutableClock clock;

    @Autowired
    Clock injectedClock;

    @Test
    void aNewUserIsAHouseholdOfOne() {
        User user = new User();

        JsonNode household = user.ok(HttpMethod.GET, "/api/v1/household", null);

        assertThat(household.get("members")).hasSize(1);
        assertThat(household.get("members").get(0).get("id").asText()).isEqualTo(user.profileId.toString());
        assertThat(household.get("members").get(0).get("you").asBoolean()).isTrue();
    }

    @Test
    void joiningFromAHouseholdOfOneBringsRecipesAndIngredientsAndDeletesTheRest() {
        assertThat(injectedClock).as("the API runs on the pinned clock").isSameAs(clock);
        User host = new User();
        User joiner = new User();
        UUID hostSoup = host.recipe("Host Soup");
        host.plan(hostSoup, "DINNER");
        UUID joinerBread = joiner.recipe("Joiner Bread");
        String joinerSpice = joiner.ok(
                        HttpMethod.POST,
                        "/api/v1/ingredients",
                        Map.of(
                                "name",
                                "Joiner Spice",
                                "basis",
                                "PER_100_G",
                                "nutrition",
                                Map.of("calories", 300, "proteinG", 10, "carbsG", 50, "fatG", 5)))
                .get("slug")
                .asText();
        joiner.plan(joinerBread, "LUNCH");
        joiner.ok(HttpMethod.POST, "/api/v1/plans/" + MONDAY + "/shopping-list", null);
        UUID joinerLog = joiner.log(joinerBread);
        UUID oldHousehold = joiner.household();
        jdbc.update("insert into plugin_opt_in (household_id, plugin_id) values (?, 'country-week')", oldHousehold);
        jdbc.update(
                "insert into plugin_subject (household_id, plugin_id, subject, created_at) values (?, 'country-week', ?, now())",
                oldHousehold,
                UUID.randomUUID());

        String token = host.invite();
        JsonNode preview = joiner.ok(HttpMethod.POST, "/api/v1/household/invites/preview", Map.of("token", token));
        assertThat(names(preview.get("members"))).containsExactly(host.username);
        assertThat(preview.get("alreadyMember").asBoolean()).isFalse();
        assertThat(preview.get("currentHouseholdShared").asBoolean()).isFalse();

        JsonNode joined = joiner.ok(HttpMethod.POST, "/api/v1/household/join", Map.of("token", token));

        assertThat(names(joined.get("members"))).containsExactlyInAnyOrder(host.username, joiner.username);
        assertThat(joiner.household()).isEqualTo(host.household());
        assertThat(count("select count(*) from household where id = ?", oldHousehold))
                .as("the old household is gone, and with it its plans, lists, opt-ins, and subjects")
                .isZero();
        for (User member : List.of(host, joiner)) {
            assertThat(titles(member.ok(HttpMethod.GET, "/api/v1/recipes", null)))
                    .containsExactlyInAnyOrder("Host Soup", "Joiner Bread");
            assertThat(slugs(member.ok(HttpMethod.GET, "/api/v1/ingredients?q=joiner", null)))
                    .containsExactly(joinerSpice);
            JsonNode plan = member.ok(HttpMethod.GET, "/api/v1/plans/" + MONDAY, null);
            assertThat(plan.get("entries")).hasSize(1);
            assertThat(plan.get("entries").get(0).get("recipeId").asText()).isEqualTo(hostSoup.toString());
        }
        assertThat(joiner.status(HttpMethod.GET, "/api/v1/plans/" + MONDAY + "/shopping-list", null))
                .as("the joiner's list was deleted, and the household has none for the week")
                .isEqualTo(404);
        assertThat(jdbc.queryForObject(
                        "select created_by_profile_id from recipe where id = ?", UUID.class, joinerBread))
                .isEqualTo(joiner.profileId);
        assertThat(jdbc.queryForObject("select recipe_id from meal_log where id = ?", UUID.class, joinerLog))
                .as("the joiner's log still links the recipe that came along")
                .isEqualTo(joinerBread);
    }

    @Test
    void anInviteWorksOnceAndOnlyForSevenDays() {
        User host = new User();
        User first = new User();
        User second = new User();

        String token = host.invite();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(count("select count(*) from household_invite where token_hash = ?", token))
                .as("only the token's hash is stored")
                .isZero();
        first.ok(HttpMethod.POST, "/api/v1/household/join", Map.of("token", token));
        assertThat(second.status(HttpMethod.POST, "/api/v1/household/join", Map.of("token", token)))
                .isEqualTo(410);
        assertThat(second.status(HttpMethod.POST, "/api/v1/household/invites/preview", Map.of("token", token)))
                .isEqualTo(410);

        JsonNode invite = host.ok(HttpMethod.POST, "/api/v1/household/invites", null);
        Map<String, String> later = Map.of("token", invite.get("token").asText());
        assertThat(Instant.parse(invite.get("expiresAt").asText()))
                .isCloseTo(clock.instant().plus(Duration.ofDays(7)), within(1, ChronoUnit.SECONDS));
        clock.advance(Duration.ofDays(7).minusMinutes(1));
        assertThat(second.status(HttpMethod.POST, "/api/v1/household/invites/preview", later))
                .as("still valid a minute before seven days are up")
                .isEqualTo(200);
        clock.advance(Duration.ofMinutes(2));
        assertThat(second.status(HttpMethod.POST, "/api/v1/household/invites/preview", later))
                .as("expired a minute after seven days are up")
                .isEqualTo(410);
        assertThat(second.status(HttpMethod.POST, "/api/v1/household/join", later))
                .isEqualTo(410);
        assertThat(second.household()).isNotEqualTo(host.household());

        assertThat(second.status(HttpMethod.POST, "/api/v1/household/join", Map.of("token", "no-such-invite")))
                .isEqualTo(404);
    }

    @Test
    void joiningYourOwnHouseholdIsAConflict() {
        User host = new User();
        String token = host.invite();

        JsonNode preview = host.ok(HttpMethod.POST, "/api/v1/household/invites/preview", Map.of("token", token));
        assertThat(preview.get("alreadyMember").asBoolean()).isTrue();
        assertThat(host.status(HttpMethod.POST, "/api/v1/household/join", Map.of("token", token)))
                .isEqualTo(409);
        assertThat(host.status(HttpMethod.POST, "/api/v1/household/invites/preview", Map.of("token", token)))
                .as("a refused join does not use the invite")
                .isEqualTo(200);
    }

    @Test
    void joiningFromASharedHouseholdBringsNothing() {
        User host = new User();
        User mover = new User();
        User otherHost = new User();
        mover.join(host);
        mover.recipe("Stew Made Together");
        otherHost.recipe("Other Pie");

        JsonNode preview =
                mover.ok(HttpMethod.POST, "/api/v1/household/invites/preview", Map.of("token", otherHost.invite()));
        assertThat(preview.get("currentHouseholdShared").asBoolean()).isTrue();
        mover.join(otherHost);

        assertThat(titles(host.ok(HttpMethod.GET, "/api/v1/recipes", null))).containsExactly("Stew Made Together");
        assertThat(titles(mover.ok(HttpMethod.GET, "/api/v1/recipes", null))).containsExactly("Other Pie");
        assertThat(names(host.ok(HttpMethod.GET, "/api/v1/household", null).get("members")))
                .containsExactly(host.username);
        assertThat(names(otherHost.ok(HttpMethod.GET, "/api/v1/household", null).get("members")))
                .containsExactlyInAnyOrder(otherHost.username, mover.username);
    }

    @Test
    void leavingTakesOnlyYourLog() {
        User host = new User();
        User leaver = new User();
        leaver.join(host);
        UUID soup = host.recipe("Shared Soup");
        UUID hostLunch = host.plan(soup, "LUNCH");
        UUID leaverLunch = leaver.plan(soup, "LUNCH");
        UUID dinner = host.plan(soup, "DINNER");
        jdbc.update("insert into meal_plan_entry_diner (entry_id, profile_id) values (?, ?)", dinner, leaver.profileId);
        UUID leaverLog = leaver.log(soup);
        UUID householdId = host.household();

        JsonNode left = leaver.ok(HttpMethod.POST, "/api/v1/household/leave", null);

        assertThat(names(left.get("members"))).containsExactly(leaver.username);
        assertThat(leaver.household()).isNotEqualTo(householdId);
        assertThat(names(host.ok(HttpMethod.GET, "/api/v1/household", null).get("members")))
                .containsExactly(host.username);
        assertThat(entryIds(host.ok(HttpMethod.GET, "/api/v1/plans/" + MONDAY, null)))
                .as("a meal only the leaver ate is gone")
                .containsExactlyInAnyOrder(hostLunch, dinner)
                .doesNotContain(leaverLunch);
        assertThat(jdbc.queryForList(
                        "select profile_id from meal_plan_entry_diner where entry_id = ?", UUID.class, dinner))
                .containsExactly(host.profileId);
        assertThat(titles(host.ok(HttpMethod.GET, "/api/v1/recipes", null))).containsExactly("Shared Soup");
        assertThat(leaver.ok(HttpMethod.GET, "/api/v1/recipes", null)).isEmpty();
        assertThat(leaver.status(HttpMethod.GET, "/api/v1/recipes/" + soup, null))
                .as("the household's recipes are now someone else's")
                .isEqualTo(404);
        JsonNode logs = leaver.ok(HttpMethod.GET, "/api/v1/logs?from=" + MONDAY + "&to=" + MONDAY, null);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).get("id").asText()).isEqualTo(leaverLog.toString());
        assertThat(logs.get(0).hasNonNull("recipeId"))
                .as("the link to the household's recipe is cleared")
                .isFalse();
        assertThat(logs.get(0).get("nutrition").get("calories").asDouble()).isEqualTo(400);
    }

    @Test
    void leavingAHouseholdOfOneIsAConflict() {
        User alone = new User();
        UUID household = alone.household();

        assertThat(alone.status(HttpMethod.POST, "/api/v1/household/leave", null))
                .isEqualTo(409);
        assertThat(alone.household()).isEqualTo(household);
    }

    @Test
    void importNeedsAnEmptyHouseholdOfOne() throws IOException {
        JsonNode document;
        try (InputStream in = new ClassPathResource("export/v3.json").getInputStream()) {
            document = objectMapper.readTree(in);
        }
        User host = new User();
        User member = new User();
        member.join(host);
        assertThat(member.status(HttpMethod.POST, "/api/v1/account/import", document))
                .as("a shared household is never empty to import into")
                .isEqualTo(409);

        User cook = new User();
        cook.ok(
                HttpMethod.POST,
                "/api/v1/ingredients",
                Map.of(
                        "name",
                        "Lonely Salt",
                        "basis",
                        "PER_100_G",
                        "nutrition",
                        Map.of("calories", 0, "proteinG", 0, "carbsG", 0, "fatG", 0)));
        assertThat(cook.status(HttpMethod.POST, "/api/v1/account/import", document))
                .as("an ingredient of your own makes the account not empty")
                .isEqualTo(409);
    }

    @Test
    void aHouseholdRecipeNamesWhoAddedIt() {
        User host = new User();
        User member = new User();
        member.join(host);
        UUID recipe = host.recipe("Host's Curry");

        JsonNode seenByMember = member.ok(HttpMethod.GET, "/api/v1/recipes/" + recipe, null);
        assertThat(seenByMember.get("createdBy").get("displayName").asText()).isEqualTo(host.username);
        assertThat(seenByMember.get("createdBy").get("you").asBoolean()).isFalse();
        JsonNode listedForHost =
                host.ok(HttpMethod.GET, "/api/v1/recipes", null).get(0);
        assertThat(listedForHost.get("createdBy").get("id").asText()).isEqualTo(host.profileId.toString());
        assertThat(listedForHost.get("createdBy").get("you").asBoolean()).isTrue();
        JsonNode library =
                host.ok(HttpMethod.GET, "/api/v1/recipes/library", null).get(0);
        assertThat(host.ok(
                                HttpMethod.GET,
                                "/api/v1/recipes/" + library.get("id").asText(),
                                null)
                        .hasNonNull("createdBy"))
                .isFalse();

        host.ok(HttpMethod.POST, "/api/v1/household/leave", null);
        assertThat(member.ok(HttpMethod.GET, "/api/v1/recipes/" + recipe, null)
                        .get("createdBy")
                        .get("displayName")
                        .asText())
                .as("a recipe still names who added it after they leave")
                .isEqualTo(host.username);
    }

    @Test
    void outsidersCannotSeeAHouseholdsRecipes() {
        User host = new User();
        User member = new User();
        User outsider = new User();
        member.join(host);
        UUID recipe = member.recipe("Family Secret");

        assertThat(host.status(HttpMethod.GET, "/api/v1/recipes/" + recipe, null))
                .isEqualTo(200);
        assertThat(outsider.status(HttpMethod.GET, "/api/v1/recipes/" + recipe, null))
                .isEqualTo(404);
    }

    private int count(String sql, Object... args) {
        return requireNonNull(jdbc.queryForObject(sql, Integer.class, args));
    }

    private static List<String> names(JsonNode people) {
        return values(people, "displayName");
    }

    private static List<String> titles(JsonNode recipes) {
        return values(recipes, "title");
    }

    private static List<String> slugs(JsonNode ingredients) {
        return values(ingredients, "slug");
    }

    private static List<UUID> entryIds(JsonNode plan) {
        return values(plan.get("entries"), "id").stream().map(UUID::fromString).toList();
    }

    private static List<String> values(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.get(field).asText()));
        return values;
    }

    /** A fresh realm user, signed in, with their profile created. */
    private final class User {

        final String username = createUser();
        final String token = accessToken(username);
        final RestClient api =
                RestClient.builder().baseUrl("http://localhost:" + port).build();
        final UUID profileId =
                UUID.fromString(ok(HttpMethod.GET, "/api/v1/me", null).get("id").asText());

        UUID household() {
            return requireNonNull(
                    jdbc.queryForObject("select household_id from user_profile where id = ?", UUID.class, profileId));
        }

        String invite() {
            return ok(HttpMethod.POST, "/api/v1/household/invites", null)
                    .get("token")
                    .asText();
        }

        void join(User host) {
            ok(HttpMethod.POST, "/api/v1/household/join", Map.of("token", host.invite()));
        }

        UUID recipe(String title) {
            return UUID.fromString(
                    TestRecipes.create(api, token, title).get("id").asText());
        }

        UUID plan(UUID recipeId, String mealType) {
            return UUID.fromString(ok(
                            HttpMethod.POST,
                            "/api/v1/plans/" + MONDAY + "/entries",
                            Map.of("date", MONDAY, "mealType", mealType, "recipeId", recipeId, "servings", 2))
                    .get("id")
                    .asText());
        }

        UUID log(UUID recipeId) {
            return UUID.fromString(ok(
                            HttpMethod.POST,
                            "/api/v1/logs",
                            Map.of("date", MONDAY, "mealType", "DINNER", "recipeId", recipeId, "servings", 1))
                    .get("id")
                    .asText());
        }

        JsonNode ok(HttpMethod method, String uri, @Nullable Object body) {
            Response response = send(method, uri, body);
            assertThat(response.status())
                    .as(method + " " + uri + " answered " + response.body())
                    .isBetween(200, 299);
            return requireNonNull(response.body(), method + " " + uri + " returned no body");
        }

        int status(HttpMethod method, String uri, @Nullable Object body) {
            return send(method, uri, body).status();
        }

        private Response send(HttpMethod method, String uri, @Nullable Object body) {
            RestClient.RequestBodySpec request =
                    api.method(method).uri(uri).headers(headers -> headers.setBearerAuth(token));
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return requireNonNull(request.exchange((req, res) -> {
                byte[] bytes = res.getBody().readAllBytes();
                return new Response(
                        res.getStatusCode().value(), bytes.length == 0 ? null : objectMapper.readTree(bytes));
            }));
        }
    }

    private record Response(int status, @Nullable JsonNode body) {}
}
