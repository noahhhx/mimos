package io.github.noahhhx.mimos.openfoodfacts;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;

/**
 * Looks barcodes up in Open Food Facts (ADR-0025): one GET of
 * {@code /api/v2/product/{barcode}} per lookup, nothing stored. Exists only
 * while {@code mimos.open-food-facts.enabled} is on.
 */
public class OpenFoodFactsClient {

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** Open Food Facts asks every client to name itself and a way to reach its authors. */
    static final String USER_AGENT = "Mimos/" + version() + " (https://github.com/noahhhx/mimos)";

    private final RestClient restClient;

    OpenFoodFactsClient(URI baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    /**
     * The product with this barcode, or empty when Open Food Facts has none
     * or has one without a name.
     *
     * @throws OpenFoodFactsUnavailableException when it did not answer, or answered with an error
     */
    public Optional<FoodProduct> lookUp(Barcode barcode) {
        return product(barcode).flatMap(product -> OpenFoodFactsTranslator.translate(barcode, product));
    }

    private Optional<OffJson.Product> product(Barcode barcode) {
        try {
            return restClient
                    .get()
                    .uri(uri -> uri.path("/api/v2/product/{barcode}")
                            .queryParam("fields", OffJson.FIELDS)
                            .build(barcode.digits()))
                    .exchange((request, response) -> {
                        // An unknown barcode is a 404 with status 0, an invalid one a 200 with status 0.
                        boolean notFound = response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND);
                        if (!notFound && !response.getStatusCode().is2xxSuccessful()) {
                            throw new OpenFoodFactsUnavailableException("Open Food Facts answered "
                                    + response.getStatusCode().value());
                        }
                        OffJson.@Nullable Answer answer =
                                OffJson.MAPPER.readValue(response.getBody(), OffJson.Answer.class);
                        if (answer == null) {
                            throw new OpenFoodFactsUnavailableException("Open Food Facts answered with an empty body");
                        }
                        if (notFound || !Objects.equals(answer.status(), 1)) {
                            return Optional.<OffJson.Product>empty();
                        }
                        return Optional.ofNullable(answer.product());
                    });
        } catch (JacksonException e) {
            throw new OpenFoodFactsUnavailableException("Open Food Facts answered with unreadable JSON");
        } catch (RestClientException e) {
            throw new OpenFoodFactsUnavailableException("Open Food Facts did not answer");
        }
    }

    private static String version() {
        Properties properties = new Properties();
        try (InputStream in = OpenFoodFactsClient.class.getResourceAsStream("open-food-facts.properties")) {
            properties.load(Objects.requireNonNull(in, "open-food-facts.properties is missing"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties.getProperty("version", "unknown");
    }
}
