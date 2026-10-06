package io.github.noahhhx.mimos.api.support;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A fresh realm user, signed in, with their profile created: for tests
 * that change households (ADR-0019), which must leave `test` and `test2`
 * alone.
 */
public class TestUser {

    public final String username = ApiIntegrationTestSupport.createUser();
    public final String token = ApiIntegrationTestSupport.accessToken(username);
    public final RestClient api;
    public final UUID profileId;

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public TestUser(int port, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.api = RestClient.builder().baseUrl("http://localhost:" + port).build();
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.profileId =
                UUID.fromString(ok(HttpMethod.GET, "/api/v1/me", null).get("id").asText());
    }

    public UUID household() {
        return requireNonNull(
                jdbc.queryForObject("select household_id from user_profile where id = ?", UUID.class, profileId));
    }

    public String invite() {
        return ok(HttpMethod.POST, "/api/v1/household/invites", null)
                .get("token")
                .asText();
    }

    public void join(TestUser host) {
        ok(HttpMethod.POST, "/api/v1/household/join", Map.of("token", host.invite()));
    }

    public UUID recipe(String title) {
        return UUID.fromString(TestRecipes.create(api, token, title).get("id").asText());
    }

    public JsonNode ok(HttpMethod method, String uri, @Nullable Object body) {
        Response response = send(method, uri, body);
        assertThat(response.status())
                .as(method + " " + uri + " answered " + response.body())
                .isBetween(200, 299);
        return requireNonNull(response.body(), method + " " + uri + " returned no body");
    }

    public Response send(HttpMethod method, String uri, @Nullable Object body) {
        RestClient.RequestBodySpec request =
                api.method(method).uri(uri).headers(headers -> headers.setBearerAuth(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return requireNonNull(request.exchange((req, res) -> {
            byte[] bytes = res.getBody().readAllBytes();
            return new Response(res.getStatusCode().value(), bytes.length == 0 ? null : objectMapper.readTree(bytes));
        }));
    }

    public int status(HttpMethod method, String uri, @Nullable Object body) {
        return send(method, uri, body).status();
    }

    public record Response(int status, @Nullable JsonNode body) {}
}
