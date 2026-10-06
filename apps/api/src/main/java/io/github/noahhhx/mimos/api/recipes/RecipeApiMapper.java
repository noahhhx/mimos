package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.recipes.recipe.CatalogIngredient;
import io.github.noahhhx.mimos.recipes.recipe.Ingredient;
import io.github.noahhhx.mimos.recipes.recipe.IngredientDraft;
import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import io.github.noahhhx.mimos.recipes.recipe.NutritionEstimate;
import io.github.noahhhx.mimos.recipes.recipe.NutritionSource;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeDraft;
import io.github.noahhhx.mimos.recipes.recipe.RecipeStep;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.openapitools.model.IngredientInput;
import org.openapitools.model.IngredientLineStatus;
import org.openapitools.model.IngredientQuantity;
import org.openapitools.model.NutritionBasis;
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
                .nutritionSource(toApiSource(recipe.nutritionSource()))
                .ingredients(toApiIngredients(recipe.ingredients()))
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
                NutritionSource.valueOf(input.getNutritionSource().name()),
                fromApiIngredients(input.getIngredients()),
                fromApiSteps(input));
    }

    public static org.openapitools.model.CatalogIngredient toApiCatalogIngredient(CatalogIngredient entry) {
        return new org.openapitools.model.CatalogIngredient()
                .slug(entry.slug())
                .name(entry.name())
                .isShared(entry.isShared())
                .basis(NutritionBasis.valueOf(entry.basis().name()))
                .nutrition(toApiNutrition(entry.nutrition()));
    }

    public static IngredientDraft toIngredientDraft(IngredientInput input) {
        return new IngredientDraft(
                input.getName(),
                io.github.noahhhx.mimos.recipes.recipe.NutritionBasis.valueOf(
                        input.getBasis().name()),
                fromApiNutrition(input.getNutrition()));
    }

    public static org.openapitools.model.NutritionEstimate toApiEstimate(NutritionEstimate estimate) {
        return new org.openapitools.model.NutritionEstimate()
                .nutrition(toApiNutrition(estimate.perServing()))
                .lines(estimate.lines().stream()
                        .map(status -> IngredientLineStatus.valueOf(status.name()))
                        .toList());
    }

    public static org.openapitools.model.NutritionSource toApiSource(NutritionSource source) {
        return org.openapitools.model.NutritionSource.valueOf(source.name());
    }

    public static List<IngredientQuantity> toApiIngredients(List<Ingredient> ingredients) {
        return ingredients.stream()
                .map(ingredient -> new IngredientQuantity()
                        .quantity(toBigDecimal(ingredient.quantity()))
                        .unit(ingredient.unit())
                        .name(ingredient.name())
                        .note(ingredient.note())
                        .catalogSlug(ingredient.catalogSlug())
                        .recipeId(ingredient.recipeId()))
                .toList();
    }

    public static List<Ingredient> fromApiIngredients(List<IngredientQuantity> ingredients) {
        return ingredients.stream()
                .map(ingredient -> new Ingredient(
                        fromBigDecimal(ingredient.getQuantity()),
                        ingredient.getUnit(),
                        ingredient.getName(),
                        ingredient.getNote(),
                        ingredient.getCatalogSlug(),
                        ingredient.getRecipeId()))
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
