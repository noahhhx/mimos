package io.github.noahhhx.mimos.planning.plan;

import io.github.noahhhx.mimos.recipes.recipe.CatalogIngredient;
import io.github.noahhhx.mimos.recipes.recipe.IngredientService;
import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.IdGenerator;

/**
 * Meal planning use cases. Plans are keyed by their Monday and created
 * empty on first access; entries must fall inside the plan's week and
 * reference recipes or ingredients the owner can see (their own or the
 * shared library and catalog). Recipe and ingredient data is resolved
 * through core-recipes' public interface only.
 */
@Service
public class MealPlanService {

    /** The servings bound of a planned recipe; suggestion validation mirrors it (ADR-0006). */
    public static final double MAX_SERVINGS = 100;

    /** The quantity bound of a planned food, in its unit (ADR-0026). */
    public static final double MAX_FOOD_QUANTITY = 10_000;

    private final MealPlanRepository plans;
    private final RecipeService recipes;
    private final IngredientService ingredients;
    private final IdGenerator ids;

    public MealPlanService(
            MealPlanRepository plans, RecipeService recipes, IngredientService ingredients, IdGenerator ids) {
        this.plans = plans;
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.ids = ids;
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
            PlannedItem item,
            double amount) {
        requireDiners(dinerProfileIds);
        requireMonday(startDate);
        requireWithinWeek(startDate, date);
        switch (item) {
            case PlannedItem.Recipe recipe -> {
                requireAmount(amount, MAX_SERVINGS);
                if (recipes.findVisible(recipe.recipeId(), ownerId).isEmpty()) {
                    throw new NoSuchElementException("recipe not found: " + recipe.recipeId());
                }
            }
            case PlannedItem.Food food -> {
                requireAmount(amount, MAX_FOOD_QUANTITY);
                CatalogIngredient ingredient = visibleIngredient(ownerId, food.catalogSlug());
                if (ingredient.nutritionOf(1, food.unit()).isEmpty()) {
                    throw new IllegalArgumentException(
                            switch (ingredient.basis()) {
                                case PER_100_G ->
                                    food.catalogSlug() + " is measured per 100 g, so its unit must be g or kg";
                                case PER_100_ML ->
                                    food.catalogSlug()
                                            + " is measured per 100 ml, so its unit must be ml, l, tsp, or tbsp";
                                case PER_PIECE -> food.catalogSlug() + " is counted in pieces, so it takes no unit";
                            });
                }
            }
        }
        UUID planId = plans.ensurePlan(ownerId, startDate);
        MealPlanRepository.PlannedMealInput entry = new MealPlanRepository.PlannedMealInput(
                ids.generateId(), date, mealType, item, amount, Set.copyOf(dinerProfileIds));
        plans.insertEntry(planId, entry);
        return withTitle(ownerId, entry);
    }

    /**
     * Changes the amount, the diners (at least one), or both, of one of
     * the owner's entries; null leaves that part as it is. The amount is
     * bounded by the entry's own kind, and does not follow the diners.
     */
    @Transactional
    public PlannedMeal updateEntry(
            UUID ownerId,
            LocalDate startDate,
            UUID entryId,
            @Nullable Double amount,
            @Nullable Set<UUID> dinerProfileIds) {
        requireMonday(startDate);
        if (dinerProfileIds != null) {
            requireDiners(dinerProfileIds);
        }
        MealPlanRepository.PlannedMealInput entry = requireEntry(ownerId, startDate, entryId);
        if (amount != null) {
            requireAmount(amount, entry.item() instanceof PlannedItem.Recipe ? MAX_SERVINGS : MAX_FOOD_QUANTITY);
            plans.updateServings(entryId, amount);
        }
        if (dinerProfileIds != null) {
            plans.replaceDiners(entryId, dinerProfileIds);
        }
        return withTitle(
                ownerId,
                new MealPlanRepository.PlannedMealInput(
                        entry.id(),
                        entry.date(),
                        entry.mealType(),
                        entry.item(),
                        amount != null ? amount : entry.amount(),
                        dinerProfileIds != null ? Set.copyOf(dinerProfileIds) : entry.dinerProfileIds()));
    }

    /** Removes one of the owner's entries. */
    @Transactional
    public void removeEntry(UUID ownerId, LocalDate startDate, UUID entryId) {
        requireMonday(startDate);
        requireEntry(ownerId, startDate, entryId);
        plans.deleteEntry(entryId);
    }

    /** The week's raw entries with their titles resolved (for shopping lists and logging). */
    public List<PlannedMeal> plannedMeals(UUID ownerId, LocalDate startDate) {
        return hydrate(ownerId, startDate, plans.ensurePlan(ownerId, startDate)).entries();
    }

    /**
     * The diner's planned calories and macros for each day of the owner's
     * week, Monday first (ADR-0022). Reading them creates no plan.
     */
    public List<PlannedDayTotal> plannedTotals(UUID ownerId, LocalDate startDate, UUID dinerProfileId) {
        requireMonday(startDate);
        return plannedTotals(ownerId, startDate, startDate.plusDays(6), dinerProfileId);
    }

    /**
     * The diner's planned calories and macros for each day from
     * {@code from} to {@code to}, both included, in date order (ADR-0022).
     * Reading them creates no plan.
     */
    public List<PlannedDayTotal> plannedTotals(UUID ownerId, LocalDate from, LocalDate to, UUID dinerProfileId) {
        List<MealPlanRepository.PlannedMealInput> entries = plans.loadEntryInputsBetween(ownerId, from, to);
        return PlannedDayTotal.forRange(from, to, dinerProfileId, entries, perUnitNutrition(ownerId, entries));
    }

