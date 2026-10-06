package io.github.noahhhx.mimos.planning.logging;

import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calorie and macro logging. Logging from a recipe copies the recipe's
 * per-serving nutrition scaled by servings (the log is historical: later
 * recipe edits don't rewrite history); ad-hoc logs carry manual totals.
 */
@Service
public class MealLogService {

    static final int MAX_RANGE_DAYS = 62;
    static final int MAX_DESCRIPTION_LENGTH = 200;
    static final double MAX_SERVINGS = 100;

    private final MealLogRepository repository;
    private final RecipeService recipes;
    private final Clock clock;

    public MealLogService(MealLogRepository repository, RecipeService recipes, Clock clock) {
        this.repository = repository;
        this.recipes = recipes;
        this.clock = clock;
    }

    /**
     * Logs a meal for a profile, from a recipe (nutrition derived) or
     * ad-hoc. A recipe must be one visible to {@code recipeOwnerId}, the
     * owner of the recipes the profile can see.
     */
    public MealLog create(UUID ownerProfileId, UUID recipeOwnerId, MealLogDraft draft) {
        if (draft.date() == null) {
            throw new IllegalArgumentException("date is required");
        }
        requireServings(draft.servings());
        MealLog log;
        if (draft.recipeId() != null) {
            Recipe recipe = recipes.findVisible(draft.recipeId(), recipeOwnerId)
                    .orElseThrow(() -> new NoSuchElementException("recipe not found: " + draft.recipeId()));
            String description =
                    draft.description() != null && !draft.description().isBlank()
                            ? draft.description().strip()
                            : recipe.title();
            log = new MealLog(
                    UUID.randomUUID(),
                    draft.date(),
                    draft.mealType(),
                    recipe.id(),
                    requireDescription(description),
                    draft.servings(),
                    zerosForStorage(scaled(recipe.nutrition(), draft.servings())),
                    clock.instant());
        } else {
            if (draft.description() == null || draft.description().isBlank()) {
                throw new IllegalArgumentException("description is required for ad-hoc logs");
            }
            log = new MealLog(
                    UUID.randomUUID(),
                    draft.date(),
                    draft.mealType(),
                    null,
                    requireDescription(draft.description()),
                    draft.servings(),
                    zerosForStorage(nonNegative(draft.nutrition())),
                    clock.instant());
        }
        repository.insert(ownerProfileId, log);
        return log;
    }

    /**
     * Restores a logged meal from an account export (ADR-0011). The draft's
     * nutrition is the total as logged, kept as history rather than derived
     * from today's recipe; a referenced recipe must be visible to
     * {@code recipeOwnerId}.
     */
    public MealLog restore(UUID ownerProfileId, UUID recipeOwnerId, MealLogDraft draft, Instant loggedAt) {
        if (draft.date() == null) {
            throw new IllegalArgumentException("date is required");
        }
        requireServings(draft.servings());
        if (draft.recipeId() != null
                && recipes.findVisible(draft.recipeId(), recipeOwnerId).isEmpty()) {
            throw new NoSuchElementException("recipe not found: " + draft.recipeId());
        }
        if (draft.description() == null) {
            throw new IllegalArgumentException("description is required");
        }
        MealLog log = new MealLog(
                UUID.randomUUID(),
                draft.date(),
                draft.mealType(),
                draft.recipeId(),
                requireDescription(draft.description()),
                draft.servings(),
                zerosForStorage(nonNegative(draft.nutrition())),
                loggedAt);
        repository.insert(ownerProfileId, log);
        return log;
    }

    /** Every log the owner has, in display order. */
    public List<MealLog> findAll(UUID ownerProfileId) {
        return repository.findAll(ownerProfileId);
    }

    /** Whether the owner has logged any meal. */
    public boolean hasLogs(UUID ownerProfileId) {
        return repository.existsFor(ownerProfileId);
    }

    /**
     * Clears the links from a profile's logs to the recipes of
     * {@code recipeOwnerId}, which the profile can no longer see. Each log
     * keeps its description and the nutrition copied when it was logged.
     */
    @Transactional
    public void unlinkRecipesOf(UUID ownerProfileId, UUID recipeOwnerId) {
        repository.unlinkRecipes(ownerProfileId, recipes.ownedIds(recipeOwnerId));
    }

    /** The owner's logs in a date range (inclusive), in display order. */
    public List<MealLog> findRange(UUID ownerProfileId, LocalDate from, LocalDate to) {
        requireRange(from, to);
        return repository.findRange(ownerProfileId, from, to);
    }

    /** Per-day totals for a date range (inclusive). */
    public List<DailyLogSummary> summarize(UUID ownerProfileId, LocalDate from, LocalDate to) {
        return DailyLogSummary.summarize(findRange(ownerProfileId, from, to));
    }

    /** Deletes one of the owner's log entries. */
    public void delete(UUID ownerProfileId, UUID logId) {
        if (!repository.deleteById(ownerProfileId, logId)) {
            throw new NoSuchElementException("meal log not found: " + logId);
        }
    }

    /** Stored log totals are definitive numbers: unknown values become 0. */
    private static Nutrition zerosForStorage(Nutrition nutrition) {
        return new Nutrition(
                orZero(nutrition.calories()),
                orZero(nutrition.proteinG()),
                orZero(nutrition.carbsG()),
                orZero(nutrition.fatG()));
    }

    private static double orZero(@Nullable Double value) {
        return value == null ? 0 : value;
    }

    private static Nutrition scaled(Nutrition perServing, double servings) {
        return new Nutrition(
                scale(perServing.calories(), servings),
                scale(perServing.proteinG(), servings),
                scale(perServing.carbsG(), servings),
                scale(perServing.fatG(), servings));
    }

    private static @Nullable Double scale(@Nullable Double perServing, double servings) {
        return perServing == null ? null : Math.round(perServing * servings * 10.0) / 10.0;
    }

    private static Nutrition nonNegative(Nutrition nutrition) {
        return new Nutrition(
                requireNonNegative("calories", nutrition.calories()),
                requireNonNegative("proteinG", nutrition.proteinG()),
                requireNonNegative("carbsG", nutrition.carbsG()),
                requireNonNegative("fatG", nutrition.fatG()));
    }

    private static @Nullable Double requireNonNegative(String field, @Nullable Double value) {
        if (value != null && (Double.isNaN(value) || value < 0)) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static String requireDescription(String description) {
        if (description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return description;
    }

    private static void requireServings(double servings) {
        if (Double.isNaN(servings) || servings <= 0 || servings > MAX_SERVINGS) {
            throw new IllegalArgumentException("servings must be between 0 (exclusive) and " + MAX_SERVINGS);
        }
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("range must be at most " + MAX_RANGE_DAYS + " days");
        }
    }
}
