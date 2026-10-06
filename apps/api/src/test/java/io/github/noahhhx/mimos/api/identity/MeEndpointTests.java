package io.github.noahhhx.mimos.api.identity;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises the authenticated vertical slice end to end: a real token from
 * the containerized Keycloak reaches {@code /api/v1/me}, the profile is
 * created on first use, and anonymous requests get problem-details.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeEndpointTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void authenticatedRequestReturnsProfileCreatedOnFirstUse() {
        RestClient api =
                RestClient.builder().baseUrl("http://localhost:" + port).build();

        JsonNode first = me(api);
        JsonNode second = me(api);

        assertThat(first).isNotNull();
        assertThat(first.get("subjectId").asText()).isNotBlank();
        assertThat(first.get("displayName").asText()).isEqualTo("test");
        assertThat(first.get("createdAt").asText()).isNotBlank();
        // Same subject, same profile row.
        assertThat(second.get("id")).isEqualTo(first.get("id"));

        Integer rows = jdbc.queryForObject(
                "select count(*) from user_profile where subject_id = ?",
                Integer.class,
                first.get("subjectId").asText());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void unauthenticatedRequestIsRejectedWithProblemDetails() {
        RestClient api =
                RestClient.builder().baseUrl("http://localhost:" + port).build();

        JsonNode problem = api.get().uri("/api/v1/me").exchange((req, res) -> {
            assertThat(res.getStatusCode().value()).isEqualTo(401);
            assertThat(res.getHeaders().getContentType())
                    .isNotNull()
                    .matches(ct -> ct.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            assertThat(res.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                    .isEqualTo("Bearer resource_metadata=\"http://localhost:" + port
                            + "/.well-known/oauth-protected-resource/api/v1/me\"");
            return objectMapper.readTree(res.getBody().readAllBytes());
        });

        assertThat(problem.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("instance").asText()).isEqualTo("/api/v1/me");
    }

    @Test
    void aNewUserGetsAHouseholdOfOneAndConcurrentFirstRequestsLeaveNoOtherHousehold() throws Exception {
        RestClient api =
                RestClient.builder().baseUrl("http://localhost:" + port).build();
        String token = accessToken(createUser());

        List<CompletableFuture<JsonNode>> firstRequests = IntStream.range(0, 8)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> me(api, token)))
                .toList();
        Set<String> profileIds = new HashSet<>();
        for (CompletableFuture<JsonNode> request : firstRequests) {
            profileIds.add(request.get().get("id").asText());
        }
        me(api, token);

        assertThat(profileIds).hasSize(1);
        UUID profileId = UUID.fromString(profileIds.iterator().next());
        UUID householdId =
                jdbc.queryForObject("select household_id from user_profile where id = ?", UUID.class, profileId);
        assertThat(jdbc.queryForList("select id from user_profile where household_id = ?", UUID.class, householdId))
                .containsExactly(profileId);
        assertThat(jdbc.queryForObject(
                        "select count(*) from household h"
                                + " where not exists (select 1 from user_profile p where p.household_id = h.id)",
                        Integer.class))
                .isZero();
    }

    private JsonNode me(RestClient api) {
        return me(api, accessToken());
    }

    private JsonNode me(RestClient api, String token) {
        JsonNode body = api.get()
                .uri("/api/v1/me")
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(JsonNode.class);
        return requireNonNull(body, "/api/v1/me returned no body");
    }
}
