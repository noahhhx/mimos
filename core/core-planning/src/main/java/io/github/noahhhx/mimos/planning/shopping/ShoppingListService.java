package io.github.noahhhx.mimos.planning.shopping;

import io.github.noahhhx.mimos.planning.plan.MealPlanService;
import io.github.noahhhx.mimos.planning.plan.PlannedMeal;
import io.github.noahhhx.mimos.recipes.recipe.Ingredient;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shopping list generation: aggregates ingredient quantities across the
 * week's planned recipes, scaled by planned servings, keyed by normalized
 * ingredient name + unit. Regeneration replaces the list but preserves
 * checked-off state for lines that survive (same name and unit) — ticking
 * things off in the store then replanning shouldn't lose your progress.
 */
@Service
public class ShoppingListService {

    private final MealPlanService mealPlanService;
    private final RecipeService recipes;
    private final ShoppingListRepository repository;
    private final Clock clock;

    public ShoppingListService(
            MealPlanService mealPlanService, RecipeService recipes, ShoppingListRepository repository, Clock clock) {
        this.mealPlanService = mealPlanService;
        this.recipes = recipes;
        this.repository = repository;
        this.clock = clock;
    }

    /** Generates (or regenerates) the owner's shopping list for the week. */
    @Transactional
    public ShoppingList generate(UUID ownerProfileId, LocalDate startDate) {
        requireMonday(startDate);
        List<PlannedMeal> planned = mealPlanService.plannedMeals(ownerProfileId, startDate);
        Map<UUID, Recipe> recipesById =
                recipes.findByIds(planned.stream().map(PlannedMeal::recipeId).toList());
        Map<String, Boolean> previouslyChecked = repository
                .findList(ownerProfileId, startDate)
                .map(list -> list.items().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                item -> lineKey(Aisles.normalizeName(item.name()), item.unit()),
                                ShoppingList.ShoppingListItem::checked,
                                (a, b) -> a)))
                .orElse(Map.of());

        // Aggregate: (normalized name, unit) -> first-seen display name, and the total of the
        // measured quantities. Unmeasured ingredients add a line but nothing to its total, so a
        // line with no measured contributions has no quantity (ADR-0007).
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (PlannedMeal meal : planned) {
            Recipe recipe = recipesById.get(meal.recipeId());
            if (recipe == null) {
                continue; // recipe deleted since planning; the FK already removed the entry.
            }
            double scale = meal.servings() / recipe.servings();
            for (Ingredient ingredient : recipe.ingredients()) {
                String key = lineKey(Aisles.normalizeName(ingredient.name()), ingredient.unit());
                Double quantity = ingredient.quantity();
                if (quantity != null) {
                    totals.merge(key, quantity * scale, Double::sum);
                }
                displayNames.putIfAbsent(key, ingredient.name());
            }
        }

        List<ShoppingListRepository.ItemRow> items = displayNames.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    String name = entry.getValue();
                    String unit = unitOf(key);
                    Double total = totals.get(key);
                    return new ShoppingListRepository.ItemRow(
                            name,
                            unit,
                            total == null ? null : round(total),
                            Aisles.categorize(name),
                            previouslyChecked.getOrDefault(key, false));
                })
                .sorted(Comparator.comparing((ShoppingListRepository.ItemRow item) -> categoryRank(item.category()))
                        .thenComparing(ShoppingListRepository.ItemRow::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        repository.replaceList(ownerProfileId, startDate, clock.instant(), items);
        return find(ownerProfileId, startDate)
                .orElseThrow(() -> new IllegalStateException("list must exist after generation"));
    }

    /** The owner's generated list for the week, if one exists. */
    public Optional<ShoppingList> find(UUID ownerProfileId, LocalDate startDate) {
        requireMonday(startDate);
        return repository.findList(ownerProfileId, startDate);
    }

    /** Checks an item off (or back on). */
    @Transactional
    public ShoppingList.ShoppingListItem updateChecked(
            UUID ownerProfileId, LocalDate startDate, UUID itemId, boolean checked) {
        requireMonday(startDate);
        ShoppingList.ShoppingListItem item = repository
                .findItem(ownerProfileId, startDate, itemId)
                .orElseThrow(() -> new NoSuchElementException("shopping list item not found: " + itemId));
        repository.updateChecked(itemId, checked);
        return new ShoppingList.ShoppingListItem(
                item.id(), item.name(), item.unit(), item.quantity(), item.category(), checked);
    }

    private static void requireMonday(LocalDate startDate) {
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("startDate must be the Monday of the week");
        }
    }

    private static String lineKey(String normalizedName, @Nullable String unit) {
        return unit == null
                ? normalizedName
                : normalizedName + "\u0000" + unit.strip().toLowerCase();
    }

    private static @Nullable String unitOf(String key) {
        int separator = key.indexOf('\u0000');
        return separator < 0 ? null : key.substring(separator + 1);
    }

    private static int categoryRank(String category) {
        int rank = Aisles.CATEGORY_ORDER.indexOf(category);
        return rank < 0 ? Aisles.CATEGORY_ORDER.size() : rank;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
