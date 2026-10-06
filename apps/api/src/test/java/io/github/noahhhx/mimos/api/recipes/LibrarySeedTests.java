package io.github.noahhhx.mimos.api.recipes;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.recipes.library.LibrarySeeder;
import io.github.noahhhx.mimos.recipes.recipe.NutritionEstimate;
import io.github.noahhhx.mimos.recipes.recipe.NutritionSource;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The curated library is seeded at startup (idempotently), publicly
 * readable, and searchable — a new user's first session shows a real,
 * cookable library (roadmap step 11).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LibrarySeedTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    LibrarySeeder librarySeeder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RecipeService recipeService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void libraryIsSeededAndPubliclyReadable() {
        RestClient api =
                RestClient.builder().baseUrl("http://localhost:" + port).build();

        JsonNode library = requireNonNull(
                api.get().uri("/api/v1/public/recipes").retrieve().body(JsonNode.class), "library returned no body");
        assertThat(library.size()).isGreaterThanOrEqualTo(12);

        JsonNode bySlug = requireNonNull(
                api.get()
                        .uri("/api/v1/public/recipes/fluffy-buttermilk-pancakes")
                        .retrieve()
                        .body(JsonNode.class),
                "recipe by slug returned no body");
        assertThat(bySlug.get("title").asText()).isEqualTo("Fluffy Buttermilk Pancakes");
        assertThat(bySlug.get("isLibrary").asBoolean()).isTrue();
        assertThat(bySlug.get("ingredients").size()).isGreaterThanOrEqualTo(5);
        assertThat(bySlug.get("nutrition").get("calories").asDouble()).isPositive();

        // Search hits seeded content.
        JsonNode search = requireNonNull(
                api.get()
                        .uri(builder -> builder.path("/api/v1/public/recipes")
                                .queryParam("q", "pancakes")
                                .build())
                        .retrieve()
                        .body(JsonNode.class),
                "search returned no body");
        boolean found = false;
        for (JsonNode recipe : search) {
            found = found || recipe.get("slug").asText().equals("fluffy-buttermilk-pancakes");
        }
        assertThat(found).isTrue();
    }

    /** ADR-0015: library nutrition is calculated, and no library line is left out of it by accident. */
    @Test
    void everyLibraryLineCountsTowardCalculatedNutrition() {
        JsonNode seeds;
        try (InputStream in = new ClassPathResource("library/library-seed.json").getInputStream()) {
            seeds = objectMapper.readTree(in);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        assertThat(seeds.size()).isPositive();
        for (JsonNode seed : seeds) {
            Recipe recipe = recipeService.findBySlug(seed.get("slug").asText()).orElseThrow();
            assertThat(recipe.nutritionSource()).as(recipe.slug()).isEqualTo(NutritionSource.INGREDIENTS);
            assertThat(recipe.nutrition().calories()).as(recipe.slug()).isPositive();
            NutritionEstimate estimate =
                    recipeService.estimateNutrition(UUID.randomUUID(), recipe.ingredients(), recipe.servings());
            for (int line = 0; line < estimate.lines().size(); line++) {
                assertThat(estimate.lines().get(line))
                        .as(
                                "%s: %s",
                                recipe.slug(), recipe.ingredients().get(line).name())
                        .isIn(NutritionEstimate.LineStatus.COUNTED, NutritionEstimate.LineStatus.UNMEASURED);
            }
        }
    }

    @Test
    void seedingIsIdempotent() {
        // The app context already seeded once at startup; run again and
        // verify no duplicates and no failures.
        librarySeeder.run(new org.springframework.boot.DefaultApplicationArguments());

        Integer pancakes = jdbc.queryForObject(
                "select count(*) from recipe where slug = 'fluffy-buttermilk-pancakes'", Integer.class);
        assertThat(pancakes).isEqualTo(1);

        Integer libraryCount =
                jdbc.queryForObject("select count(*) from recipe where household_id is null", Integer.class);
        assertThat(libraryCount).isGreaterThanOrEqualTo(12);
    }
}
