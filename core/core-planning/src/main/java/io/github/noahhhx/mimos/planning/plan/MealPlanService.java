package io.github.noahhhx.mimos.planning.plan;

import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
    public MealPlan planFor(UUID ownerId, LocalDate startDate) {
        requireMonday(startDate);
        UUID planId = plans.ensurePlan(ownerId, startDate);
        return hydrate(ownerId, startDate, planId);
    }

    /** Plans a meal in the week's plan, eaten by the given diners (at least one). */
    @Transactional
    public PlannedMeal addEntry(
            UUID ownerId,
            Set<UUID> dinerProfileIds,
            LocalDate startDate,
            LocalDate date,
            MealType mealType,
            UUID recipeId,
            double servings) {
        if (dinerProfileIds.isEmpty()) {
            throw new IllegalArgumentException("a planned meal needs at least one diner");
        }
        requireMonday(startDate);
        requireWithinWeek(startDate, date);
        requireServings(servings);
        Recipe recipe = recipes.findVisible(recipeId, ownerId)
                .orElseThrow(() -> new NoSuchElementException("recipe not found: " + recipeId));
        UUID planId = plans.ensurePlan(ownerId, startDate);
        MealPlanRepository.PlannedMealInput entry =
                new MealPlanRepository.PlannedMealInput(UUID.randomUUID(), date, mealType, recipeId, servings);
        plans.insertEntry(planId, entry, dinerProfileIds);
        return new PlannedMeal(
                entry.id(), entry.date(), entry.mealType(), entry.recipeId(), recipe.title(), entry.servings());
    }

    /** Changes the planned servings of one of the owner's entries. */
    @Transactional
    public PlannedMeal updateServings(UUID ownerId, LocalDate startDate, UUID entryId, double servings) {
        requireMonday(startDate);
        requireServings(servings);
        MealPlanRepository.PlannedMealInput entry = requireEntry(ownerId, startDate, entryId);
        plans.updateServings(entryId, servings);
        return withTitle(new MealPlanRepository.PlannedMealInput(
                entry.id(), entry.date(), entry.mealType(), entry.recipeId(), servings));
    }

    /** Removes one of the owner's entries. */
    @Transactional
    public void removeEntry(UUID ownerId, LocalDate startDate, UUID entryId) {
        requireMonday(startDate);
        requireEntry(ownerId, startDate, entryId);
        plans.deleteEntry(entryId);
    }

    /** The week's raw entries with their recipes resolved (for shopping lists and logging). */
    public List<PlannedMeal> plannedMeals(UUID ownerId, LocalDate startDate) {
        return hydrate(ownerId, startDate, plans.ensurePlan(ownerId, startDate)).entries();
    }

    /** Every week the owner has planned meals in, oldest first; weeks without entries are left out. */
    public List<MealPlan> plansWithEntries(UUID ownerId) {
        List<MealPlanRepository.WeekEntry> all = plans.loadAllEntryInputs(ownerId);
        Map<UUID, Recipe> recipesById = recipes.findByIds(
                all.stream().map(weekEntry -> weekEntry.entry().recipeId()).toList());
        Map<UUID, MealPlan> byPlan = new LinkedHashMap<>();
        for (MealPlanRepository.WeekEntry weekEntry : all) {
            byPlan.computeIfAbsent(
                            weekEntry.planId(),
                            planId -> new MealPlan(planId, ownerId, weekEntry.startDate(), new ArrayList<>()))
                    .entries()
                    .add(toPlannedMeal(weekEntry.entry(), recipesById));
        }
        return byPlan.values().stream()
                .map(plan -> new MealPlan(plan.id(), ownerId, plan.startDate(), List.copyOf(plan.entries())))
                .toList();
    }

    /** Whether the owner has planned any meal in any week. */
    public boolean hasEntries(UUID ownerId) {
        return plans.hasEntries(ownerId);
    }

    /** Deletes every plan the owner has, with all their entries. */
    @Transactional
    public void deleteAll(UUID ownerId) {
        plans.deleteAllPlans(ownerId);
    }

    /**
     * Takes a diner off every meal the owner has planned, in every week;
     * a meal left with no diners is deleted, since an entry needs one.
     */
    @Transactional
    public void removeDiner(UUID ownerId, UUID dinerProfileId) {
        plans.removeDiner(ownerId, dinerProfileId);
    }

    private MealPlan hydrate(UUID ownerId, LocalDate startDate, UUID planId) {
        List<MealPlanRepository.PlannedMealInput> inputs = plans.loadEntryInputs(ownerId, startDate);
        Map<UUID, Recipe> recipesById = recipes.findByIds(inputs.stream()
                .map(MealPlanRepository.PlannedMealInput::recipeId)
                .toList());
        List<PlannedMeal> entries =
                inputs.stream().map(input -> toPlannedMeal(input, recipesById)).toList();
        return new MealPlan(planId, ownerId, startDate, entries);
    }

    private static PlannedMeal toPlannedMeal(MealPlanRepository.PlannedMealInput input, Map<UUID, Recipe> recipesById) {
        Recipe recipe = recipesById.get(input.recipeId());
        return new PlannedMeal(
                input.id(),
                input.date(),
                input.mealType(),
                input.recipeId(),
                recipe != null ? recipe.title() : "Deleted recipe",
                input.servings());
    }

    private PlannedMeal withTitle(MealPlanRepository.PlannedMealInput entry) {
        Recipe recipe = recipes.findByIds(List.of(entry.recipeId())).get(entry.recipeId());
        String title = recipe != null ? recipe.title() : "Deleted recipe";
        return new PlannedMeal(entry.id(), entry.date(), entry.mealType(), entry.recipeId(), title, entry.servings());
    }

    private MealPlanRepository.PlannedMealInput requireEntry(UUID ownerId, LocalDate startDate, UUID entryId) {
        MealPlanRepository.PlannedMealInput entry = plans.findEntry(ownerId, entryId)
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
