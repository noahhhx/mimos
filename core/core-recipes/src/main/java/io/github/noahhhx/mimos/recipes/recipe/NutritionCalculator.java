package io.github.noahhhx.mimos.recipes.recipe;

import io.github.noahhhx.mimos.recipes.recipe.NutritionEstimate.LineStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Calculates a recipe's per-serving nutrition from its lines' catalog entries (ADR-0015). */
final class NutritionCalculator {

    private NutritionCalculator() {}

    /** {@code catalog} holds the entries of every linked line, by slug. */
    static NutritionEstimate estimate(List<Ingredient> lines, int servings, Map<String, CatalogIngredient> catalog) {
        List<LineStatus> statuses = new ArrayList<>();
        double calories = 0;
        double proteinG = 0;
        double carbsG = 0;
        double fatG = 0;
        boolean anyCounted = false;
        for (Ingredient line : lines) {
            String slug = line.catalogSlug();
            @Nullable CatalogIngredient entry = slug == null ? null : catalog.get(slug);
            Double quantity = line.quantity();
            if (entry == null) {
                statuses.add(LineStatus.NOT_LINKED);
                continue;
            }
            if (quantity == null) {
                statuses.add(LineStatus.UNMEASURED);
                continue;
            }
            Optional<MetricUnit> unit =
                    MetricUnit.parse(line.unit()).filter(candidate -> candidate.dimension == entry.basis().dimension);
            if (unit.isEmpty()) {
                statuses.add(LineStatus.UNIT_NOT_SUPPORTED);
                continue;
            }
            double bases = quantity * unit.get().size / entry.basis().amount;
            calories += bases * entry.calories();
            proteinG += bases * entry.proteinG();
            carbsG += bases * entry.carbsG();
            fatG += bases * entry.fatG();
            anyCounted = true;
            statuses.add(LineStatus.COUNTED);
        }
        Nutrition perServing = anyCounted
                ? new Nutrition(
                        (double) Math.round(calories / servings),
                        tenths(proteinG / servings),
                        tenths(carbsG / servings),
                        tenths(fatG / servings))
                : Nutrition.UNKNOWN;
        return new NutritionEstimate(perServing, List.copyOf(statuses));
    }

    private static double tenths(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
