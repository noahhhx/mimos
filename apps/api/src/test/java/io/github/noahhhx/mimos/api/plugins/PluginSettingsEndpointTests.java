package io.github.noahhhx.mimos.api.plugins;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.io.IOException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Per-user plugin opt-in (ADR-0013): plugins start off, the list shows the
 * instance's available plugins with the caller's choice, and a plugin the
 * caller has not turned on never receives their context. Every test uses a
 * fresh account, so no other test class's choices leak in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PluginSettingsEndpointTests extends ApiIntegrationTestSupport {

    private static final LocalDate WEEK = LocalDate.of(2026, 7, 6); // a Monday

    private static final StubPlugin stub;

    static {
        try {
            stub = StubPlugin.start();
        } catch (IOException exception) {
            throw new IllegalStateException("stub plugin failed to start", exception);
        }
    }

    @DynamicPropertySource
    static void pluginRegistrations(DynamicPropertyRegistry registry) {
        registry.add("mimos.plugins[0].url", stub::url);
        registry.add("mimos.plugins[0].timeout", () -> "500ms");
        // Registered but unreachable: not available, so never listed.
        registry.add("mimos.plugins[1].url", () -> "http://localhost:1");
        registry.add("mimos.plugins[1].timeout", () -> "500ms");
    }

    @AfterAll
    static void closeStub() {
        stub.close();
    }

    @LocalServerPort
    int port;

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void pluginsStartOffAndAnOffPluginSeesNothing() {
        String token = accessToken(createUser());

        JsonNode plugins = plugins(token);
        assertThat(plugins).hasSize(1);
        assertThat(plugins.get(0).get("id").asText()).isEqualTo("stub-plugin");
        assertThat(plugins.get(0).get("name").asText()).isEqualTo("Stub Plugin");
        assertThat(plugins.get(0).get("enabled").asBoolean()).isFalse();

        int callsBefore = stub.suggestionCalls();
        assertThat(suggestions(token)).isEmpty();
        assertThat(stub.suggestionCalls())
                .as("a plugin the user has not turned on never receives their context")
                .isEqualTo(callsBefore);
    }

    @Test
    void turningAPluginOnAndOffDecidesWhetherItIsAsked() {
        String token = accessToken(createUser());

        assertThat(update(token, "stub-plugin", "{\"enabled\":true}")).isEqualTo(204);
        assertThat(plugins(token).get(0).get("enabled").asBoolean()).isTrue();
        // Turning it on twice is the same as once.
        assertThat(update(token, "stub-plugin", "{\"enabled\":true}")).isEqualTo(204);
        int callsBefore = stub.suggestionCalls();
        suggestions(token);
        assertThat(stub.suggestionCalls()).isEqualTo(callsBefore + 1);

        assertThat(update(token, "stub-plugin", "{\"enabled\":false}")).isEqualTo(204);
        assertThat(plugins(token).get(0).get("enabled").asBoolean()).isFalse();
        suggestions(token);
        assertThat(stub.suggestionCalls()).isEqualTo(callsBefore + 1);
    }

    @Test
    void oneUsersChoiceIsTheirOwn() {
        String first = accessToken(createUser());
        String second = accessToken(createUser());

        update(first, "stub-plugin", "{\"enabled\":true}");

        assertThat(plugins(first).get(0).get("enabled").asBoolean()).isTrue();
        assertThat(plugins(second).get(0).get("enabled").asBoolean()).isFalse();
    }

    @Test
    void onlyAvailablePluginsCanBeTurnedOnButAnyCanBeTurnedOff() {
        String token = accessToken(createUser());

        assertThat(update(token, "no-such-plugin", "{\"enabled\":true}")).isEqualTo(404);
        assertThat(update(token, "no-such-plugin", "{\"enabled\":false}")).isEqualTo(204);
    }

    @Test
    void theSettingIsRequired() {
        String token = accessToken(createUser());

        assertThat(update(token, "stub-plugin", "{}")).isEqualTo(400);
        assertThat(update(token, "stub-plugin", "{\"enabled\":null}")).isEqualTo(400);
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        api().get().uri("/api/v1/me/plugins").exchange((request, response) -> {
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            return null;
        });
    }

    private JsonNode plugins(String token) {
        return requireNonNull(
                api().get()
                        .uri("/api/v1/me/plugins")
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "plugins returned no body");
    }

    private JsonNode suggestions(String token) {
        JsonNode body = requireNonNull(
                api().get()
                        .uri("/api/v1/plans/{start}/suggestions", WEEK.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "suggestions returned no body");
        return body.get("suggestions");
    }

    private int update(String token, String pluginId, String body) {
        return api().put()
                .uri("/api/v1/me/plugins/{id}", pluginId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
