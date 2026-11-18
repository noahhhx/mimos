package io.github.noahhhx.mimos.api.recipes;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestUser;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A household's star rating of its own recipes (ADR-0035): one per recipe,
 * set or overwritten by any member, cleared, kept by a replace, and never
 * on a library recipe or another household's recipe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecipeRatingsEndpointTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void aRatingIsSetOverwrittenByAnotherMemberAndCleared() {
        TestUser host = user();
        TestUser guest = user();
        guest.join(host);
        UUID recipe = host.recipe("Rated stew");
        assertThat(host.ok(HttpMethod.GET, uri(recipe), null).has("rating")).isFalse();

        JsonNode rated = host.ok(HttpMethod.PUT, ratingUri(recipe), stars(4));
        assertThat(rated.get("rating").asInt()).isEqualTo(4);
        assertThat(rated.get("title").asText()).isEqualTo("Rated stew");

        JsonNode overwritten = guest.ok(HttpMethod.PUT, ratingUri(recipe), stars(2));
        assertThat(overwritten.get("rating").asInt()).isEqualTo(2);
        assertThat(host.ok(HttpMethod.GET, uri(recipe), null).get("rating").asInt())
                .isEqualTo(2);
        JsonNode listed = host.ok(HttpMethod.GET, "/api/v1/recipes", null);
        assertThat(listed.get(0).get("rating").asInt()).isEqualTo(2);

        assertThat(guest.status(HttpMethod.DELETE, ratingUri(recipe), null)).isEqualTo(204);
        assertThat(host.ok(HttpMethod.GET, uri(recipe), null).has("rating")).isFalse();
        assertThat(host.status(HttpMethod.DELETE, ratingUri(recipe), null)).isEqualTo(204);
    }

    @Test
    void ratingKeepsUpdatedAtAndAReplaceKeepsTheRating() {
        TestUser cook = user();
        UUID recipe = cook.recipe("Kept rating");
        String before = cook.ok(HttpMethod.GET, "/api/v1/recipes", null)
                .get(0)
                .get("updatedAt")
                .asText();

        cook.ok(HttpMethod.PUT, ratingUri(recipe), stars(5));

        assertThat(cook.ok(HttpMethod.GET, "/api/v1/recipes", null)
                        .get(0)
                        .get("updatedAt")
                        .asText())
                .isEqualTo(before);
        ObjectNode input =
                (ObjectNode) cook.ok(HttpMethod.GET, uri(recipe), null).deepCopy();
        input.put("title", "Kept rating, renamed");
        JsonNode replaced = cook.ok(HttpMethod.PUT, uri(recipe), input);
        assertThat(replaced.get("title").asText()).isEqualTo("Kept rating, renamed");
        assertThat(replaced.get("rating").asInt()).isEqualTo(5);
    }

    @Test
    void starsOutsideOneToFiveOrNotWholeAreRefused() {
        TestUser cook = user();
        UUID recipe = cook.recipe("Strict stars");

        for (Object bad : new Object[] {0, 6, -1, 3.5}) {
            assertThat(cook.status(HttpMethod.PUT, ratingUri(recipe), Map.of("stars", bad)))
                    .as("stars %s", bad)
                    .isEqualTo(400);
        }
        assertThat(cook.status(HttpMethod.PUT, ratingUri(recipe), Map.of())).isEqualTo(400);
        assertThat(cook.ok(HttpMethod.GET, uri(recipe), null).has("rating")).isFalse();
    }

    @Test
    void libraryRecipesCannotBeRatedAndNeverShowARating() {
        TestUser cook = user();
        UUID library = UUID.fromString(cook.ok(HttpMethod.GET, "/api/v1/recipes/library", null)
                .get(0)
                .get("id")
                .asText());

        assertThat(cook.status(HttpMethod.PUT, ratingUri(library), stars(3))).isEqualTo(403);
        assertThat(cook.status(HttpMethod.DELETE, ratingUri(library), null)).isEqualTo(403);
        assertThat(cook.ok(HttpMethod.GET, uri(library), null).has("rating")).isFalse();
    }

    @Test
    void anotherHouseholdsRecipeIsNotFoundAndKeepsItsRating() {
        TestUser owner = user();
        TestUser stranger = user();
        UUID recipe = owner.recipe("Private rating");
        owner.ok(HttpMethod.PUT, ratingUri(recipe), stars(3));

        assertThat(stranger.status(HttpMethod.PUT, ratingUri(recipe), stars(1))).isEqualTo(404);
        assertThat(stranger.status(HttpMethod.DELETE, ratingUri(recipe), null)).isEqualTo(404);
        assertThat(owner.ok(HttpMethod.GET, uri(recipe), null).get("rating").asInt())
                .isEqualTo(3);
    }

    @Test
    void theDatabaseRefusesARatingOutsideOneToFive() {
        TestUser cook = user();
        UUID recipe = cook.recipe("Checked");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update recipe set rating = 6 where id = ?", recipe));
    }

    private TestUser user() {
        return new TestUser(port, objectMapper, jdbc);
    }

    private static String uri(UUID recipe) {
        return "/api/v1/recipes/" + recipe;
    }

    private static String ratingUri(UUID recipe) {
        return uri(recipe) + "/rating";
    }

    private static Map<String, Object> stars(int stars) {
        return Map.of("stars", stars);
    }
}
