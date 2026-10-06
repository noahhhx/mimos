package io.github.noahhhx.mimos.api.identity;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import io.github.noahhhx.mimos.api.support.TestRecipes;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What a user creates belongs to their household (ADR-0019): recipes
 * record the profile that created them, and planned meals name the caller
 * as their only diner, whether made through the API or restored by an
 * import.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HouseholdOwnershipTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void recipesAndPlannedMealsBelongToTheCallersHousehold() {
        RestClient api =
                RestClient.builder().baseUrl("http://localhost:" + port).build();
        String token = accessToken(createUser());
        UUID profileId = UUID.fromString(get(api, token, "/api/v1/me").get("id").asText());
        UUID householdId = householdOf(profileId);

        UUID recipeId = UUID.fromString(
                TestRecipes.create(api, token, "Household Soup").get("id").asText());
        JsonNode entry = requireNonNull(
                api.post()
                        .uri("/api/v1/plans/{start}/entries", "2026-01-05")
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("date", "2026-01-07", "mealType", "DINNER", "recipeId", recipeId, "servings", 2))
                        .retrieve()
                        .body(JsonNode.class),
                "add entry returned no body");

        assertThat(jdbc.queryForMap("select household_id, created_by_profile_id from recipe where id = ?", recipeId))
                .containsEntry("household_id", householdId)
                .containsEntry("created_by_profile_id", profileId);
        assertThat(jdbc.queryForList(
                        "select profile_id from meal_plan_entry_diner where entry_id = ?",
                        UUID.class,
                        UUID.fromString(entry.get("id").asText())))
                .containsExactly(profileId);
    }

    @Test
    void anImportRecordsTheImporterAsCreatorAndDiner() throws IOException {
        RestClient api =
                RestClient.builder().baseUrl("http://localhost:" + port).build();
        String token = accessToken(createUser());
        UUID profileId = UUID.fromString(get(api, token, "/api/v1/me").get("id").asText());
        UUID householdId = householdOf(profileId);
        JsonNode document;
        try (InputStream in = new ClassPathResource("export/v3.json").getInputStream()) {
            document = objectMapper.readTree(in);
        }

        api.post()
                .uri("/api/v1/account/import")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(document)
                .retrieve()
                .toBodilessEntity();

        assertThat(jdbc.queryForList(
                        "select distinct created_by_profile_id from recipe where household_id = ?",
                        UUID.class,
                        householdId))
                .containsExactly(profileId);
        assertThat(jdbc.queryForList("""
                        select distinct d.profile_id
                        from meal_plan_entry e
                        join meal_plan p on p.id = e.meal_plan_id
                        left join meal_plan_entry_diner d on d.entry_id = e.id
                        where p.household_id = ?
                        """, UUID.class, householdId)).containsExactly(profileId);
    }

    private UUID householdOf(UUID profileId) {
        return requireNonNull(
                jdbc.queryForObject("select household_id from user_profile where id = ?", UUID.class, profileId));
    }

    private static JsonNode get(RestClient api, String token, String uri) {
        return requireNonNull(
                api.get()
                        .uri(uri)
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "GET " + uri + " returned no body");
    }
}
