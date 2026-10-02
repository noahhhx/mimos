package io.github.noahhhx.mimos.api.account;

import io.github.noahhhx.mimos.planning.logging.MealLog;
import io.github.noahhhx.mimos.planning.logging.MealLogService;
import io.github.noahhhx.mimos.planning.plan.MealPlan;
import io.github.noahhhx.mimos.planning.plan.MealPlanService;
import io.github.noahhhx.mimos.planning.plan.PlannedMeal;
import io.github.noahhhx.mimos.planning.shopping.ShoppingList;
import io.github.noahhhx.mimos.planning.shopping.ShoppingListService;
import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.openapitools.model.AccountExport;
import org.openapitools.model.ExportedMealLog;
import org.openapitools.model.ExportedMealPlan;
import org.openapitools.model.ExportedPlanEntry;
import org.openapitools.model.ExportedRecipe;
import org.openapitools.model.ExportedShoppingList;
import org.openapitools.model.ExportedShoppingListItem;
import org.openapitools.model.IngredientQuantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one user's data as an account export in the current format
 * version (ADR-0011), read through the core modules' public services.
 * Personal recipes are exported in full; library recipes only by slug.
 */
@Service
public class AccountExporter {

    private final RecipeService recipes;
    private final MealPlanService mealPlans;
    private final ShoppingListService shoppingLists;
    private final MealLogService mealLogs;
    private final Clock clock;

    public AccountExporter(
            RecipeService recipes,
            MealPlanService mealPlans,
            ShoppingListService shoppingLists,
            MealLogService mealLogs,
            Clock clock) {
        this.recipes = recipes;
        this.mealPlans = mealPlans;
        this.shoppingLists = shoppingLists;
        this.mealLogs = mealLogs;
        this.clock = clock;
    }

    /** The owner's data as one consistent snapshot. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountExport export(UUID ownerProfileId) {
        List<Recipe> owned = recipes.findOwned(ownerProfileId, null);
        List<MealPlan> plans = mealPlans.plansWithEntries(ownerProfileId);
        List<ShoppingList> lists = shoppingLists.findAll(ownerProfileId);
        List<MealLog> logs = mealLogs.findAll(ownerProfileId);

        Set<UUID> ownedIds = owned.stream().map(Recipe::id).collect(Collectors.toSet());
        Map<UUID, String> librarySlugs = librarySlugs(Stream.concat(
                        plans.stream().flatMap(plan -> plan.entries().stream().map(PlannedMeal::recipeId)),
                        logs.stream().map(MealLog::recipeId))
                .filter(id -> id != null && !ownedIds.contains(id))
                .collect(Collectors.toSet()));

        return new AccountExport()
                .format(AccountExport.FormatEnum.MIMOS_EXPORT)
                .version(ExportUpgrader.CURRENT_VERSION)
                .exportedAt(toOffset(clock.instant()))
                .recipes(owned.stream().map(AccountExporter::toExported).toList())
                .mealPlans(plans.stream()
                        .map(plan -> toExported(plan, ownedIds, librarySlugs))
                        .toList())
                .shoppingLists(lists.stream().map(AccountExporter::toExported).toList())
                .mealLogs(logs.stream()
                        .map(log -> toExported(log, ownedIds, librarySlugs))
                        .toList());
    }

    /** Slugs of the library recipes among the given ids. */
    private Map<UUID, String> librarySlugs(Set<UUID> recipeIds) {
        Map<UUID, String> slugs = new HashMap<>();
        recipes.findByIds(recipeIds).values().forEach(recipe -> {
            String slug = recipe.slug();
            if (recipe.isLibrary() && slug != null) {
                slugs.put(recipe.id(), slug);
            }
        });
        return slugs;
    }

    private static ExportedRecipe toExported(Recipe recipe) {
        return new ExportedRecipe()
                .id(recipe.id())
                .title(recipe.title())
                .description(recipe.description())
                .servings(recipe.servings())
                .prepMinutes(recipe.prepMinutes())
                .cookMinutes(recipe.cookMinutes())
                .tags(recipe.tags())
                .nutrition(toApiNutrition(recipe.nutrition()))
                .ingredients(recipe.ingredients().stream()
                        .map(ingredient -> new IngredientQuantity()
                                .quantity(toBigDecimal(ingredient.quantity()))
                                .unit(ingredient.unit())
                                .name(ingredient.name()))
                        .toList())
                .steps(recipe.steps().stream()
                        .map(step -> new org.openapitools.model.RecipeStep().instruction(step.instruction()))
                        .toList())
                .createdAt(toOffset(recipe.createdAt()))
                .updatedAt(toOffset(recipe.updatedAt()));
    }

    /** A plan's entries; an entry whose recipe is neither owned nor in the library cannot be referenced. */
    private static ExportedMealPlan toExported(MealPlan plan, Set<UUID> ownedIds, Map<UUID, String> librarySlugs) {
        return new ExportedMealPlan()
                .startDate(plan.startDate())
                .entries(plan.entries().stream()
                        .filter(entry ->
                                ownedIds.contains(entry.recipeId()) || librarySlugs.containsKey(entry.recipeId()))
                        .map(entry -> new ExportedPlanEntry()
                                .date(entry.date())
                                .mealType(org.openapitools.model.MealType.valueOf(
                                        entry.mealType().name()))
                                .recipeId(ownedIds.contains(entry.recipeId()) ? entry.recipeId() : null)
                                .librarySlug(librarySlugs.get(entry.recipeId()))
                                .servings(BigDecimal.valueOf(entry.servings())))
                        .toList());
    }

    private static ExportedShoppingList toExported(ShoppingList list) {
        return new ExportedShoppingList()
                .startDate(list.startDate())
                .generatedAt(toOffset(list.generatedAt()))
                .items(list.items().stream()
                        .map(item -> new ExportedShoppingListItem()
                                .name(item.name())
                                .unit(item.unit())
                                .quantity(toBigDecimal(item.quantity()))
                                .category(item.category())
                                .checked(item.checked()))
                        .toList());
    }

    private static ExportedMealLog toExported(MealLog log, Set<UUID> ownedIds, Map<UUID, String> librarySlugs) {
        UUID recipeId = log.recipeId();
        return new ExportedMealLog()
                .date(log.date())
                .mealType(org.openapitools.model.MealType.valueOf(log.mealType().name()))
                .recipeId(recipeId != null && ownedIds.contains(recipeId) ? recipeId : null)
                .librarySlug(recipeId != null ? librarySlugs.get(recipeId) : null)
                .description(log.description())
                .servings(BigDecimal.valueOf(log.servings()))
                .nutrition(toApiNutrition(log.nutrition()))
                .loggedAt(toOffset(log.loggedAt()));
    }

    private static org.openapitools.model.Nutrition toApiNutrition(Nutrition nutrition) {
        return new org.openapitools.model.Nutrition()
                .calories(toBigDecimal(nutrition.calories()))
                .proteinG(toBigDecimal(nutrition.proteinG()))
                .carbsG(toBigDecimal(nutrition.carbsG()))
                .fatG(toBigDecimal(nutrition.fatG()));
    }

    private static @Nullable BigDecimal toBigDecimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
