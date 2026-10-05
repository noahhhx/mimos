package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.recipes.recipe.IngredientService;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.RecipesApi;
import org.openapitools.model.CatalogIngredient;
import org.openapitools.model.IngredientInput;
import org.openapitools.model.NutritionEstimate;
import org.openapitools.model.NutritionEstimateInput;
import org.openapitools.model.RecipeDetail;
import org.openapitools.model.RecipeInput;
import org.openapitools.model.RecipeSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal recipe CRUD and library browsing. Implements the
 * contract-generated {@link RecipesApi} (ADR-0003); status semantics
 * (404 invisible, 403 read-only library) come from the domain exceptions
 * mapped in {@code ApiExceptionHandler}.
 */
@RestController
public class RecipesController implements RecipesApi {

    private final RecipeService recipeService;
    private final IngredientService ingredients;
    private final CurrentUserService currentUser;

    public RecipesController(
            RecipeService recipeService, IngredientService ingredients, CurrentUserService currentUser) {
        this.recipeService = recipeService;
        this.ingredients = ingredients;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<List<RecipeSummary>> listMyRecipes(@Nullable String q) {
        UUID profileId = currentUser.requireProfile().id();
        List<RecipeSummary> recipes = recipeService.findOwned(profileId, q).stream()
                .map(RecipeApiMapper::toSummary)
                .toList();
        return ResponseEntity.ok(recipes);
    }

    @Override
    public ResponseEntity<RecipeDetail> createRecipe(RecipeInput recipeInput) {
        UUID profileId = currentUser.requireProfile().id();
        Recipe created = recipeService.create(profileId, RecipeApiMapper.toDraft(recipeInput));
        return ResponseEntity.status(201).body(RecipeApiMapper.toDetail(created));
    }

    @Override
    public ResponseEntity<RecipeDetail> getRecipe(UUID recipeId) {
        UUID profileId = currentUser.requireProfile().id();
        Recipe recipe = recipeService
                .findVisible(recipeId, profileId)
                .orElseThrow(() -> new NoSuchElementException("recipe not found: " + recipeId));
        return ResponseEntity.ok(RecipeApiMapper.toDetail(recipe));
    }

    @Override
    public ResponseEntity<RecipeDetail> replaceRecipe(UUID recipeId, RecipeInput recipeInput) {
        UUID profileId = currentUser.requireProfile().id();
        Recipe replaced = recipeService.replace(profileId, recipeId, RecipeApiMapper.toDraft(recipeInput));
        return ResponseEntity.ok(RecipeApiMapper.toDetail(replaced));
    }

    @Override
    public ResponseEntity<Void> deleteRecipe(UUID recipeId) {
        UUID profileId = currentUser.requireProfile().id();
        recipeService.delete(profileId, recipeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<NutritionEstimate> estimateRecipeNutrition(NutritionEstimateInput input) {
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(RecipeApiMapper.toApiEstimate(recipeService.estimateNutrition(
                profileId, RecipeApiMapper.fromApiIngredients(input.getIngredients()), input.getServings())));
    }

    @Override
    public ResponseEntity<List<CatalogIngredient>> listIngredients(@Nullable String q) {
        UUID profileId = currentUser.requireProfile().id();
        String term = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        return ResponseEntity.ok(ingredients.findVisible(profileId).stream()
                .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(term)
                        || entry.slug().contains(term))
                .map(RecipeApiMapper::toApiCatalogIngredient)
                .toList());
    }

    @Override
    public ResponseEntity<CatalogIngredient> createIngredient(IngredientInput ingredientInput) {
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.status(201)
                .body(RecipeApiMapper.toApiCatalogIngredient(
                        ingredients.create(profileId, RecipeApiMapper.toIngredientDraft(ingredientInput))));
    }

    @Override
    public ResponseEntity<CatalogIngredient> replaceIngredient(String ingredientSlug, IngredientInput ingredientInput) {
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(RecipeApiMapper.toApiCatalogIngredient(
                ingredients.replace(profileId, ingredientSlug, RecipeApiMapper.toIngredientDraft(ingredientInput))));
    }

    @Override
    public ResponseEntity<Void> deleteIngredient(String ingredientSlug) {
        ingredients.delete(currentUser.requireProfile().id(), ingredientSlug);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<RecipeSummary>> listLibraryRecipes(@Nullable String q) {
        return ResponseEntity.ok(recipeService.findLibrary(q).stream()
                .map(RecipeApiMapper::toSummary)
                .toList());
    }
}
