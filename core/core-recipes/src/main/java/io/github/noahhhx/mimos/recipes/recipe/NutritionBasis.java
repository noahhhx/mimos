package io.github.noahhhx.mimos.recipes.recipe;

/** The amount a catalog ingredient's nutrition is given for (ADR-0015). */
public enum NutritionBasis {
    PER_100_G(MetricUnit.Dimension.MASS, 100),
    PER_100_ML(MetricUnit.Dimension.VOLUME, 100),
    PER_PIECE(MetricUnit.Dimension.COUNT, 1);

    final MetricUnit.Dimension dimension;
    final double amount;

    NutritionBasis(MetricUnit.Dimension dimension, double amount) {
        this.dimension = dimension;
        this.amount = amount;
    }
}
