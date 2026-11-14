package io.github.noahhhx.mimos.recipes.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogIngredientTest {

    private static final CatalogIngredient LENTILS = new CatalogIngredient(
            "red-lentils", null, "Red lentils, dry", NutritionBasis.PER_100_G, 358, 24.0, 63.0, 2.2);
    private static final CatalogIngredient OLIVE_OIL =
            new CatalogIngredient("olive-oil", null, "Olive oil", NutritionBasis.PER_100_ML, 813, 0.0, 0.0, 92.0);
    private static final CatalogIngredient BANANA =
            new CatalogIngredient("banana", null, "Banana", NutritionBasis.PER_PIECE, 105, 1.3, 27.0, 0.4);

    @Test
    void agreesWithTheCalculatorForACountedRecipeLine() {
        Ingredient line = new Ingredient(250.0, "g", "red lentils", null, "red-lentils", null);

        NutritionEstimate estimate = NutritionCalculator.estimate(
                List.of(line), 1, new NutritionCalculator.Links(Map.of("red-lentils", LENTILS), Map.of()));

        // The calculator rounds a recipe's per-serving values; none of these lose anything to rounding.
        assertThat(estimate.perServing())
                .isEqualTo(LENTILS.nutritionOf(250, "g").orElseThrow());
    }

    @Test
    void theNutritionOfAnAmountIsExact() {
        CatalogIngredient snack =
                new CatalogIngredient("snack-bar", null, "Snack bar", NutritionBasis.PER_100_G, 250, 10.0, 30.0, 5.0);

        assertThat(snack.nutritionOf(1, "g").orElseThrow()).isEqualTo(new Nutrition(2.5, 0.1, 0.3, 0.05));
        assertThat(snack.nutritionOf(85, "g").orElseThrow()).isEqualTo(new Nutrition(212.5, 8.5, 25.5, 4.25));
        assertThat(LENTILS.nutritionOf(1, "kg").orElseThrow()).isEqualTo(new Nutrition(3580.0, 240.0, 630.0, 22.0));
        assertThat(OLIVE_OIL.nutritionOf(50, "ml").orElseThrow()).isEqualTo(new Nutrition(406.5, 0.0, 0.0, 46.0));
        assertThat(BANANA.nutritionOf(2, null).orElseThrow()).isEqualTo(new Nutrition(210.0, 2.6, 54.0, 0.8));
    }

    @Test
    void aUnitTheBasisDoesNotTakeIsEmpty() {
        assertThat(LENTILS.nutritionOf(1, "ml")).isEmpty();
        assertThat(LENTILS.nutritionOf(1, "tsp")).isEmpty();
        assertThat(OLIVE_OIL.nutritionOf(1, "g")).isEmpty();
        assertThat(OLIVE_OIL.nutritionOf(2, null)).isEmpty();
        assertThat(BANANA.nutritionOf(1, "g")).isEmpty();
        assertThat(BANANA.nutritionOf(1, "blob")).isEmpty();
    }
}
