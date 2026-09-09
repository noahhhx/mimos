package io.github.noahhhx.mimos.api.plugins;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * With no plugins registered the product is unchanged: the suggestions
 * endpoint answers an empty list and the week rules still apply
 * (ADR-0006: suggestions are an enhancement, never a dependency).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuggestionsNoPluginsTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void noPluginsMeansNoSuggestions() {
        JsonNode body = requireNonNull(
                api().get()
                        .uri("/api/v1/plans/{start}/suggestions", "2026-01-05")
                        .headers(headers -> headers.setBearerAuth(accessToken()))
                        .retrieve()
                        .body(JsonNode.class),
                "suggestions returned no body");
        assertThat(body.get("suggestions")).isEmpty();
    }

    @Test
    void theWeekRulesStillApply() {
        api().get()
                .uri("/api/v1/plans/{start}/suggestions", "2026-01-06")
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .exchange((request, response) -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(400);
                    return null;
                });
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        api().get()
                .uri(
                        "/api/v1/plans/{start}/suggestions",
                        LocalDate.of(2026, 1, 5).toString())
                .exchange((request, response) -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(401);
                    assertThat(requireNonNull(response.getHeaders().getContentType())
                                    .toString())
                            .startsWith("application/problem+json");
                    return null;
                });
    }
}
