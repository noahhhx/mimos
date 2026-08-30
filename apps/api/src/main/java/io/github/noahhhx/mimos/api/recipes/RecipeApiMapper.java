package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.recipes.recipe.Ingredient;
import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeDraft;
import io.github.noahhhx.mimos.recipes.recipe.RecipeStep;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.openapitools.model.IngredientQuantity;
import org.openapitools.model.RecipeDetail;
import org.openapitools.model.RecipeInput;
import org.openapitools.model.RecipeSummary;

/** Maps between the contract models (generated) and the domain records. */
public final class RecipeApiMapper {

    private RecipeApiMapper() {}

    public static RecipeSummary toSummary(Recipe recipe) {
        return new RecipeSummary()
                .id(recipe.id())
                .slug(recipe.slug())
                .title(recipe.title())
                .description(recipe.description())
                .servings(recipe.servings())
                .isLibrary(recipe.isLibrary())
                .tags(recipe.tags())
                .nutrition(toApiNutrition(recipe.nutrition()));
    }

    public static RecipeDetail toDetail(Recipe recipe) {
        return new RecipeDetail()
                .id(recipe.id())
                .slug(recipe.slug())
                .title(recipe.title())
                .description(recipe.description())
                .servings(recipe.servings())
                .prepMinutes(recipe.prepMinutes())
                .cookMinutes(recipe.cookMinutes())
                .isLibrary(recipe.isLibrary())
                .tags(recipe.tags())
                .nutrition(toApiNutrition(recipe.nutrition()))
                .ingredients(recipe.ingredients().stream()
                        .map(ingredient -> new IngredientQuantity()
                                .quantity(BigDecimal.valueOf(ingredient.quantity()))
                                .unit(ingredient.unit())
                                .name(ingredient.name()))
                        .toList())
                .steps(recipe.steps().stream()
                        .map(step -> new org.openapitools.model.RecipeStep().instruction(step.instruction()))
                        .toList());
    }

    public static RecipeDraft toDraft(RecipeInput input) {
        return new RecipeDraft(
                input.getTitle(),
                input.getDescription(),
                input.getServings(),
                input.getPrepMinutes(),
                input.getCookMinutes(),
                input.getTags(),
                fromApiNutrition(input.getNutrition()),
                fromApiIngredients(input),
                fromApiSteps(input));
    }

    private static List<Ingredient> fromApiIngredients(RecipeInput input) {
        return input.getIngredients().stream()
                .map(ingredient -> new Ingredient(
                        ingredient.getQuantity().doubleValue(), ingredient.getUnit(), ingredient.getName()))
                .toList();
    }

    private static List<RecipeStep> fromApiSteps(RecipeInput input) {
        return input.getSteps().stream()
                .map(step -> new RecipeStep(step.getInstruction()))
                .toList();
    }

    private static org.openapitools.model.Nutrition toApiNutrition(Nutrition nutrition) {
        return new org.openapitools.model.Nutrition()
                .calories(toBigDecimal(nutrition.calories()))
                .proteinG(toBigDecimal(nutrition.proteinG()))
                .carbsG(toBigDecimal(nutrition.carbsG()))
                .fatG(toBigDecimal(nutrition.fatG()));
    }

    private static Nutrition fromApiNutrition(org.openapitools.model.@Nullable Nutrition nutrition) {
        if (nutrition == null) {
            return Nutrition.UNKNOWN;
        }
        return new Nutrition(
                fromBigDecimal(nutrition.getCalories()),
                fromBigDecimal(nutrition.getProteinG()),
                fromBigDecimal(nutrition.getCarbsG()),
                fromBigDecimal(nutrition.getFatG()));
    }

    private static @Nullable BigDecimal toBigDecimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static @Nullable Double fromBigDecimal(@Nullable BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
