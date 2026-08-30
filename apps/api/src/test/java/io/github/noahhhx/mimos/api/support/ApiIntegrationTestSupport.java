package io.github.noahhhx.mimos.api.support;

import static java.util.Objects.requireNonNull;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

/**
 * Boots the API against real Postgres and Keycloak containers, importing the
 * realm from {@code deploy/keycloak/mimos-realm.json}. Containers are
 * singletons: started once per test JVM and shared across test classes.
 */
public abstract class ApiIntegrationTestSupport {

    private static final DockerImageName KEYCLOAK_IMAGE = DockerImageName.parse("quay.io/keycloak/keycloak:26.5");

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    static final KeycloakContainer keycloak =
            new KeycloakContainer(KEYCLOAK_IMAGE).withRealmImportFile("mimos-realm.json");

    static {
        postgres.start();
        keycloak.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // The container's mapped port differs from the compose default, so the
        // issuer is derived from the container's actual URL.
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> keycloak.getAuthServerUrl() + "/realms/mimos");
    }

    /** Password grant against the real token endpoint (direct access grants are enabled on mimos-web). */
    protected static String accessToken() {
        return accessToken("test");
    }

    /** Token for a specific realm user (the realm ships `test` and `test2`). */
    protected static String accessToken(String username) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "mimos-web");
        form.add("username", username);
        form.add("password", "mimos-test");
        JsonNode response = RestClient.create()
                .post()
                .uri(keycloak.getAuthServerUrl() + "/realms/mimos/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        JsonNode token = requireNonNull(response, "token endpoint returned no body");
        return requireNonNull(token.get("access_token"), "token response missing access_token")
                .asText();
    }
}
