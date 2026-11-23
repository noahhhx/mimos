package io.github.noahhhx.mimos.planning.shopping;

import io.github.noahhhx.mimos.planning.plan.MealPlanService;
import io.github.noahhhx.mimos.planning.plan.PlannedItem;
import io.github.noahhhx.mimos.planning.plan.PlannedMeal;
import io.github.noahhhx.mimos.recipes.recipe.Ingredient;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shopping list generation: aggregates ingredient quantities across the
 * week's planned recipes, scaled by planned servings, plus one line per
 * planned food (ADR-0026), keyed by normalized ingredient name + unit. A
 * line that is another recipe (ADR-0018) adds that recipe's ingredients
 * instead, through any depth of nesting, so the list holds only what to buy. A
 * counted line (no unit: "1 onion") rounds up to whole items once totaled,
 * since nobody buys 0.13 of an onion; measured lines keep their amount. Regeneration replaces the list but preserves
 * checked-off state for lines that survive (same name and unit) — ticking
 * things off in the store then replanning shouldn't lose your progress.
 * Items added by hand (ADR-0027) are not the plan's, so regeneration keeps
 * them as they are and never merges them with a generated line.
 */
@Service
public class ShoppingListService {

    static final int MAX_ITEMS = 1000;
    static final int MAX_NAME_LENGTH = 200;
    static final int MAX_UNIT_LENGTH = 30;
    static final double MAX_ADDED_QUANTITY = 10_000;

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
    public ShoppingList generate(UUID ownerId, LocalDate startDate) {
        requireMonday(startDate);
        Instant generatedAt = clock.instant();
        UUID listId = repository.lockOrCreateList(ownerId, startDate, generatedAt);
        List<ShoppingList.ShoppingListItem> previous =
                repository.findList(ownerId, startDate).map(ShoppingList::items).orElse(List.of());
        List<PlannedMeal> planned = mealPlanService.plannedMeals(ownerId, startDate);
        Map<UUID, Recipe> recipesById = withLinkedRecipes(planned.stream()
                .map(meal -> meal.item() instanceof PlannedItem.Recipe recipe ? recipe.recipeId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<String, Boolean> previouslyChecked = previous.stream()
                .filter(item -> !item.added())
                .collect(Collectors.toMap(
                        item -> lineKey(Aisles.normalizeName(item.name()), item.unit()),
                        ShoppingList.ShoppingListItem::checked,
                        (a, b) -> a));
        List<ShoppingListItemDraft> added = previous.stream()
                .filter(ShoppingList.ShoppingListItem::added)
                .map(item -> new ShoppingListItemDraft(
                        item.name(), item.unit(), item.quantity(), item.category(), item.checked(), true))
                .toList();

        // Aggregate: (normalized name, unit) -> first-seen display name, and the total of the
        // measured quantities. Unmeasured ingredients add a line but nothing to its total, so a
        // line with no measured contributions has no quantity (ADR-0007).
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (PlannedMeal meal : planned) {
            switch (meal.item()) {
                case PlannedItem.Recipe plannedRecipe -> {
                    Recipe recipe = recipesById.get(plannedRecipe.recipeId());
                    if (recipe == null) {
                        continue; // recipe deleted since planning; the FK already removed the entry.
                    }
                    Set<UUID> path = new HashSet<>(Set.of(recipe.id()));
                    addIngredients(recipe, meal.amount() / recipe.servings(), recipesById, path, totals, displayNames);
                }
                // A planned food is one line of its own, merged with any recipe line of the same name and unit.
                case PlannedItem.Food food -> {
                    String key = lineKey(Aisles.normalizeName(meal.title()), food.unit());
                    totals.merge(key, meal.amount(), Double::sum);
                    displayNames.putIfAbsent(key, meal.title());
                }
            }
        }

        List<ShoppingListItemDraft> items = java.util.stream.Stream.concat(
                        displayNames.entrySet().stream().map(entry -> {
                            String key = entry.getKey();
                            String name = entry.getValue();
                            String unit = unitOf(key);
                            Double total = totals.get(key);
                            return new ShoppingListItemDraft(
                                    name,
                                    unit,
                                    total == null ? null : unit == null ? wholeItems(total) : round(total),
                                    Aisles.categorize(name),
                                    previouslyChecked.getOrDefault(key, false),
                                    false);
                        }),
                        added.stream())
                .sorted(Comparator.comparing((ShoppingListItemDraft item) -> categoryRank(item.category()))
                        .thenComparing(ShoppingListItemDraft::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        repository.replaceItems(listId, generatedAt, items);
        return find(ownerId, startDate)
                .orElseThrow(() -> new IllegalStateException("list must exist after generation"));
    }

    /**
     * Adds an item by hand (ADR-0027): anything to buy, planned or not,
     * with an optional amount, in the aisle its name suggests. A week with
     * no list yet gets one generated from its plan first, so the item joins
     * the week's list rather than standing in for it.
     */
    @Transactional
    public ShoppingList.ShoppingListItem addItem(
            UUID ownerId, LocalDate startDate, String name, @Nullable Double quantity, @Nullable String unit) {
        requireMonday(startDate);
        String itemName = requireText("item name", name, MAX_NAME_LENGTH).strip();
        String itemUnit = unit == null || unit.isBlank() ? null : unit.strip();
        if (itemUnit != null && itemUnit.length() > MAX_UNIT_LENGTH) {
            throw new IllegalArgumentException("item unit must be at most " + MAX_UNIT_LENGTH + " characters");
        }
        if (quantity != null
                && (quantity.isNaN() || quantity.isInfinite() || quantity <= 0 || quantity > MAX_ADDED_QUANTITY)) {
            throw new IllegalArgumentException(
                    "item quantity must be more than 0 and at most " + (int) MAX_ADDED_QUANTITY);
        }
        if (itemUnit != null && quantity == null) {
            throw new IllegalArgumentException("an item with a unit needs a quantity");
        }
        if (repository.findList(ownerId, startDate).isEmpty()) {
            generate(ownerId, startDate);
        }
        UUID listId = repository.lockOrCreateList(ownerId, startDate, clock.instant());
        if (repository.countItems(listId) >= MAX_ITEMS) {
            throw new IllegalArgumentException("a shopping list can have at most " + MAX_ITEMS + " items");
        }
        UUID itemId = repository.appendItem(
                listId,
                new ShoppingListItemDraft(itemName, itemUnit, quantity, Aisles.categorize(itemName), false, true));
        return repository
                .findItem(ownerId, startDate, itemId)
                .orElseThrow(() -> new IllegalStateException("item must exist after adding it"));
    }

    /**
     * Removes an item added by hand. A generated item is the plan's, so it
     * cannot be removed: it would come back on the next regeneration.
     */
    @Transactional
    public void removeItem(UUID ownerId, LocalDate startDate, UUID itemId) {
        requireMonday(startDate);
        ShoppingList.ShoppingListItem item = repository
                .findItem(ownerId, startDate, itemId)
                .orElseThrow(() -> new NoSuchElementException("shopping list item not found: " + itemId));
        if (!item.added()) {
            throw new IllegalArgumentException(
                    "Only items added by hand can be removed. Change the plan and regenerate the list instead.");
        }
        repository.deleteItem(itemId);
    }

    /** The recipes by id, plus every recipe their lines link, however deep, loaded a level at a time. */
    private Map<UUID, Recipe> withLinkedRecipes(Set<UUID> recipeIds) {
        Map<UUID, Recipe> loaded = new HashMap<>();
        Set<UUID> requested = new HashSet<>();
        Set<UUID> next = recipeIds;
        while (!next.isEmpty()) {
            requested.addAll(next);
            loaded.putAll(recipes.findByIds(next));
            next = loaded.values().stream()
                    .flatMap(recipe -> recipe.ingredients().stream())
                    .map(Ingredient::recipeId)
                    .filter(Objects::nonNull)
                    .filter(id -> !requested.contains(id))
                    .collect(Collectors.toSet());
        }
        return loaded;
    }

    /**
     * Adds a recipe's lines at {@code scale} (null: unmeasured), replacing
     * each linked recipe with its own lines. A link that is missing, or that
     * leads back into {@code path} (two edits saved at once can make a cycle),
     * stays a line of its own.
     */
    private static void addIngredients(
            Recipe recipe,
            @Nullable Double scale,
            Map<UUID, Recipe> recipesById,
            Set<UUID> path,
            Map<String, Double> totals,
            Map<String, String> displayNames) {
        for (Ingredient ingredient : recipe.ingredients()) {
            UUID linkedId = ingredient.recipeId();
            Recipe linked = linkedId == null || path.contains(linkedId) ? null : recipesById.get(linkedId);
            if (linked != null) {
                Double servings = ingredient.servingsOf(linked.servings());
                Double linkedScale = scale == null || servings == null ? null : scale * servings / linked.servings();
                path.add(linked.id());
                addIngredients(linked, linkedScale, recipesById, path, totals, displayNames);
                path.remove(linked.id());
                continue;
            }
            String key = lineKey(Aisles.normalizeName(ingredient.name()), ingredient.unit());
            Double quantity = ingredient.quantity();
            if (quantity != null && scale != null) {
                totals.merge(key, quantity * scale, Double::sum);
            }
            displayNames.putIfAbsent(key, ingredient.name());
        }
    }

    /** The owner's generated list for the week, if one exists. */
    public Optional<ShoppingList> find(UUID ownerId, LocalDate startDate) {
        requireMonday(startDate);
        return repository.findList(ownerId, startDate);
    }

    /** Every list the owner has, oldest week first. */
    public List<ShoppingList> findAll(UUID ownerId) {
        return repository.findAllLists(ownerId);
    }

    /** Whether any of the owner's lists has an item. */
    public boolean hasItems(UUID ownerId) {
        return repository.hasItems(ownerId);
    }

    /** Deletes every list the owner has. */
    @Transactional
    public void deleteAll(UUID ownerId) {
        repository.deleteAllLists(ownerId);
    }

    /**
     * Restores a list as it was exported (ADR-0011): items, order, and
     * check-off state as given, not regenerated from the plan, replacing
     * any list the owner has for the week.
     */
    @Transactional
    public ShoppingList restore(
            UUID ownerId, LocalDate startDate, Instant generatedAt, List<ShoppingListItemDraft> items) {
        requireMonday(startDate);
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("a shopping list can have at most " + MAX_ITEMS + " items");
        }
        for (ShoppingListItemDraft item : items) {
            requireText("item name", item.name(), MAX_NAME_LENGTH);
            requireText("item category", item.category(), MAX_NAME_LENGTH);
            if (item.unit() != null && item.unit().length() > MAX_UNIT_LENGTH) {
                throw new IllegalArgumentException("item unit must be at most " + MAX_UNIT_LENGTH + " characters");
            }
            Double quantity = item.quantity();
            if (quantity != null && (quantity.isNaN() || quantity.isInfinite() || quantity < 0)) {
                throw new IllegalArgumentException("item quantity must not be negative: " + item.name());
            }
        }
        repository.replaceItems(repository.lockOrCreateList(ownerId, startDate, generatedAt), generatedAt, items);
        return find(ownerId, startDate).orElseThrow(() -> new IllegalStateException("list must exist after restoring"));
    }

    /** Checks an item off (or back on). */
    @Transactional
    public ShoppingList.ShoppingListItem updateChecked(
            UUID ownerId, LocalDate startDate, UUID itemId, boolean checked) {
        requireMonday(startDate);
        ShoppingList.ShoppingListItem item = repository
                .findItem(ownerId, startDate, itemId)
                .orElseThrow(() -> new NoSuchElementException("shopping list item not found: " + itemId));
        repository.updateChecked(itemId, checked);
        return new ShoppingList.ShoppingListItem(
                item.id(), item.name(), item.unit(), item.quantity(), item.category(), checked, item.added());
    }

    private static void requireMonday(LocalDate startDate) {
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("startDate must be the Monday of the week");
        }
    }

    private static String requireText(String field, @Nullable String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.strip().length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return value;
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

    /** Rounded first, so floating-point noise (1.0000001 eggs) does not buy an extra one. */
    private static double wholeItems(double value) {
        return Math.ceil(round(value));
    }
}
