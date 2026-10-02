package io.github.noahhhx.mimos.recipes.recipe;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recipe use cases: personal recipe CRUD with validation, library browse
 * and search, and public slug lookup. All mutations are validated here so
 * the rules live with the domain, not the HTTP layer. Validation failures
 * raise {@link IllegalArgumentException} (mapped to 400 by the API),
 * invisible recipes raise {@link NoSuchElementException} (404), and
 * attempts to mutate library recipes raise {@link ReadOnlyRecipeException}
 * (403).
 */
@Service
public class RecipeService {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_DESCRIPTION_LENGTH = 2000;
    static final int MAX_SERVINGS = 50;
    static final int MAX_INGREDIENTS = 100;
    static final int MAX_STEPS = 50;
    static final int MAX_TAGS = 12;
    static final int MAX_TAG_LENGTH = 30;

    private final RecipeRepository repository;
    private final Clock clock;

    public RecipeService(RecipeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Creates a personal recipe for the given owner. */
    @Transactional
    public Recipe create(UUID ownerProfileId, RecipeDraft submitted) {
        RecipeDraft draft = withDistinctTags(submitted);
        validate(draft);
        Recipe recipe = new Recipe(
                UUID.randomUUID(),
                ownerProfileId,
                null,
                draft.title(),
                draft.description(),
                draft.servings(),
                draft.prepMinutes(),
                draft.cookMinutes(),
                draft.nutrition(),
                draft.tags(),
                draft.ingredients(),
                draft.steps(),
                clock.instant(),
                clock.instant());
        repository.insert(recipe);
        return recipe;
    }

    /**
     * Restores a personal recipe from an account export (ADR-0011): validated
     * like any new recipe, with a new id and the exported timestamps.
     */
    @Transactional
    public Recipe restore(UUID ownerProfileId, RecipeDraft submitted, Instant createdAt, Instant updatedAt) {
        RecipeDraft draft = withDistinctTags(submitted);
        validate(draft);
        Recipe recipe = new Recipe(
                UUID.randomUUID(),
                ownerProfileId,
                null,
                draft.title(),
                draft.description(),
                draft.servings(),
                draft.prepMinutes(),
                draft.cookMinutes(),
                draft.nutrition(),
                draft.tags(),
                draft.ingredients(),
                draft.steps(),
                createdAt,
                updatedAt);
        repository.insert(recipe);
        return recipe;
    }

    /** Replaces a personal recipe; library recipes are read-only. */
    @Transactional
    public Recipe replace(UUID ownerProfileId, UUID recipeId, RecipeDraft submitted) {
        Recipe existing = requireOwned(ownerProfileId, recipeId);
        RecipeDraft draft = withDistinctTags(submitted);
        validate(draft);
        Recipe replaced = new Recipe(
                existing.id(),
                existing.ownerProfileId(),
                existing.slug(),
                draft.title(),
                draft.description(),
                draft.servings(),
                draft.prepMinutes(),
                draft.cookMinutes(),
                draft.nutrition(),
                draft.tags(),
                draft.ingredients(),
                draft.steps(),
                existing.createdAt(),
                clock.instant());
        repository.replace(replaced);
        return replaced;
    }

    /** Deletes a personal recipe; library recipes are read-only. */
    @Transactional
    public void delete(UUID ownerProfileId, UUID recipeId) {
        Recipe existing = requireOwned(ownerProfileId, recipeId);
        repository.deleteById(existing.id());
    }

    /** A recipe the viewer may see: their own or a library recipe. */
    public Optional<Recipe> findVisible(UUID recipeId, UUID viewerProfileId) {
        return repository
                .findById(recipeId)
                .filter(recipe -> recipe.isLibrary() || viewerProfileId.equals(recipe.ownerProfileId()));
    }

    /** The viewer's personal recipes, optionally filtered by a search term. */
    public List<Recipe> findOwned(UUID viewerProfileId, @Nullable String query) {
        return repository.findOwnedBy(viewerProfileId, normalizeQuery(query));
    }

    /** Whether the owner has any personal recipes. */
    public boolean hasOwned(UUID ownerProfileId) {
        return repository.existsOwnedBy(ownerProfileId);
    }

    /** Curated library recipes, optionally filtered by a search term. */
    public List<Recipe> findLibrary(@Nullable String query) {
        return repository.findLibrary(normalizeQuery(query));
    }

    /** A library recipe by its public slug. */
    public Optional<Recipe> findBySlug(String slug) {
        return repository.findBySlug(slug).filter(Recipe::isLibrary);
    }

    /** Loads several recipes at once (used by core-planning); missing ids are omitted. */
    public java.util.Map<UUID, Recipe> findByIds(java.util.Collection<UUID> recipeIds) {
        return repository.findByIds(recipeIds);
    }

    private Recipe requireOwned(UUID ownerProfileId, UUID recipeId) {
        Recipe recipe = repository
                .findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("recipe not found: " + recipeId));
        if (recipe.isLibrary()) {
            throw new ReadOnlyRecipeException("library recipes are read-only");
        }
        if (!ownerProfileId.equals(recipe.ownerProfileId())) {
            // Not the owner: indistinguishable from nonexistent.
            throw new NoSuchElementException("recipe not found: " + recipeId);
        }
        return recipe;
    }

