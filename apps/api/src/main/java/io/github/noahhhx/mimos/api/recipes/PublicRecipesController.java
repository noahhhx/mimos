package io.github.noahhhx.mimos.api.recipes;

import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.util.List;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.PublicApi;
import org.openapitools.model.RecipeDetail;
import org.openapitools.model.RecipeSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated read access to the curated library (SEO recipe pages).
 * Only library recipes are ever exposed here — personal recipes never are.
 */
@RestController
public class PublicRecipesController implements PublicApi {

    private final RecipeService recipeService;

    public PublicRecipesController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @Override
    public ResponseEntity<List<RecipeSummary>> listPublicRecipes(@Nullable String q) {
        return ResponseEntity.ok(recipeService.findLibrary(q).stream()
                .map(RecipeApiMapper::toSummary)
                .toList());
    }

    @Override
    public ResponseEntity<RecipeDetail> getPublicRecipe(String slug) {
        RecipeDetail recipe = recipeService
                .findBySlug(slug)
                .map(RecipeApiMapper::toDetail)
                .orElseThrow(() -> new NoSuchElementException("no library recipe with slug: " + slug));
        return ResponseEntity.ok(recipe);
    }
}
