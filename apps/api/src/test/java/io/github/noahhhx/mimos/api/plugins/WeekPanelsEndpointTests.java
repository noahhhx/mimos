package io.github.noahhhx.mimos.api.plugins;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Week panels end to end over real stub plugins (ADR-0017): only plugins
 * the caller turned on are asked, panels arrive validated and attributed,
 * each plugin knows each user by its own stable pseudonym, and a pressed
 * button reaches only the plugin that drew it. Every test uses a fresh
 * account, so no other test's opt-ins or subjects leak in.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeekPanelsEndpointTests extends ApiIntegrationTestSupport {

    private static final LocalDate WEEK = LocalDate.of(2026, 9, 7); // a Monday

    private static final String PANEL = """
            {"summary":{"icon":"🇵🇪","label":"Peru"},
             "blocks":[
               {"type":"text","text":"Spin to pick a country."},
               {"type":"video","url":"https://example.com/dropped"},
               {"type":"highlight","icon":"🇵🇪","title":"Peru","text":"South America"},
               {"type":"wheel","segments":[{"label":"Peru","icon":"🇵🇪"},{"label":"Chile"}],"landing":0},
               {"type":"actions","actions":[
                 {"id":"choose","label":"Choose","value":"PE","primary":true},
                 {"id":"skip","label":"Skip"}]}]}
            """;

    private static final StubPlugin stub;
    private static final StubPlugin other;

    static {
        try {
            stub = StubPlugin.start();
            other = StubPlugin.start("other-plugin", "Other Plugin");
        } catch (IOException exception) {
            throw new IllegalStateException("stub plugin failed to start", exception);
        }
    }

    @DynamicPropertySource
    static void pluginRegistrations(DynamicPropertyRegistry registry) {
        registry.add("mimos.plugins[0].url", stub::url);
        registry.add("mimos.plugins[0].timeout", () -> "500ms");
        registry.add("mimos.plugins[1].url", other::url);
        registry.add("mimos.plugins[1].timeout", () -> "500ms");
        // Registered but unreachable: never available, never asked.
        registry.add("mimos.plugins[2].url", () -> "http://localhost:1");
        registry.add("mimos.plugins[2].timeout", () -> "500ms");
    }

    @AfterAll
    static void closeStubs() {
        stub.close();
        other.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void resetStubs() {
        for (StubPlugin plugin : List.of(stub, other)) {
            plugin.weekPanelJson = "{\"blocks\":[]}";
            plugin.weekPanelStatus = 200;
        }
    }

    @Test
    void aPluginRendersAPanelOnlyOnceTheCallerTurnsItOn() {
        String token = accessToken(createUser());
        stub.weekPanelJson = PANEL;
        int requestsBefore = stub.weekPanelRequests().size();

        assertThat(panels(token, WEEK)).isEmpty();
        assertThat(stub.weekPanelRequests())
                .as("a plugin the user has not turned on never hears of them")
                .hasSize(requestsBefore);

        turn(token, "stub-plugin", true);
        JsonNode panels = panels(token, WEEK);

        assertThat(panels).hasSize(1);
        JsonNode panel = panels.get(0);
        assertThat(panel.get("pluginId").asText()).isEqualTo("stub-plugin");
        assertThat(panel.get("pluginName").asText()).isEqualTo("Stub Plugin");
        assertThat(panel.get("summary")).isEqualTo(json("{\"icon\":\"🇵🇪\",\"label\":\"Peru\"}"));
        // The unknown block is dropped; the rest arrive with their type.
        assertThat(panel.get("blocks")).isEqualTo(json("""
                [{"type":"text","text":"Spin to pick a country."},
                 {"type":"highlight","icon":"🇵🇪","title":"Peru","text":"South America"},
                 {"type":"wheel","segments":[{"label":"Peru","icon":"🇵🇪"},{"label":"Chile"}],"landing":0},
                 {"type":"actions","actions":[
                   {"id":"choose","label":"Choose","value":"PE","primary":true},
                   {"id":"skip","label":"Skip","primary":false}]}]
                """));

        JsonNode request = lastRequest(stub);
        assertThat(request.get("weekStartDate").asText()).isEqualTo(WEEK.toString());
        assertThat(request.has("action")).as("a render carries no action").isFalse();
    }

    @Test
    void eachPluginKnowsEachUserByItsOwnStablePseudonym() {
        String first = accessToken(createUser());
        String second = accessToken(createUser());
        for (String token : List.of(first, second)) {
            turn(token, "stub-plugin", true);
            turn(token, "other-plugin", true);
        }

        panels(first, WEEK);
        UUID firstAtStub = subject(lastRequest(stub));
        UUID firstAtOther = subject(lastRequest(other));
        panels(first, WEEK.plusWeeks(1));
        assertThat(subject(lastRequest(stub))).as("stable across calls").isEqualTo(firstAtStub);

        turn(first, "stub-plugin", false);
        turn(first, "stub-plugin", true);
        panels(first, WEEK);
        assertThat(subject(lastRequest(stub)))
                .as("kept across turning it off and on")
                .isEqualTo(firstAtStub);

        panels(second, WEEK);
        assertThat(subject(lastRequest(stub))).as("differs per user").isNotEqualTo(firstAtStub);
        assertThat(firstAtOther).as("differs per plugin").isNotEqualTo(firstAtStub);

        // Suggestions name the user to the same plugin by the same subject.
        api().get()
                .uri("/api/v1/plans/{start}/suggestions", WEEK.toString())
                .headers(headers -> headers.setBearerAuth(first))
                .retrieve()
                .toBodilessEntity();
        JsonNode context = json(requireNonNull(stub.lastContext(), "the stub saw no suggestion context"));
        assertThat(subject(context)).isEqualTo(firstAtStub);
    }

    @Test
    void aPressedButtonReachesThePluginAndReturnsItsPanel() {
        String token = accessToken(createUser());
        turn(token, "stub-plugin", true);
        panels(token, WEEK);
        UUID subject = subject(lastRequest(stub));
        stub.weekPanelJson = PANEL;

        Response response = press(token, WEEK, "stub-plugin", "{\"id\":\"choose\",\"value\":\"PE\"}");

        assertThat(response.status()).isEqualTo(200);
        JsonNode panel = requireNonNull(response.body());
        assertThat(panel.get("pluginId").asText()).isEqualTo("stub-plugin");
        assertThat(panel.get("summary").get("label").asText()).isEqualTo("Peru");
        assertThat(panel.get("blocks")).hasSize(4);
        JsonNode request = lastRequest(stub);
        assertThat(subject(request)).isEqualTo(subject);
        assertThat(request.get("weekStartDate").asText()).isEqualTo(WEEK.toString());
        assertThat(request.get("action")).isEqualTo(json("{\"id\":\"choose\",\"value\":\"PE\"}"));
    }

    @Test
    void aButtonForAPluginTheCallerHasNotTurnedOnIsNotFound() {
        String token = accessToken(createUser());
        turn(token, "other-plugin", true);
        int stubRequestsBefore = stub.weekPanelRequests().size();
        int otherRequestsBefore = other.weekPanelRequests().size();

        assertThat(press(token, WEEK, "stub-plugin", "{\"id\":\"choose\"}").status())
                .isEqualTo(404);
        assertThat(press(token, WEEK, "no-such-plugin", "{\"id\":\"choose\"}").status())
                .isEqualTo(404);
        assertThat(stub.weekPanelRequests()).hasSize(stubRequestsBefore);
        assertThat(other.weekPanelRequests())
                .as("a press for one plugin never reaches another")
                .hasSize(otherRequestsBefore);
    }

    @Test
    void aMalformedButtonPressIsABadRequest() {
        String token = accessToken(createUser());
        turn(token, "stub-plugin", true);
        int requestsBefore = stub.weekPanelRequests().size();

        assertThat(press(token, WEEK, "stub-plugin", "{\"id\":\"Not An Id\"}").status())
                .isEqualTo(400);
        assertThat(press(token, WEEK, "stub-plugin", "{}").status()).isEqualTo(400);
        assertThat(press(token, WEEK, "stub-plugin", "{\"id\":\"choose\",\"value\":\"" + "x".repeat(101) + "\"}")
                        .status())
                .isEqualTo(400);
        assertThat(press(token, WEEK.plusDays(1), "stub-plugin", "{\"id\":\"choose\"}")
                        .status())
                .isEqualTo(400);
        assertThat(stub.weekPanelRequests()).hasSize(requestsBefore);
    }

    @Test
    void aFailingPluginHidesItsPanelButFailsAPressLoudly() {
        String token = accessToken(createUser());
        turn(token, "stub-plugin", true);
        turn(token, "other-plugin", true);
        other.weekPanelJson = PANEL;
        stub.weekPanelStatus = 500;

        JsonNode panels = panels(token, WEEK);
        assertThat(panels).hasSize(1);
        assertThat(panels.get(0).get("pluginId").asText()).isEqualTo("other-plugin");

        Response response = press(token, WEEK, "stub-plugin", "{\"id\":\"choose\"}");
        assertThat(response.status()).isEqualTo(502);
        JsonNode problem = requireNonNull(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(502);
        assertThat(problem.get("detail").asText()).isEqualTo("Stub Plugin did not answer. Try again.");

        stub.weekPanelJson = "[\"not a panel\"]";
        stub.weekPanelStatus = 200;
        assertThat(press(token, WEEK, "stub-plugin", "{\"id\":\"choose\"}").status())
                .as("a malformed answer is a failure too")
                .isEqualTo(502);
    }

    private RestClient api() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    private JsonNode json(String text) {
        return requireNonNull(objectMapper.readTree(text));
    }

    private static UUID subject(JsonNode request) {
        return UUID.fromString(request.get("subject").asText());
    }

    private JsonNode lastRequest(StubPlugin plugin) {
        List<String> requests = plugin.weekPanelRequests();
        assertThat(requests).as("the plugin was asked for a panel").isNotEmpty();
        return json(requests.get(requests.size() - 1));
    }

    private JsonNode panels(String token, LocalDate week) {
        JsonNode body = requireNonNull(
                api().get()
                        .uri("/api/v1/plans/{start}/panels", week.toString())
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .body(JsonNode.class),
                "panels returned no body");
        return body.get("panels");
    }

    private record Response(int status, @Nullable JsonNode body) {}

    private Response press(String token, LocalDate week, String pluginId, String body) {
        return api().post()
                .uri("/api/v1/plans/{start}/panels/{pluginId}/actions", week.toString(), pluginId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) ->
                        new Response(response.getStatusCode().value(), objectMapper.readTree(response.getBody())));
    }

    private void turn(String token, String pluginId, boolean enabled) {
        api().put()
                .uri("/api/v1/me/plugins/{id}", pluginId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"enabled\":" + enabled + "}")
                .retrieve()
                .toBodilessEntity();
    }
}
