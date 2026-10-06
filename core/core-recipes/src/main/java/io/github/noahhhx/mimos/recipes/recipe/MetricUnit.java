package io.github.noahhhx.mimos.recipes.recipe;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The units calculated nutrition understands: metric, with the metric
 * spoons (ADR-0015), and servings of a linked recipe (ADR-0018). A line
 * with no unit is a count of pieces.
 */
enum MetricUnit {
    PIECE(Dimension.COUNT, 1),
    G(Dimension.MASS, 1, "g"),
    KG(Dimension.MASS, 1000, "kg"),
    ML(Dimension.VOLUME, 1, "ml"),
    L(Dimension.VOLUME, 1000, "l"),
    TSP(Dimension.VOLUME, 5, "tsp"),
    TBSP(Dimension.VOLUME, 15, "tbsp"),
    SERVING(Dimension.SERVING, 1, "servings", "serving");

    enum Dimension {
        MASS,
        VOLUME,
        COUNT,
        SERVING
    }

    private final List<String> symbols;
    final Dimension dimension;
    /** How many grams, millilitres, pieces, or servings one of this unit is. */
    final double size;

    MetricUnit(Dimension dimension, double size, String... symbols) {
        this.symbols = List.of(symbols);
        this.dimension = dimension;
        this.size = size;
    }

    static Optional<MetricUnit> parse(@Nullable String unit) {
        if (unit == null || unit.isBlank()) {
            return Optional.of(PIECE);
        }
        String normalized = unit.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(candidate -> candidate.symbols.contains(normalized))
                .findFirst();
    }
}