    private static @Nullable String normalizeQuery(@Nullable String query) {
        return query == null || query.isBlank() ? null : query.strip();
    }

    /** A tag is a set member: repeating one is harmless, not an error (and not a duplicate row). */
    private static RecipeDraft withDistinctTags(RecipeDraft draft) {
        return new RecipeDraft(
                draft.title(),
                draft.description(),
                draft.servings(),
                draft.prepMinutes(),
                draft.cookMinutes(),
                draft.tags().stream().distinct().toList(),
                draft.nutrition(),
                draft.ingredients(),
                draft.steps());
    }

    private static void validate(RecipeDraft draft) {
        requireText("title", draft.title(), MAX_TITLE_LENGTH);
        requireText("description", draft.description(), MAX_DESCRIPTION_LENGTH);
        if (draft.servings() < 1 || draft.servings() > MAX_SERVINGS) {
            throw new IllegalArgumentException("servings must be between 1 and " + MAX_SERVINGS);
        }
        requireBounded("prepMinutes", draft.prepMinutes(), 0, 24 * 60);
        requireBounded("cookMinutes", draft.cookMinutes(), 0, 24 * 60);
        if (draft.ingredients().isEmpty() || draft.ingredients().size() > MAX_INGREDIENTS) {
            throw new IllegalArgumentException("a recipe needs between 1 and " + MAX_INGREDIENTS + " ingredients");
        }
        for (Ingredient ingredient : draft.ingredients()) {
            requireText("ingredient name", ingredient.name(), MAX_TITLE_LENGTH);
            Double quantity = ingredient.quantity();
            if (quantity != null && (quantity.isNaN() || quantity <= 0)) {
                throw new IllegalArgumentException("ingredient quantity must be positive: " + ingredient.name());
            }
            if (ingredient.unit() != null && ingredient.unit().length() > 30) {
                throw new IllegalArgumentException("ingredient unit must be at most 30 characters");
            }
        }
        if (draft.steps().isEmpty() || draft.steps().size() > MAX_STEPS) {
            throw new IllegalArgumentException("a recipe needs between 1 and " + MAX_STEPS + " steps");
        }
        for (RecipeStep step : draft.steps()) {
            requireText("step instruction", step.instruction(), 4000);
        }
        if (draft.tags().size() > MAX_TAGS) {
            throw new IllegalArgumentException("a recipe can have at most " + MAX_TAGS + " tags");
        }
        for (String tag : draft.tags()) {
            requireText("tag", tag, MAX_TAG_LENGTH);
        }
        validateNutrition(draft.nutrition());
    }

    private static void validateNutrition(Nutrition nutrition) {
        requireNonNegative("calories", nutrition.calories());
        requireNonNegative("proteinG", nutrition.proteinG());
        requireNonNegative("carbsG", nutrition.carbsG());
        requireNonNegative("fatG", nutrition.fatG());
    }

    private static void requireText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
    }

    private static void requireBounded(String field, @Nullable Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }

    private static void requireNonNegative(String field, @Nullable Double value) {
        if (value != null && (Double.isNaN(value) || value < 0)) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
