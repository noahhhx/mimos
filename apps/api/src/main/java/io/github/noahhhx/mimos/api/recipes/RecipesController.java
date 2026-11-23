package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.api.identity.IdentityService;
import io.github.noahhhx.mimos.api.identity.UserProfileRecord;
import io.github.noahhhx.mimos.recipes.recipe.IngredientService;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipePictureService;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.RecipesApi;
import org.openapitools.model.CatalogIngredient;
import org.openapitools.model.IngredientInput;
import org.openapitools.model.NutritionEstimate;
import org.openapitools.model.NutritionEstimateInput;
import org.openapitools.model.RecipeDetail;
import org.openapitools.model.RecipeInput;
import org.openapitools.model.RecipeRatingInput;
import org.openapitools.model.RecipeSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The household's recipe CRUD and library browsing. Implements the
 * contract-generated {@link RecipesApi} (ADR-0003); status semantics
 * (404 invisible, 403 read-only library) come from the domain exceptions
 * mapped in {@code ApiExceptionHandler}. Household recipes name who added
 * them (ADR-0019), and every detail lists its pictures (ADR-0023).
 */
@RestController
public class RecipesController implements RecipesApi {

    private final RecipeService recipeService;
    private final RecipePictureService pictures;
    private final IngredientService ingredients;
    private final CurrentUserService currentUser;
    private final IdentityService identity;
    private final RecipeSummaries summaries;

    public RecipesController(
            RecipeService recipeService,
            RecipePictureService pictures,
            IngredientService ingredients,
            CurrentUserService currentUser,
            IdentityService identity,
            RecipeSummaries summaries) {
        this.recipeService = recipeService;
        this.pictures = pictures;
        this.ingredients = ingredients;
        this.currentUser = currentUser;
        this.identity = identity;
        this.summaries = summaries;
    }

    @Override
    public ResponseEntity<List<RecipeSummary>> listMyRecipes(@Nullable String q) {
        UserProfileRecord profile = currentUser.requireProfile();
        return ResponseEntity.ok(summaries.of(recipeService.findOwned(profile.householdId(), q), profile.id()));
    }

    @Override
    public ResponseEntity<RecipeDetail> createRecipe(RecipeInput recipeInput) {
        UserProfileRecord profile = currentUser.requireProfile();
        Recipe created =
                recipeService.create(profile.householdId(), profile.id(), RecipeApiMapper.toDraft(recipeInput));
        return ResponseEntity.status(201).body(detail(created, profile.id()));
    }

    @Override
    public ResponseEntity<RecipeDetail> getRecipe(UUID recipeId) {
        UserProfileRecord profile = currentUser.requireProfile();
        Recipe recipe = recipeService
                .findVisible(recipeId, profile.householdId())
                .orElseThrow(() -> new NoSuchElementException("recipe not found: " + recipeId));
        return ResponseEntity.ok(detail(recipe, profile.id()));
    }

    @Override
    public ResponseEntity<RecipeDetail> replaceRecipe(UUID recipeId, RecipeInput recipeInput) {
        UserProfileRecord profile = currentUser.requireProfile();
        Recipe replaced = recipeService.replace(profile.householdId(), recipeId, RecipeApiMapper.toDraft(recipeInput));
        return ResponseEntity.ok(detail(replaced, profile.id()));
    }

    @Override
    public ResponseEntity<RecipeDetail> rateRecipe(UUID recipeId, RecipeRatingInput recipeRatingInput) {
        UserProfileRecord profile = currentUser.requireProfile();
        Integer stars = recipeRatingInput.getStars();
        if (stars == null) {
            throw new IllegalArgumentException("stars is required");
        }
        Recipe rated = recipeService.rate(profile.householdId(), recipeId, stars);
        return ResponseEntity.ok(detail(rated, profile.id()));
    }

    @Override
    public ResponseEntity<Void> clearRecipeRating(UUID recipeId) {
        recipeService.rate(currentUser.requireProfile().householdId(), recipeId, null);
        return ResponseEntity.noContent().build();
    }

    private RecipeDetail detail(Recipe recipe, UUID callerId) {
        UUID creatorId = recipe.createdByProfileId();
        Map<UUID, String> names = creatorId == null ? Map.of() : identity.displayNames(Set.of(creatorId));
        return RecipeApiMapper.toDetail(recipe, pictures.list(recipe.id()))
                .createdBy(RecipeSummaries.creator(recipe, names, callerId));
    }

    @Override
    public ResponseEntity<Void> deleteRecipe(UUID recipeId) {
        UUID householdId = currentUser.requireProfile().householdId();
        recipeService.delete(householdId, recipeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<NutritionEstimate> estimateRecipeNutrition(NutritionEstimateInput input) {
        UUID householdId = currentUser.requireProfile().householdId();
        return ResponseEntity.ok(RecipeApiMapper.toApiEstimate(recipeService.estimateNutrition(
                householdId, RecipeApiMapper.fromApiIngredients(input.getIngredients()), input.getServings())));
    }

    @Override
    public ResponseEntity<List<CatalogIngredient>> listIngredients(@Nullable String q) {
        UUID householdId = currentUser.requireProfile().householdId();
        String term = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        return ResponseEntity.ok(ingredients.findVisible(householdId).stream()
                .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(term)
                        || entry.slug().contains(term))
                .map(RecipeApiMapper::toApiCatalogIngredient)
                .toList());
    }

    @Override
    public ResponseEntity<CatalogIngredient> createIngredient(IngredientInput ingredientInput) {
        UUID householdId = currentUser.requireProfile().householdId();
        return ResponseEntity.status(201)
                .body(RecipeApiMapper.toApiCatalogIngredient(
                        ingredients.create(householdId, RecipeApiMapper.toIngredientDraft(ingredientInput))));
    }

    @Override
    public ResponseEntity<CatalogIngredient> replaceIngredient(String ingredientSlug, IngredientInput ingredientInput) {
        UUID householdId = currentUser.requireProfile().householdId();
        return ResponseEntity.ok(RecipeApiMapper.toApiCatalogIngredient(
                ingredients.replace(householdId, ingredientSlug, RecipeApiMapper.toIngredientDraft(ingredientInput))));
    }

    @Override
    public ResponseEntity<Void> deleteIngredient(String ingredientSlug) {
        ingredients.delete(currentUser.requireProfile().householdId(), ingredientSlug);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<RecipeSummary>> listLibraryRecipes(@Nullable String q) {
        return ResponseEntity.ok(recipeService.findLibrary(q).stream()
                .map(RecipeApiMapper::toSummary)
                .toList());
    }
}
