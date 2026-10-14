package io.github.noahhhx.mimos.openfoodfacts;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The parts of Open Food Facts' {@code /api/v2/product/{barcode}} answer
 * Mimos reads. Every field may be missing, and numbers sometimes arrive as
 * strings, so numeric fields stay {@link JsonNode}s for the translator to
 * read.
 */
final class OffJson {

    static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** The fields the client asks for; Open Food Facts leaves the rest out. */
    static final String FIELDS = String.join(
            ",",
            "product_name",
            "generic_name",
            "brands",
            "nutrition_data_per",
            "product_quantity",
            "product_quantity_unit",
            "serving_quantity",
            "serving_quantity_unit",
            "nutriments");

    private OffJson() {}

    /** {@code status} is 1 for a product found and 0 otherwise, whatever the HTTP status. */
    record Answer(@Nullable Integer status, @Nullable Product product) {}

    record Product(
            @JsonProperty("product_name") @Nullable String productName,
            @JsonProperty("generic_name") @Nullable String genericName,
            @Nullable String brands,
            @JsonProperty("nutrition_data_per") @Nullable String nutritionDataPer,
            @JsonProperty("product_quantity_unit") @Nullable String productQuantityUnit,
            @JsonProperty("serving_quantity") @Nullable JsonNode servingQuantity,
            @JsonProperty("serving_quantity_unit") @Nullable String servingQuantityUnit,
            @Nullable JsonNode nutriments) {}
}
