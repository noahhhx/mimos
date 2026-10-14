package io.github.noahhhx.mimos.openfoodfacts;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Open Food Facts' product, read into a {@link FoodProduct} (ADR-0025).
 * Nutrition comes from the {@code _100g} nutriments only, which Open Food
 * Facts keeps per 100 ml for liquids; its estimates are never used. A value
 * that is missing, negative, or not a number is absent.
 */
final class OpenFoodFactsTranslator {

    static final double KJ_PER_KCAL = 4.184;

    private OpenFoodFactsTranslator() {}

    /** Empty when the product has no name at all: Mimos cannot show it. */
    static Optional<FoodProduct> translate(Barcode barcode, OffJson.Product product) {
        String name = text(product.productName());
        if (name == null) {
            name = text(product.genericName());
        }
        if (name == null) {
            return Optional.empty();
        }
        FoodProduct.Basis basis = basis(product);
        return Optional.of(new FoodProduct(
                barcode,
                name,
                firstBrand(product.brands()),
                basis,
                nutrition(product.nutriments()),
                servingSize(product, basis)));
    }

    /**
     * {@code nutrition_data_per} says {@code 100g} for most drinks, so a
     * product counts as per 100 ml when anything about it is in ml.
     */
    private static FoodProduct.Basis basis(OffJson.Product product) {
        boolean liquid = "100ml".equalsIgnoreCase(product.nutritionDataPer())
                || "ml".equalsIgnoreCase(product.productQuantityUnit())
                || "ml".equalsIgnoreCase(product.servingQuantityUnit());
        return liquid ? FoodProduct.Basis.PER_100_ML : FoodProduct.Basis.PER_100_G;
    }

    private static FoodProduct.Nutrition nutrition(@Nullable JsonNode nutriments) {
        Double calories = nutriment(nutriments, "energy-kcal_100g");
        if (calories == null) {
            // energy_100g is always kilojoules, whatever unit was entered.
            Double kilojoules = nutriment(nutriments, "energy-kj_100g");
            if (kilojoules == null) {
                kilojoules = nutriment(nutriments, "energy_100g");
            }
            calories = kilojoules == null ? null : round(kilojoules / KJ_PER_KCAL);
        }
        return new FoodProduct.Nutrition(
                calories,
                nutriment(nutriments, "proteins_100g"),
                nutriment(nutriments, "carbohydrates_100g"),
                nutriment(nutriments, "fat_100g"));
    }

    /** A serving in another unit than the basis (330 g of a drink) is left out rather than guessed. */
    private static @Nullable Double servingSize(OffJson.Product product, FoodProduct.Basis basis) {
        Double quantity = number(product.servingQuantity());
        if (quantity == null || quantity == 0) {
            return null;
        }
        String unit = product.servingQuantityUnit();
        String basisUnit = basis == FoodProduct.Basis.PER_100_ML ? "ml" : "g";
        return unit == null || unit.equalsIgnoreCase(basisUnit) ? quantity : null;
    }

    private static @Nullable String firstBrand(@Nullable String brands) {
        if (brands == null) {
            return null;
        }
        for (String brand : brands.split(",")) {
            String trimmed = text(brand);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static @Nullable Double nutriment(@Nullable JsonNode nutriments, String key) {
        return nutriments == null ? null : number(nutriments.get(key));
    }

    /** A non-negative finite number, from a JSON number or a numeric string, to one decimal place. */
    private static @Nullable Double number(@Nullable JsonNode node) {
        if (node == null) {
            return null;
        }
        double value;
        if (node.isNumber()) {
            value = node.doubleValue();
        } else if (node.isString()) {
            try {
                value = Double.parseDouble(node.stringValue().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        return Double.isFinite(value) && value >= 0 ? round(value) : null;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static @Nullable String text(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
