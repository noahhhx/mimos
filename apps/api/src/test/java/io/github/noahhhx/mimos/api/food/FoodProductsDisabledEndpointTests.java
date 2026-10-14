package io.github.noahhhx.mimos.api.food;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.api.support.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mimos.open-food-facts.enabled=false")
class FoodProductsDisabledEndpointTests extends ApiIntegrationTestSupport {

    private static final StubOpenFoodFacts openFoodFacts = new StubOpenFoodFacts();

    @DynamicPropertySource
    static void openFoodFacts(DynamicPropertyRegistry registry) {
        registry.add("mimos.open-food-facts.base-url", openFoodFacts::url);
    }

    @LocalServerPort
    int port;

    @Test
    void turnedOffEveryLookupIsNotFoundAndNoBarcodeLeavesMimos() {
        int status = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/v1/food-products/3017620422003")
                .headers(headers -> headers.setBearerAuth(accessToken()))
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(404);
        assertThat(openFoodFacts.paths()).isEmpty();
    }
}
