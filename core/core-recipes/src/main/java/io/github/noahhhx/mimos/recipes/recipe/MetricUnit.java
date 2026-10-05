package io.github.noahhhx.mimos.recipes.recipe;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The units calculated nutrition understands: metric only, with the metric
 * spoons (ADR-0015). A line with no unit is a count of pieces.
 */
enum MetricUnit {
    PIECE(null, Dimension.COUNT, 1),
    G("g", Dimension.MASS, 1),
    KG("kg", Dimension.MASS, 1000),
    ML("ml", Dimension.VOLUME, 1),
    L("l", Dimension.VOLUME, 1000),
    TSP("tsp", Dimension.VOLUME, 5),
    TBSP("tbsp", Dimension.VOLUME, 15);

    enum Dimension {
        MASS,
        VOLUME,
        COUNT
    }

    private final @Nullable String symbol;
    final Dimension dimension;
    /** How many grams, millilitres, or pieces one of this unit is. */
    final double size;

    MetricUnit(@Nullable String symbol, Dimension dimension, double size) {
        this.symbol = symbol;
        this.dimension = dimension;
        this.size = size;
    }

    static Optional<MetricUnit> parse(@Nullable String unit) {
        if (unit == null || unit.isBlank()) {
            return Optional.of(PIECE);
        }
        String normalized = unit.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(candidate -> normalized.equals(candidate.symbol))
                .findFirst();
    }
}
