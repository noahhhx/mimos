package io.github.noahhhx.mimos.openfoodfacts;

import org.jspecify.annotations.Nullable;

/**
 * A packaged food as Open Food Facts describes it (ADR-0025): its name, its
 * first brand, and its nutrition per 100 g or per 100 ml. {@code servingSize}
 * is one serving in the basis's unit, when Open Food Facts gives one.
 */
public record FoodProduct(
        Barcode barcode,
        String name,
        @Nullable String brand,
        Basis basis,
        Nutrition nutrition,
        @Nullable Double servingSize) {

    public enum Basis {
        PER_100_G,
        PER_100_ML
    }

    /** Values per basis. Absent values are unknown, not zero. */
    public record Nutrition(
            @Nullable Double calories,
            @Nullable Double proteinG,
            @Nullable Double carbsG,
            @Nullable Double fatG) {}
}
