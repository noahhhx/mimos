package io.github.noahhhx.mimos.openfoodfacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class OpenFoodFactsClientTests {

    private static final Barcode NUTELLA = new Barcode("3017620422003");

    private StubOpenFoodFacts stub;
    private OpenFoodFactsClient client;

    @BeforeEach
    void start() throws IOException {
        stub = StubOpenFoodFacts.start();
        client = new OpenFoodFactsClient(stub.url());
    }

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void asksForOnlyTheFieldsItReadsAndNamesItself() {
        stub.answer(200, Fixtures.bytes("nutella.json"));

        assertThat(client.lookUp(NUTELLA))
                .hasValueSatisfying(product -> assertThat(product.name()).isEqualTo("Nutella"));

        StubOpenFoodFacts.Request request = Objects.requireNonNull(stub.lastRequest(), "the stub saw no request");
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().getPath()).isEqualTo("/api/v2/product/3017620422003");
        assertThat(request.uri().getQuery())
                .isEqualTo("fields=product_name,generic_name,brands,nutrition_data_per,product_quantity,"
                        + "product_quantity_unit,serving_quantity,serving_quantity_unit,nutriments");
        assertThat(request.userAgent())
                .matches("Mimos/\\d+\\.\\d+\\.\\d+(-SNAPSHOT)? \\(https://github\\.com/noahhhx/mimos\\)");
    }

    @Test
    void anUnknownBarcodeIsNotFound() {
        stub.answer(404, Fixtures.bytes("not-found.json"));

        assertThat(client.lookUp(new Barcode("20000000"))).isEmpty();
    }

    @Test
    void anInvalidBarcodeIsNotFound() {
        stub.answer(200, Fixtures.bytes("invalid-code.json"));

        assertThat(client.lookUp(new Barcode("0000000000017"))).isEmpty();
    }

    @Test
    void aProductWithNoNameIsNotFound() {
        stub.answer(200, Fixtures.bytes("nameless.json"));

        assertThat(client.lookUp(new Barcode("6111246950845"))).isEmpty();
    }

    @Test
    void aServerErrorIsUnavailable() {
        stub.answer(503, new byte[0]);

        assertThatThrownBy(() -> client.lookUp(NUTELLA))
                .isInstanceOf(OpenFoodFactsUnavailableException.class)
                .hasMessage("Open Food Facts answered 503");
    }

    @Test
    void aRateLimitIsUnavailable() {
        stub.answer(429, "<html>429 Too Many Requests</html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client.lookUp(NUTELLA)).isInstanceOf(OpenFoodFactsUnavailableException.class);
    }

    @Test
    void unreadableJsonIsUnavailable() {
        stub.answer(200, "<html>maintenance</html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client.lookUp(NUTELLA)).isInstanceOf(OpenFoodFactsUnavailableException.class);
    }

    @Test
    void anEmptyAnswerIsUnavailable() {
        stub.answer(200, new byte[0]);

        assertThatThrownBy(() -> client.lookUp(NUTELLA)).isInstanceOf(OpenFoodFactsUnavailableException.class);
    }

    @Test
    void noAnswerIsUnavailable() {
        OpenFoodFactsClient unreachable = new OpenFoodFactsClient(URI.create("http://localhost:1"));

        assertThatThrownBy(() -> unreachable.lookUp(NUTELLA))
                .isInstanceOf(OpenFoodFactsUnavailableException.class)
                .hasMessage("Open Food Facts did not answer");
    }
}
