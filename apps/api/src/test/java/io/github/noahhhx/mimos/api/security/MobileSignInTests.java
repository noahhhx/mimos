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
 * The {@code mimos-mobile} Keycloak client (ADR-0029) as the Android app uses
 * it: Authorization Code with PKCE and the custom-scheme redirect, a refresh
 * token that renews, a session that ends through the end-session endpoint,
 * and the resulting token accepted by the API. Runs against the realm export
 * in {@code deploy/keycloak}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MobileSignInTests extends ApiIntegrationTestSupport {

    private static final String CLIENT_ID = "mimos-mobile";
    private static final String REDIRECT_URI = "io.github.noahhhx.mimos.oauth:/oauth2redirect";
    private static final Pattern FORM_ACTION =
            Pattern.compile("<form[^>]*id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"");

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void theAppSignsInWithPkceAndItsTokenWorksOnTheApi() throws Exception {
        JsonNode tokens = signIn();

        HttpResponse<String> me = http().send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                                .header(
                                        "Authorization",
                                        "Bearer " + tokens.get("access_token").asString())
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(me.body()).get("displayName").asString())
                .isEqualTo("test");
    }

    @Test
    void theRefreshTokenRenewsTheSessionAndEndSessionRevokesIt() throws Exception {
        JsonNode tokens = signIn();
        String refreshToken = tokens.get("refresh_token").asString();
        assertThat(tokens.has("id_token")).isTrue();

        HttpResponse<String> renewed = http().send(
                        tokenRequest(Map.of(
                                "grant_type", "refresh_token",
                                "client_id", CLIENT_ID,
                                "refresh_token", refreshToken)),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(renewed.statusCode()).as(renewed.body()).isEqualTo(200);
        String renewedRefreshToken =
                objectMapper.readTree(renewed.body()).get("refresh_token").asString();

        URI logout = UriComponentsBuilder.fromUriString(realmUrl() + "/protocol/openid-connect/logout")
                .queryParam("id_token_hint", tokens.get("id_token").asString())
                .queryParam("post_logout_redirect_uri", REDIRECT_URI)
                .encode()
                .build()
                .toUri();
        HttpResponse<String> ended =
                http().send(HttpRequest.newBuilder(logout).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(ended.statusCode()).isEqualTo(302);
        assertThat(ended.headers().firstValue("Location").orElseThrow()).startsWith(REDIRECT_URI);

        HttpResponse<String> afterwards = http().send(
                        tokenRequest(Map.of(
                                "grant_type", "refresh_token",
                                "client_id", CLIENT_ID,
                                "refresh_token", renewedRefreshToken)),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(afterwards.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(afterwards.body()).get("error").asString())
                .isEqualTo("invalid_grant");
    }

    @Test
    void otherRedirectUrisAreRefused() throws Exception {
        for (String redirectUri : new String[] {
            "io.github.noahhhx.mimos:/oauth2redirect",
            "io.github.noahhhx.mimos.oauth:/elsewhere",
            "http://localhost/callback",
            "https://example.com/callback"
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
    void pkceIsRequiredAndPasswordGrantsAreOff() throws Exception {
        URI withoutPkce = UriComponentsBuilder.fromUri(authorizeUri(REDIRECT_URI, challenge(verifier())))
                .replaceQueryParam("code_challenge")
                .replaceQueryParam("code_challenge_method")
                .build(true)
                .toUri();
        HttpResponse<String> refused =
                http().send(HttpRequest.newBuilder(withoutPkce).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(refused.statusCode()).isEqualTo(302);
        assertThat(refused.headers().firstValue("Location").orElseThrow())
                .startsWith(REDIRECT_URI)
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

    /** Signs `test` in through the login form and returns the token response. */
    private JsonNode signIn() throws Exception {
        HttpClient browser = http();
        String verifier = verifier();

        HttpResponse<String> login = browser.send(
                HttpRequest.newBuilder(authorizeUri(REDIRECT_URI, challenge(verifier)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        Matcher action = FORM_ACTION.matcher(login.body());
        assertThat(action.find()).as("login form").isTrue();

        HttpResponse<String> callback = browser.send(
                HttpRequest.newBuilder(URI.create(action.group(1).replace("&amp;", "&")))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookies(login))
                        .POST(form(Map.of("username", "test", "password", "mimos-test")))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(302);
        String location = callback.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith(REDIRECT_URI + "?");
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
                        "redirect_uri", REDIRECT_URI,
                        "code_verifier", verifier)),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tokens.statusCode()).as(tokens.body()).isEqualTo(200);
        return objectMapper.readTree(tokens.body());
    }

    private static URI authorizeUri(String redirectUri, String challenge) {
        return UriComponentsBuilder.fromUriString(realmUrl() + "/protocol/openid-connect/auth")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", "mobile-state")
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
}
