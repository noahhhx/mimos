package io.github.noahhhx.mimos.api.instance;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** The instance endpoint answers without a token, so an app can check versions before sign-in. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicInstanceEndpointTests extends ApiIntegrationTestSupport {

    @LocalServerPort
    int port;

    @Test
    void answersVersionsWithoutAToken() {
        var response = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/v1/public/instance")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(JsonNode.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = requireNonNull(response.getBody());
        assertThat(body.get("version").asString()).matches("\\d+\\.\\d+\\.\\d+");
        assertThat(body.get("minAppVersion").asString()).isEqualTo("0.0.0");
    }

    @Test
    void dropsTheBuildQualifier() {
        assertThat(PublicInstanceController.release("0.1.0-SNAPSHOT")).isEqualTo("0.1.0");
        assertThat(PublicInstanceController.release("1.2.3")).isEqualTo("1.2.3");
        assertThat(PublicInstanceController.release("1.2.3+20260101")).isEqualTo("1.2.3");
        assertThat(PublicInstanceController.release("1.2.3-rc.1+abc")).isEqualTo("1.2.3");
    }
}
