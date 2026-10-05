package io.github.noahhhx.mimos.api.identity;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
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

    private JsonNode me(RestClient api) {
        JsonNode body = api.get()
                .uri("/api/v1/me")
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .retrieve()
                .body(JsonNode.class);
        return requireNonNull(body, "/api/v1/me returned no body");
    }
}