    /** Every week the owner has planned meals in, oldest first; weeks without entries are left out. */
    public List<MealPlan> plansWithEntries(UUID ownerId) {
        List<MealPlanRepository.WeekEntry> all = plans.loadAllEntryInputs(ownerId);
        Map<UUID, Recipe> recipesById = recipes.findByIds(recipeIdsOf(
                all.stream().map(MealPlanRepository.WeekEntry::entry).toList()));
        Map<String, CatalogIngredient> ingredientsBySlug = ingredients.findVisibleBySlugs(
                ownerId,
                foodSlugsOf(
                        all.stream().map(MealPlanRepository.WeekEntry::entry).toList()));
        Map<UUID, MealPlan> byPlan = new LinkedHashMap<>();
        for (MealPlanRepository.WeekEntry weekEntry : all) {
            byPlan.computeIfAbsent(
                            weekEntry.planId(),
                            planId -> new MealPlan(planId, ownerId, weekEntry.startDate(), new ArrayList<>()))
                    .entries()
                    .add(toPlannedMeal(weekEntry.entry(), recipesById, ingredientsBySlug));
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
        Map<UUID, Recipe> recipesById = recipes.findByIds(recipeIdsOf(inputs));
        Map<String, CatalogIngredient> ingredientsBySlug = ingredients.findVisibleBySlugs(ownerId, foodSlugsOf(inputs));
        List<PlannedMeal> entries = inputs.stream()
                .map(input -> toPlannedMeal(input, recipesById, ingredientsBySlug))
                .toList();
        return new MealPlan(planId, ownerId, startDate, entries);
    }

    /** Per unit of amount: a recipe's per-serving nutrition, or a food's per its unit. */
    private Map<PlannedItem, Nutrition> perUnitNutrition(
            UUID ownerId, List<MealPlanRepository.PlannedMealInput> entries) {
        Map<UUID, Recipe> recipesById = recipes.findByIds(recipeIdsOf(entries));
        Map<String, CatalogIngredient> ingredientsBySlug =
                ingredients.findVisibleBySlugs(ownerId, foodSlugsOf(entries));
        Map<PlannedItem, Nutrition> perUnit = new HashMap<>();
        for (MealPlanRepository.PlannedMealInput entry : entries) {
            switch (entry.item()) {
                case PlannedItem.Recipe recipe -> {
                    Recipe found = recipesById.get(recipe.recipeId());
                    if (found != null) {
                        perUnit.putIfAbsent(recipe, found.nutrition());
                    }
                }
                case PlannedItem.Food food -> {
                    CatalogIngredient ingredient = ingredientsBySlug.get(food.catalogSlug());
                    perUnit.putIfAbsent(
                            food,
                            ingredient == null
                                    ? Nutrition.UNKNOWN
                                    : ingredient.nutritionOf(1, food.unit()).orElse(Nutrition.UNKNOWN));
                }
            }
        }
        return perUnit;
    }

    private static PlannedMeal toPlannedMeal(
            MealPlanRepository.PlannedMealInput input,
            Map<UUID, Recipe> recipesById,
            Map<String, CatalogIngredient> ingredientsBySlug) {
        String title =
                switch (input.item()) {
                    case PlannedItem.Recipe recipe -> {
                        Recipe found = recipesById.get(recipe.recipeId());
                        yield found != null ? found.title() : "Deleted recipe";
                    }
                    case PlannedItem.Food food -> {
                        CatalogIngredient ingredient = ingredientsBySlug.get(food.catalogSlug());
                        yield ingredient != null ? ingredient.name() : food.catalogSlug();
                    }
                };
        return new PlannedMeal(
                input.id(),
                input.date(),
                input.mealType(),
                input.item(),
                title,
                input.amount(),
                input.dinerProfileIds());
    }

    private PlannedMeal withTitle(UUID ownerId, MealPlanRepository.PlannedMealInput entry) {
        return toPlannedMeal(
                entry,
                recipes.findByIds(recipeIdsOf(List.of(entry))),
                ingredients.findVisibleBySlugs(ownerId, foodSlugsOf(List.of(entry))));
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

    private CatalogIngredient visibleIngredient(UUID ownerId, String slug) {
        CatalogIngredient ingredient =
                ingredients.findVisibleBySlugs(ownerId, List.of(slug)).get(slug);
        if (ingredient == null) {
            throw new NoSuchElementException("ingredient not found: " + slug);
        }
        return ingredient;
    }

    private static List<UUID> recipeIdsOf(List<MealPlanRepository.PlannedMealInput> entries) {
        List<UUID> ids = new ArrayList<>();
        for (MealPlanRepository.PlannedMealInput entry : entries) {
            if (entry.item() instanceof PlannedItem.Recipe recipe) {
                ids.add(recipe.recipeId());
            }
        }
        return ids;
    }

    private static List<String> foodSlugsOf(List<MealPlanRepository.PlannedMealInput> entries) {
        List<String> slugs = new ArrayList<>();
        for (MealPlanRepository.PlannedMealInput entry : entries) {
            if (entry.item() instanceof PlannedItem.Food food) {
                slugs.add(food.catalogSlug());
            }
        }
        return slugs;
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

    private static void requireDiners(Set<UUID> dinerProfileIds) {
        if (dinerProfileIds.isEmpty()) {
            throw new IllegalArgumentException("a planned meal needs at least one diner");
        }
    }

    private static void requireAmount(double amount, double max) {
        if (Double.isNaN(amount) || amount <= 0 || amount > max) {
            throw new IllegalArgumentException("servings must be between 0 (exclusive) and " + max);
        }
    }
}
