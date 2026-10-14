package io.github.noahhhx.mimos.api.food;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoodProductsEndpointTests extends ApiIntegrationTestSupport {

    private static final StubOpenFoodFacts openFoodFacts = new StubOpenFoodFacts();

    @DynamicPropertySource
    static void openFoodFacts(DynamicPropertyRegistry registry) {
        registry.add("mimos.open-food-facts.base-url", openFoodFacts::url);
    }

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @AfterEach
    void clearFault() {
        openFoodFacts.reset();
    }

    record Response(int status, @Nullable JsonNode body) {}

    private Response get(String barcode, @Nullable String token) {
        return requireNonNull(RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/v1/food-products/{barcode}", barcode)
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                })
                .exchange((request, response) -> {
                    byte[] bytes = response.getBody().readAllBytes();
                    return new Response(
                            response.getStatusCode().value(), bytes.length == 0 ? null : objectMapper.readTree(bytes));
                }));
    }

    private JsonNode product(String barcode) {
        Response response = get(barcode, accessToken());
        assertThat(response.status())
                .as("GET %s answered %s", barcode, response.body())
                .isEqualTo(200);
        return requireNonNull(response.body());
    }

    @Test
    void aSolidComesBackPer100Grams() {
        JsonNode nutella = product("3017620422003");

        assertThat(nutella.get("barcode").asText()).isEqualTo("3017620422003");
        assertThat(nutella.get("name").asText()).isEqualTo("Nutella");
        assertThat(nutella.get("brand").asText()).isEqualTo("Nutella");
        assertThat(nutella.get("basis").asText()).isEqualTo("PER_100_G");
        JsonNode nutrition = nutella.get("nutrition");
        assertThat(nutrition.get("calories").asDouble()).isEqualTo(539.0);
        assertThat(nutrition.get("proteinG").asDouble()).isEqualTo(6.3);
        assertThat(nutrition.get("carbsG").asDouble()).isEqualTo(57.5);
        assertThat(nutrition.get("fatG").asDouble()).isEqualTo(30.9);
        assertThat(nutella.has("servingSize"))
                .as("Open Food Facts gives no serving")
                .isFalse();
        assertThat(openFoodFacts.paths()).containsExactly("/api/v2/product/3017620422003");
    }

    @Test
    void aDrinkComesBackPer100MillilitresWithItsServing() {
        JsonNode cola = product("5449000000996");

        assertThat(cola.get("basis").asText()).isEqualTo("PER_100_ML");
        assertThat(cola.get("servingSize").asDouble()).isEqualTo(330.0);
        assertThat(cola.get("nutrition").get("fatG").asDouble())
                .as("a zero is known")
                .isEqualTo(0.0);
    }

    @Test
    void aMissingValueIsAbsent() {
        JsonNode marmite = product("50184453");

        assertThat(marmite.get("nutrition").has("fatG")).isFalse();
        assertThat(marmite.get("nutrition").get("calories").asDouble()).isEqualTo(260.0);
    }

    @Test
    void anUnknownBarcodeIsNotFound() {
        Response response = get("20000000", accessToken());

        assertThat(response.status()).isEqualTo(404);
        assertThat(requireNonNull(response.body()).get("detail").asText()).contains("20000000");
    }

    @Test
    void aProductWithNoNameIsNotFound() {
        assertThat(get("6111246950845", accessToken()).status()).isEqualTo(404);
    }

    @Test
    void anythingButEightToFourteenDigitsIsABadRequestAndNeverLeavesMimos() {
        for (String barcode : List.of("1234567", "123456789012345", "30176204x2003", " 3017620422003")) {
            Response response = get(barcode, accessToken());

            assertThat(response.status()).as(barcode).isEqualTo(400);
            assertThat(requireNonNull(response.body()).get("detail").asText())
                    .as(barcode)
                    .isEqualTo("A barcode is 8 to 14 digits");
        }
        assertThat(openFoodFacts.paths()).isEmpty();
    }

    @Test
    void anOpenFoodFactsErrorIsABadGateway() {
        openFoodFacts.force(503);

        Response response = get("3017620422003", accessToken());

        assertThat(response.status()).isEqualTo(502);
        assertThat(requireNonNull(response.body()).get("title").asText()).isEqualTo("Open Food Facts unavailable");
    }

    @Test
    void aLookupNeedsABearerToken() {
        assertThat(get("3017620422003", null).status()).isEqualTo(401);
        assertThat(openFoodFacts.paths()).isEmpty();
    }
}
