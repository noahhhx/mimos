package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.api.identity.IdentityService;
import io.github.noahhhx.mimos.api.identity.UserProfileRecord;
import io.github.noahhhx.mimos.recipes.recipe.IngredientService;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.RecipesApi;
import org.openapitools.model.CatalogIngredient;
import org.openapitools.model.IngredientInput;
import org.openapitools.model.NutritionEstimate;
import org.openapitools.model.NutritionEstimateInput;
import org.openapitools.model.Person;
import org.openapitools.model.RecipeDetail;
import org.openapitools.model.RecipeInput;
import org.openapitools.model.RecipeSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The household's recipe CRUD and library browsing. Implements the
 * contract-generated {@link RecipesApi} (ADR-0003); status semantics
 * (404 invisible, 403 read-only library) come from the domain exceptions
 * mapped in {@code ApiExceptionHandler}. Household recipes name who added
 * them (ADR-0019).
 */
@RestController
public class RecipesController implements RecipesApi {

    private final RecipeService recipeService;
    private final IngredientService ingredients;
    private final CurrentUserService currentUser;
    private final IdentityService identity;

    public RecipesController(
            RecipeService recipeService,
            IngredientService ingredients,
            CurrentUserService currentUser,
            IdentityService identity) {
        this.recipeService = recipeService;
        this.ingredients = ingredients;
        this.currentUser = currentUser;
        this.identity = identity;
    }

    @Override
    public ResponseEntity<List<RecipeSummary>> listMyRecipes(@Nullable String q) {
        UserProfileRecord profile = currentUser.requireProfile();
        List<Recipe> recipes = recipeService.findOwned(profile.householdId(), q);
        Map<UUID, String> names = identity.displayNames(recipes.stream()
                .map(Recipe::createdByProfileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return ResponseEntity.ok(recipes.stream()
                .map(recipe -> RecipeApiMapper.toSummary(recipe).createdBy(creator(recipe, names, profile.id())))
                .toList());
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

    private RecipeDetail detail(Recipe recipe, UUID callerId) {
        UUID creatorId = recipe.createdByProfileId();
        Map<UUID, String> names = creatorId == null ? Map.of() : identity.displayNames(Set.of(creatorId));
        return RecipeApiMapper.toDetail(recipe).createdBy(creator(recipe, names, callerId));
    }

    /** Who added a household recipe; null for library recipes and when the creator's profile is gone. */
    private static @Nullable Person creator(Recipe recipe, Map<UUID, String> names, UUID callerId) {
        UUID creatorId = recipe.createdByProfileId();
        String name = creatorId == null ? null : names.get(creatorId);
        if (creatorId == null || name == null) {
            return null;
        }
        return new Person().id(creatorId).displayName(name).you(creatorId.equals(callerId));
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
