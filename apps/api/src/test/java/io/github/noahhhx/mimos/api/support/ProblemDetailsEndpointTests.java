package io.github.noahhhx.mimos.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Every error the API answers is RFC 9457 problem-details — including the
 * ones Spring MVC raises before a handler runs (the recipe 415's response
 * was Spring's default error JSON; docs/harness/step-7-recipe-415.md) and
 * unexpected exceptions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsEndpointTests extends ApiIntegrationTestSupport {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    int port;

    @Test
    void unsupportedMediaType() {
        JsonNode problem = problemOf(
                HttpMethod.POST,
                "/api/v1/recipes",
                415,
                spec -> spec.contentType(MediaType.TEXT_PLAIN).body("{}"));

        assertThat(problem.get("detail").asText()).contains("text/plain");
    }

    @Test
    void missingContentType() {
        // What the web app sent before the step 7 fix: a body with no media type.
        problemOf(HttpMethod.POST, "/api/v1/recipes", 415, spec -> spec.body(new byte[] {'{', '}'}));
    }

    @Test
    void unsupportedMethod() {
        problemOf(HttpMethod.DELETE, "/api/v1/me", 405, spec -> {});
    }

    @Test
    void unknownPath() {
        problemOf(HttpMethod.GET, "/api/v1/no-such-thing", 404, spec -> {});
    }

    @Test
    void malformedPathParameter() {
        JsonNode problem = problemOf(HttpMethod.GET, "/api/v1/recipes/not-a-uuid", 400, spec -> {});

        assertThat(problem.get("title").asText()).isEqualTo("Invalid request");
    }

    @Test
    void unexpectedExceptionIsA500WithoutInternals(CapturedOutput output) {
        String id = "boom-" + UUID.randomUUID();

        JsonNode problem = problemOf(
                HttpMethod.GET, "/api/v1/test/boom", 500, spec -> spec.header(RequestLoggingFilter.HEADER, id));

        assertThat(problem.get("title").asText()).isEqualTo("Internal error");
        assertThat(problem.toString()).doesNotContain("secret internals");
        assertThat(output.getOut())
                .contains("Unhandled exception")
                .contains("java.lang.IllegalStateException: secret internals")
                .contains("at io.github.noahhhx.mimos.api.support.ProblemDetailsEndpointTests$Boom.boom");
        assertThat(output.getOut().lines().filter(line -> line.contains(id)))
                .anySatisfy(line -> assertThat(line)
                        .contains("ERROR")
                        .contains("GET /api/v1/test/boom -> 500")
                        .contains("exception=IllegalStateException: secret internals"));
    }

    /** Sends an authenticated request and asserts a problem-details answer with {@code status}. */
    private JsonNode problemOf(
            HttpMethod method, String path, int status, Consumer<RestClient.RequestBodySpec> request) {
        RestClient.RequestBodySpec spec = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build()
                .method(method)
                .uri(path)
                .headers(headers -> headers.setBearerAuth(accessToken()));
        request.accept(spec);
        return spec.exchange((req, res) -> {
            assertThat(res.getStatusCode().value()).isEqualTo(status);
            assertThat(res.getHeaders().getContentType())
                    .isNotNull()
                    .matches(ct -> ct.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            JsonNode problem = objectMapper.readTree(res.getBody().readAllBytes());
            assertThat(problem.get("status").asInt()).isEqualTo(status);
            assertThat(problem.get("title").asText()).isNotBlank();
            assertThat(problem.get("instance").asText()).isEqualTo(path.replaceFirst("\\?.*", ""));
            return problem;
        });
    }

    @TestConfiguration
    static class BoomConfiguration {
        @Bean
        Boom boom() {
            return new Boom();
        }
    }

    @RestController
    static class Boom {
        @GetMapping("/api/v1/test/boom")
        String boom() {
            throw new IllegalStateException("secret internals");
        }
    }
}
