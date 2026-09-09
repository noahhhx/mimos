package io.github.noahhhx.mimos.planning.plan;

import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meal planning use cases. Plans are keyed by their Monday and created
 * empty on first access; entries must fall inside the plan's week and
 * reference recipes the owner can see (their own or the library). Recipe
 * data is resolved through core-recipes' public interface only.
 */
@Service
public class MealPlanService {

    /** The servings bound of the plan domain; suggestion validation mirrors it (ADR-0006). */
    public static final double MAX_SERVINGS = 100;

    private final MealPlanRepository plans;
    private final RecipeService recipes;

    public MealPlanService(MealPlanRepository plans, RecipeService recipes) {
        this.plans = plans;
        this.recipes = recipes;
    }

    /** The owner's plan for the week, created empty on first access. */
    public MealPlan planFor(UUID ownerProfileId, LocalDate startDate) {
        requireMonday(startDate);
        UUID planId = plans.ensurePlan(ownerProfileId, startDate);
        return hydrate(ownerProfileId, startDate, planId);
    }

    /** Plans a meal in the week's plan. */
    @Transactional
    public PlannedMeal addEntry(
            UUID ownerProfileId,
            LocalDate startDate,
            LocalDate date,
            MealType mealType,
            UUID recipeId,
            double servings) {
        requireMonday(startDate);
        requireWithinWeek(startDate, date);
        requireServings(servings);
        Recipe recipe = recipes.findVisible(recipeId, ownerProfileId)
                .orElseThrow(() -> new NoSuchElementException("recipe not found: " + recipeId));
        UUID planId = plans.ensurePlan(ownerProfileId, startDate);
        MealPlanRepository.PlannedMealInput entry =
                new MealPlanRepository.PlannedMealInput(UUID.randomUUID(), date, mealType, recipeId, servings);
        plans.insertEntry(planId, entry);
        return new PlannedMeal(
                entry.id(), entry.date(), entry.mealType(), entry.recipeId(), recipe.title(), entry.servings());
    }

    /** Changes the planned servings of one of the owner's entries. */
    @Transactional
    public PlannedMeal updateServings(UUID ownerProfileId, LocalDate startDate, UUID entryId, double servings) {
        requireMonday(startDate);
        requireServings(servings);
        MealPlanRepository.PlannedMealInput entry = requireEntry(ownerProfileId, startDate, entryId);
        plans.updateServings(entryId, servings);
        return withTitle(new MealPlanRepository.PlannedMealInput(
                entry.id(), entry.date(), entry.mealType(), entry.recipeId(), servings));
    }

    /** Removes one of the owner's entries. */
    @Transactional
    public void removeEntry(UUID ownerProfileId, LocalDate startDate, UUID entryId) {
        requireMonday(startDate);
        requireEntry(ownerProfileId, startDate, entryId);
        plans.deleteEntry(entryId);
    }

    /** The week's raw entries with their recipes resolved (for shopping lists and logging). */
    public List<PlannedMeal> plannedMeals(UUID ownerProfileId, LocalDate startDate) {
        return hydrate(ownerProfileId, startDate, plans.ensurePlan(ownerProfileId, startDate))
                .entries();
    }

    private MealPlan hydrate(UUID ownerProfileId, LocalDate startDate, UUID planId) {
        List<MealPlanRepository.PlannedMealInput> inputs = plans.loadEntryInputs(ownerProfileId, startDate);
        Map<UUID, Recipe> recipesById = recipes.findByIds(inputs.stream()
                .map(MealPlanRepository.PlannedMealInput::recipeId)
                .toList());
        List<PlannedMeal> entries = inputs.stream()
                .map(input -> new PlannedMeal(
                        input.id(),
                        input.date(),
                        input.mealType(),
                        input.recipeId(),
                        recipesById.containsKey(input.recipeId())
                                ? recipesById.get(input.recipeId()).title()
                                : "Deleted recipe",
                        input.servings()))
                .toList();
        return new MealPlan(planId, ownerProfileId, startDate, entries);
    }

    private PlannedMeal withTitle(MealPlanRepository.PlannedMealInput entry) {
        Recipe recipe = recipes.findByIds(List.of(entry.recipeId())).get(entry.recipeId());
        String title = recipe != null ? recipe.title() : "Deleted recipe";
        return new PlannedMeal(entry.id(), entry.date(), entry.mealType(), entry.recipeId(), title, entry.servings());
    }

    private MealPlanRepository.PlannedMealInput requireEntry(UUID ownerProfileId, LocalDate startDate, UUID entryId) {
        MealPlanRepository.PlannedMealInput entry = plans.findEntry(ownerProfileId, entryId)
                .orElseThrow(() -> new NoSuchElementException("plan entry not found: " + entryId));
        if (entry.date().isBefore(startDate) || entry.date().isAfter(startDate.plusDays(6))) {
            // Exists, but not in the week named in the request.
            throw new NoSuchElementException("plan entry not found: " + entryId);
        }
        return entry;
    }

    private static void requireMonday(LocalDate startDate) {
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("startDate must be the Monday of the week");
        }
    }

    private static void requireWithinWeek(LocalDate startDate, LocalDate date) {
        if (date.isBefore(startDate) || date.isAfter(startDate.plusDays(6))) {
            throw new IllegalArgumentException("date must fall within the plan's week");
        }
    }

    private static void requireServings(double servings) {
        if (Double.isNaN(servings) || servings <= 0 || servings > MAX_SERVINGS) {
            throw new IllegalArgumentException("servings must be between 0 (exclusive) and " + MAX_SERVINGS);
        }
    }
}
