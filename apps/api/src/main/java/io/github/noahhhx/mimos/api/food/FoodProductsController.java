package io.github.noahhhx.mimos.api.food;

import io.github.noahhhx.mimos.openfoodfacts.Barcode;
import io.github.noahhhx.mimos.openfoodfacts.FoodProduct;
import io.github.noahhhx.mimos.openfoodfacts.OpenFoodFactsClient;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.FoodProductsApi;
import org.openapitools.model.Nutrition;
import org.openapitools.model.NutritionBasis;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Packaged food by barcode from Open Food Facts (ADR-0025); a 404 while the feature is off. */
@RestController
public class FoodProductsController implements FoodProductsApi {

    private final ObjectProvider<OpenFoodFactsClient> openFoodFacts;

    public FoodProductsController(ObjectProvider<OpenFoodFactsClient> openFoodFacts) {
        this.openFoodFacts = openFoodFacts;
    }

    @Override
    public ResponseEntity<org.openapitools.model.FoodProduct> lookUpFoodProduct(String barcode) {
        OpenFoodFactsClient client = openFoodFacts.getIfAvailable();
        if (client == null) {
            throw new NoSuchElementException("Open Food Facts is not available on this instance");
        }
        FoodProduct product = client.lookUp(new Barcode(barcode))
                .orElseThrow(
                        () -> new NoSuchElementException("Open Food Facts has no product with barcode " + barcode));
        return ResponseEntity.ok(toApi(product));
    }

    private static org.openapitools.model.FoodProduct toApi(FoodProduct product) {
        FoodProduct.Nutrition nutrition = product.nutrition();
        return new org.openapitools.model.FoodProduct()
                .barcode(product.barcode().digits())
                .name(product.name())
                .brand(product.brand())
                .basis(NutritionBasis.valueOf(product.basis().name()))
                .nutrition(new Nutrition()
                        .calories(decimal(nutrition.calories()))
                        .proteinG(decimal(nutrition.proteinG()))
                        .carbsG(decimal(nutrition.carbsG()))
                        .fatG(decimal(nutrition.fatG())))
                .servingSize(decimal(product.servingSize()));
    }

    private static @Nullable BigDecimal decimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
