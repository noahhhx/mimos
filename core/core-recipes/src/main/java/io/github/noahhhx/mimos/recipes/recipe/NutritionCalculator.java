package io.github.noahhhx.mimos.recipes.recipe;

import io.github.noahhhx.mimos.recipes.recipe.NutritionEstimate.LineStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Calculates a recipe's per-serving nutrition from what its lines link to:
 * catalog entries (ADR-0015) and the owner's own recipes (ADR-0018).
 */
final class NutritionCalculator {

    private NutritionCalculator() {}

    /** Nutrition for an amount of one dimension: what a line's link is counted against. */
    private record Measure(MetricUnit.Dimension dimension, double amount, Nutrition nutrition) {}

    /** What lines link to: catalog entries by slug, and linked recipes' per-serving nutrition by id. */
    record Links(Map<String, CatalogIngredient> catalog, Map<UUID, Nutrition> recipes) {

        private @Nullable Measure measureOf(Ingredient line) {
            String slug = line.catalogSlug();
            if (slug != null) {
                CatalogIngredient entry = catalog.get(slug);
                return entry == null
                        ? null
                        : new Measure(entry.basis().dimension, entry.basis().amount, entry.nutrition());
            }
            UUID recipeId = line.recipeId();
            Nutrition perServing = recipeId == null ? null : recipes.get(recipeId);
            return perServing == null ? null : new Measure(MetricUnit.Dimension.SERVING, 1, perServing);
        }
    }

    /** A recipe as far as its per-serving nutrition goes. */
    record Node(int servings, NutritionSource source, Nutrition typed, List<Ingredient> lines) {}

    static NutritionEstimate estimate(List<Ingredient> lines, int servings, Links links) {
        List<LineStatus> statuses = new ArrayList<>();
        double calories = 0;
        double proteinG = 0;
        double carbsG = 0;
        double fatG = 0;
        boolean anyCounted = false;
        for (Ingredient line : lines) {
            Measure measure = links.measureOf(line);
            Double quantity = line.quantity();
            if (measure == null) {
                statuses.add(LineStatus.NOT_LINKED);
                continue;
            }
            if (quantity == null) {
                statuses.add(LineStatus.UNMEASURED);
                continue;
            }
            Optional<MetricUnit> unit =
                    MetricUnit.parse(line.unit()).filter(candidate -> candidate.dimension == measure.dimension());
            if (unit.isEmpty()) {
                statuses.add(LineStatus.UNIT_NOT_SUPPORTED);
                continue;
            }
            Nutrition per = measure.nutrition();
            Double perCalories = per.calories();
            Double perProteinG = per.proteinG();
            Double perCarbsG = per.carbsG();
            Double perFatG = per.fatG();
            if (perCalories == null || perProteinG == null || perCarbsG == null || perFatG == null) {
                statuses.add(LineStatus.NUTRITION_UNKNOWN);
                continue;
            }
            double amounts = quantity * unit.get().size / measure.amount();
            calories += amounts * perCalories;
            proteinG += amounts * perProteinG;
            carbsG += amounts * perCarbsG;
            fatG += amounts * perFatG;
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

    /**
     * Per-serving nutrition of every recipe in {@code recipes}, which must
     * hold each recipe a calculated one among them links to; {@code catalog}
     * holds the entries their lines link to.
     */
    static Map<UUID, Nutrition> perServing(Map<UUID, Node> recipes, Map<String, CatalogIngredient> catalog) {
        Walk walk = new Walk(recipes, catalog);
        recipes.keySet().forEach(walk::perServing);
        return walk.done;
    }

    /** A memoised depth-first walk of the recipes' links. */
    private static final class Walk {

        private final Map<UUID, Node> recipes;
        private final Map<String, CatalogIngredient> catalog;
        private final Map<UUID, Nutrition> done = new HashMap<>();
        private final Set<UUID> walking = new HashSet<>();

        Walk(Map<UUID, Node> recipes, Map<String, CatalogIngredient> catalog) {
            this.recipes = recipes;
            this.catalog = catalog;
        }

        Nutrition perServing(UUID id) {
            Nutrition known = done.get(id);
            if (known != null) {
                return known;
            }
            Node node = recipes.get(id);
            // Saving refuses a cycle, but two saves at once can still make one: a link back to a recipe
            // still being worked out counts as unknown instead of recursing forever.
            if (node == null || !walking.add(id)) {
                return Nutrition.UNKNOWN;
            }
            Nutrition result = node.typed();
            if (node.source() == NutritionSource.INGREDIENTS) {
                Map<UUID, Nutrition> linked = new HashMap<>();
                for (Ingredient line : node.lines()) {
                    UUID recipeId = line.recipeId();
                    if (recipeId != null) {
                        linked.put(recipeId, perServing(recipeId));
                    }
                }
                result = estimate(node.lines(), node.servings(), new Links(catalog, linked))
                        .perServing();
            }
            walking.remove(id);
            done.put(id, result);
            return result;
        }
    }

    private static double tenths(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
