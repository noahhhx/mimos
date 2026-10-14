package io.github.noahhhx.mimos.openfoodfacts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpenFoodFactsTranslatorTests {

    private static FoodProduct translate(String barcode, OffJson.Product product) {
        return OpenFoodFactsTranslator.translate(new Barcode(barcode), product).orElseThrow();
    }

    private static OffJson.Product product(String json) {
        return OffJson.MAPPER.readValue(json, OffJson.Product.class);
    }

    @Test
    void aSolidIsPer100Grams() {
        FoodProduct nutella = translate("3017620422003", Fixtures.product("nutella.json"));

        assertThat(nutella)
                .isEqualTo(new FoodProduct(
                        new Barcode("3017620422003"),
                        "Nutella",
                        "Nutella",
                        FoodProduct.Basis.PER_100_G,
                        new FoodProduct.Nutrition(539.0, 6.3, 57.5, 30.9),
                        null));
    }

    @Test
    void aDrinkIsPer100MillilitresThoughOpenFoodFactsSaysPer100g() {
        FoodProduct cola = translate("5449000000996", Fixtures.product("coca-cola.json"));

        assertThat(cola.basis()).isEqualTo(FoodProduct.Basis.PER_100_ML);
        assertThat(cola.servingSize()).isEqualTo(330.0);
        assertThat(cola.nutrition())
                .as("a zero is a known value")
                .isEqualTo(new FoodProduct.Nutrition(42.4, 0.0, 10.6, 0.0));
        assertThat(cola.brand()).isEqualTo("COCA-COLA SERVICES SA/NV");
    }

    @Test
    void aMissingMacroIsAbsentNotZero() {
        FoodProduct marmite = translate("50184453", Fixtures.product("marmite.json"));

        assertThat(marmite.nutrition()).isEqualTo(new FoodProduct.Nutrition(260.0, 34.0, 30.0, null));
        assertThat(marmite.basis()).isEqualTo(FoodProduct.Basis.PER_100_G);
        assertThat(marmite.servingSize()).isEqualTo(8.0);
    }

    @Test
    void noNutritionAtAllLeavesEveryValueAbsent() {
        FoodProduct water = translate("3274080005003", Fixtures.product("water.json"));

        assertThat(water.nutrition())
                .as("Open Food Facts' own estimates are never used")
                .isEqualTo(new FoodProduct.Nutrition(null, null, null, null));
        assertThat(water.basis()).isEqualTo(FoodProduct.Basis.PER_100_ML);
        assertThat(water.servingSize()).isEqualTo(1500.0);
    }

    @Test
    void caloriesComeFromKilojoulesWhenKcalIsMissing() {
        FoodProduct marmite = translate("50184453", Fixtures.product("kilojoules-only.json"));

        assertThat(marmite.nutrition().calories()).isEqualTo(259.8);
    }

    @Test
    void aProductWithNoNameIsNotFound() {
        assertThat(OpenFoodFactsTranslator.translate(new Barcode("6111246950845"), Fixtures.product("nameless.json")))
                .isEqualTo(Optional.empty());
    }

    @Test
    void theGenericNameStandsInForAMissingProductName() {
        FoodProduct product =
                translate("50184453", product("{\"product_name\":\" \",\"generic_name\":\" Yeast Extract Spread \"}"));

        assertThat(product.name()).isEqualTo("Yeast Extract Spread");
        assertThat(product.brand()).isNull();
    }

    @Test
    void theFirstNonBlankBrandIsTheBrand() {
        assertThat(translate("50184453", product("{\"product_name\":\"x\",\"brands\":\" , Lindt, Lindt & Sprüngli\"}"))
                        .brand())
                .isEqualTo("Lindt");
    }

    @Test
    void numbersAsStringsAreRead() {
        FoodProduct product = translate("50184453", product("""
                {"product_name":"x","serving_quantity":"13.8","serving_quantity_unit":"g",
                 "nutriments":{"energy-kcal_100g":"515","proteins_100g":"7.9","carbohydrates_100g":"n/a"}}
                """));

        assertThat(product.nutrition()).isEqualTo(new FoodProduct.Nutrition(515.0, 7.9, null, null));
        assertThat(product.servingSize()).isEqualTo(13.8);
    }

    @Test
    void negativeValuesAreAbsentAndOthersRoundToOneDecimal() {
        FoodProduct product = translate("0123456789012", product("""
                {"product_name":"Peanut Butter Crunch",
                 "nutriments":{"energy-kcal_100g":52.941176470588,"proteins_100g":10.588235294118,
                               "carbohydrates_100g":2.9411764705882,"fat_100g":-1}}
                """));

        assertThat(product.nutrition()).isEqualTo(new FoodProduct.Nutrition(52.9, 10.6, 2.9, null));
    }

    @Test
    void aServingInAnotherUnitThanTheBasisIsLeftOut() {
        // Coca-Cola 5000112637922: a 330 ml can whose serving was entered as 330 g.
        FoodProduct product = translate("5000112637922", product("""
                {"product_name":"Coca Cola","product_quantity":330,"product_quantity_unit":"ml",
                 "serving_quantity":330,"serving_quantity_unit":"g"}
                """));

        assertThat(product.basis()).isEqualTo(FoodProduct.Basis.PER_100_ML);
        assertThat(product.servingSize()).isNull();
    }

    @Test
    void per100MillilitresIsReadFromTheNutritionBasisToo() {
        FoodProduct product =
                translate("6111246950845", product("{\"product_name\":\"x\",\"nutrition_data_per\":\"100ml\"}"));

        assertThat(product.basis()).isEqualTo(FoodProduct.Basis.PER_100_ML);
    }
}
