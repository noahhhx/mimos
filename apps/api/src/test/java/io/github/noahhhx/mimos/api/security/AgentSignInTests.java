package io.github.noahhhx.mimos.api.security;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The {@code mimos-agent} Keycloak client (ADR-0014) as an MCP client uses
 * it: Authorization Code with PKCE, a loopback callback on whatever port the
 * client picked, and the resulting token accepted by the API. Runs against
 * the realm export in {@code deploy/keycloak}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentSignInTests extends ApiIntegrationTestSupport {

    private static final String CLIENT_ID = "mimos-agent";
    private static final Pattern FORM_ACTION =
            Pattern.compile("<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void aLoopbackCallbackOnAnyPortSignsInAndTheTokenWorksOnTheApi() throws Exception {
        for (String redirectUri : new String[] {
            "http://localhost:" + freePortLike() + "/callback", "http://127.0.0.1:" + freePortLike() + "/callback"
        }) {
            String token = signIn(redirectUri);

            HttpResponse<String> me = http().send(
                            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                                    .header("Authorization", "Bearer " + token)
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(me.statusCode()).as(redirectUri).isEqualTo(200);
            assertThat(objectMapper.readTree(me.body()).get("displayName").asString())
                    .isEqualTo("test");
        }
    }

    @Test
    void unregisteredRedirectUrisAreRefused() throws Exception {
        for (String redirectUri : new String[] {
            "http://localhost:" + freePortLike() + "/elsewhere",
            "http://localhost.example.com/callback",
            "https://example.com/callback",
            "https://claude.ai/api/mcp/other"
        }) {
            HttpResponse<String> response = http().send(
                            HttpRequest.newBuilder(authorizeUri(redirectUri, challenge(verifier())))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(redirectUri).isEqualTo(400);
            assertThat(response.body()).as(redirectUri).contains("redirect_uri");
        }
    }

    @Test
    void claudeAiConnectorCallbackIsRegistered() throws Exception {
        HttpResponse<String> response = http().send(
                        HttpRequest.newBuilder(
                                        authorizeUri("https://claude.ai/api/mcp/auth_callback", challenge(verifier())))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(FORM_ACTION.matcher(response.body()).find()).isTrue();
    }

    @Test
    void pkceIsRequiredAndPasswordGrantsAreOff() throws Exception {
        String redirectUri = "http://localhost:" + freePortLike() + "/callback";
        URI withoutPkce = UriComponentsBuilder.fromUri(authorizeUri(redirectUri, challenge(verifier())))
                .replaceQueryParam("code_challenge")
                .replaceQueryParam("code_challenge_method")
                .build(true)
                .toUri();
        HttpResponse<String> refused =
                http().send(HttpRequest.newBuilder(withoutPkce).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(refused.statusCode()).isEqualTo(302);
        assertThat(refused.headers().firstValue("Location").orElseThrow())
                .startsWith(redirectUri)
                .contains("error=invalid_request");

        HttpResponse<String> password = http().send(
                        tokenRequest(Map.of(
                                "grant_type", "password",
                                "client_id", CLIENT_ID,
                                "username", "test",
                                "password", "mimos-test")),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(password.statusCode()).isIn(400, 401);
        assertThat(objectMapper.readTree(password.body()).get("error").asString())
                .isEqualTo("unauthorized_client");
    }

    /** Signs `test` in through the login form and returns the access token. */
    private String signIn(String redirectUri) throws Exception {
        HttpClient browser = http();
        String verifier = verifier();

        HttpResponse<String> login = browser.send(
                HttpRequest.newBuilder(authorizeUri(redirectUri, challenge(verifier)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).as(redirectUri).isEqualTo(200);
        Matcher action = FORM_ACTION.matcher(login.body());
        assertThat(action.find()).as("login form for " + redirectUri).isTrue();

        HttpResponse<String> callback = browser.send(
                HttpRequest.newBuilder(URI.create(action.group(1).replace("&amp;", "&")))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookies(login))
                        .POST(form(Map.of("username", "test", "password", "mimos-test")))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(callback.statusCode())
                .as(login.headers().allValues("Set-Cookie") + " " + callback.body())
                .isEqualTo(302);
        String location = callback.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith(redirectUri + "?");
        String code = requireNonNull(
                UriComponentsBuilder.fromUriString(location)
                        .build()
                        .getQueryParams()
                        .getFirst("code"),
                "no code in " + location);

        HttpResponse<String> tokens = browser.send(
                tokenRequest(Map.of(
                        "grant_type", "authorization_code",
                        "client_id", CLIENT_ID,
                        "code", code,
                        "redirect_uri", redirectUri,
                        "code_verifier", verifier)),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tokens.statusCode()).as(tokens.body()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(tokens.body());
        return body.get("access_token").asString();
    }

    private static URI authorizeUri(String redirectUri, String challenge) {
        return UriComponentsBuilder.fromUriString(realmUrl() + "/protocol/openid-connect/auth")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("response_type", "code")
                // What Claude Code asks for: a refresh token that outlives the browser session.
                .queryParam("scope", "openid offline_access")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", "agent-state")
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .encode()
                .build()
                .toUri();
    }

    private static HttpRequest tokenRequest(Map<String, String> fields) {
        return HttpRequest.newBuilder(URI.create(realmUrl() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(form(fields))
                .build();
    }

    private static HttpRequest.BodyPublisher form(Map<String, String> fields) {
        return HttpRequest.BodyPublishers.ofString(new LinkedHashMap<>(fields)
                .entrySet().stream()
                        .map(field -> URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "="
                                + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&")));
    }

    private static HttpClient http() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * The login session's cookies, sent by hand: Keycloak marks them
     * {@code Secure}, so Java's cookie store keeps them off plain-HTTP
     * requests to the test container.
     */
    private static String cookies(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .collect(Collectors.joining("; "));
    }

    private static String verifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /** An ephemeral-range port, as MCP clients pick for their callback listener. */
    private static int freePortLike() {
        return 49152 + new SecureRandom().nextInt(16000);
    }
}
