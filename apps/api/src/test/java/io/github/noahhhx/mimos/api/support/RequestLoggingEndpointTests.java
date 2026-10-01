package io.github.noahhhx.mimos.api.support;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Request IDs and access logging through the real filter and security
 * chains: every response carries {@code X-Request-Id}, browsers may send and
 * read it, and a failing request leaves a log line naming its cause.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingEndpointTests extends ApiIntegrationTestSupport {

    private static final String WEB_ORIGIN = "http://localhost:3000";

    @LocalServerPort
    int port;

    @Test
    void generatesAnIdForRequestsWithoutOne() {
        String id = requestIdOf(api().get().uri("/api/v1/public/recipes").exchange((req, res) -> {
            assertThat(res.getStatusCode().value()).isEqualTo(200);
            return res.getHeaders();
        }));

        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void echoesASuppliedIdAndReplacesAMalformedOne() {
        assertThat(requestIdFor("browser-id-123")).isEqualTo("browser-id-123");
        assertThat(requestIdFor("not a valid id"))
                .isNotEqualTo("not a valid id")
                .hasSize(36);
    }

    @Test
    void unsupportedMediaTypeLeavesAnErrorLineWithTheRequestId(CapturedOutput output) {
        String id = "media-type-" + UUID.randomUUID();

        api().post()
                .uri("/api/v1/recipes")
                .headers(headers -> {
                    headers.setBearerAuth(accessToken());
                    headers.set(RequestLoggingFilter.HEADER, id);
                })
                .contentType(MediaType.TEXT_PLAIN)
                .body("{}")
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(415);
                    assertThat(res.getHeaders().getFirst(RequestLoggingFilter.HEADER))
                            .isEqualTo(id);
                    return null;
                });

        assertThat(output.getOut().lines().filter(line -> line.contains(id)))
                .anySatisfy(line -> assertThat(line)
                        .contains("WARN")
                        .contains("POST /api/v1/recipes -> 415")
                        .contains("content-type=text/plain")
                        .contains("exception=HttpMediaTypeNotSupportedException: Content-Type 'text/plain"));
    }

    @Test
    void securityChainRejectionsAreLoggedWithTheirCause(CapturedOutput output) {
        String id = "anonymous-" + UUID.randomUUID();

        api().get().uri("/api/v1/me").header(RequestLoggingFilter.HEADER, id).exchange((req, res) -> {
            assertThat(res.getStatusCode().value()).isEqualTo(401);
            assertThat(res.getHeaders().getFirst(RequestLoggingFilter.HEADER)).isEqualTo(id);
            return null;
        });

        assertThat(output.getOut().lines().filter(line -> line.contains(id)))
                .anySatisfy(line -> assertThat(line)
                        .contains("GET /api/v1/me -> 401")
                        .contains("exception=InsufficientAuthenticationException"));
    }

    @Test
    void browsersMaySendAndReadTheRequestId() {
        api().method(HttpMethod.OPTIONS)
                .uri("/api/v1/me")
                .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,x-request-id")
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(200);
                    assertThat(res.getHeaders().getAccessControlAllowHeaders())
                            .map(String::toLowerCase)
                            .contains("x-request-id");
                    return null;
                });

        api().get()
                .uri("/api/v1/public/recipes")
                .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                .exchange((req, res) -> {
                    assertThat(res.getHeaders().getAccessControlExposeHeaders()).contains(RequestLoggingFilter.HEADER);
                    return null;
                });
    }

    private String requestIdFor(String supplied) {
        return requestIdOf(api().get()
                .uri("/api/v1/public/recipes")
                .header(RequestLoggingFilter.HEADER, supplied)
                .exchange((req, res) -> res.getHeaders()));
    }

    private static String requestIdOf(@Nullable HttpHeaders headers) {
        return requireNonNull(
                requireNonNull(headers, "no response headers").getFirst(RequestLoggingFilter.HEADER),
                "no X-Request-Id on the response");
    }

    private RestClient api() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }
}
