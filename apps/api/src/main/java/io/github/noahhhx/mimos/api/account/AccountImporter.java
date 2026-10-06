package io.github.noahhhx.mimos.api.account;

import io.github.noahhhx.mimos.api.household.HouseholdService;
import io.github.noahhhx.mimos.api.identity.IdentityService;
import io.github.noahhhx.mimos.api.identity.UserProfileRecord;
import io.github.noahhhx.mimos.planning.logging.MealLogDraft;
import io.github.noahhhx.mimos.planning.logging.MealLogService;
import io.github.noahhhx.mimos.planning.plan.MealPlanService;
import io.github.noahhhx.mimos.planning.plan.MealType;
import io.github.noahhhx.mimos.planning.shopping.ShoppingListRepository;
import io.github.noahhhx.mimos.planning.shopping.ShoppingListService;
import io.github.noahhhx.mimos.recipes.recipe.Ingredient;
import io.github.noahhhx.mimos.recipes.recipe.IngredientDraft;
import io.github.noahhhx.mimos.recipes.recipe.IngredientService;
import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import io.github.noahhhx.mimos.recipes.recipe.NutritionBasis;
import io.github.noahhhx.mimos.recipes.recipe.NutritionSource;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeDraft;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import io.github.noahhhx.mimos.recipes.recipe.RecipeStep;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.openapitools.model.AccountExport;
import org.openapitools.model.ExportedIngredient;
import org.openapitools.model.ExportedMealLog;
import org.openapitools.model.ExportedMealPlan;
import org.openapitools.model.ExportedPlanEntry;
import org.openapitools.model.ExportedRecipe;
import org.openapitools.model.ExportedShoppingList;
import org.openapitools.model.ExportedShoppingListItem;
import org.openapitools.model.ImportReport;
import org.openapitools.model.IngredientQuantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Restores an account export into an empty account (ADR-0011): upgrades
 * the document to the current format version, then writes every item
 * through the core modules' public services, so imported data obeys the
 * same rules as data entered through the API. All or nothing: any invalid
 * item is a 400 naming it, and nothing is written.
 */
@Service
public class AccountImporter {

    /** A failure message that already starts with a path, like {@code entries[0]: ...}. */
    private static final Pattern NESTED_PATH = Pattern.compile("[A-Za-z]+\\[\\d+]");

    private final ObjectMapper objectMapper;
    private final IdentityService identity;
    private final HouseholdService households;
    private final RecipeService recipes;
    private final IngredientService ingredients;
    private final MealPlanService mealPlans;
    private final ShoppingListService shoppingLists;
    private final MealLogService mealLogs;
    private final ExportUpgrader upgrader = ExportUpgrader.standard();

    public AccountImporter(
            ObjectMapper objectMapper,
            IdentityService identity,
            HouseholdService households,
            RecipeService recipes,
            IngredientService ingredients,
            MealPlanService mealPlans,
            ShoppingListService shoppingLists,
            MealLogService mealLogs) {
        this.objectMapper = objectMapper;
        this.identity = identity;
        this.households = households;
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.mealPlans = mealPlans;
        this.shoppingLists = shoppingLists;
        this.mealLogs = mealLogs;
    }

    /**
     * Imports the document into the importer's account, which must be an
     * empty household of one. Every planned meal becomes the importer's
     * alone, as an export holds only the meals its exporter ate.
     */
    @Transactional
    public ImportReport importInto(UserProfileRecord importer, JsonNode body) {
        ExportUpgrader.Upgraded upgraded = upgrader.upgrade(body);
        AccountExport document = bind(upgraded.document());

        UUID householdId = importer.householdId();
        identity.lockHousehold(householdId);
        if (households.isShared(householdId)) {
            throw new AccountNotEmptyException("Import needs an account of your own, and you share a household."
                    + " Leave the household first, or import into a fresh account.");
        }
        if (recipes.hasOwned(householdId)
                || ingredients.hasOwned(householdId)
                || mealPlans.hasEntries(householdId)
                || shoppingLists.hasItems(householdId)
                || mealLogs.hasLogs(importer.id())) {
            throw new AccountNotEmptyException("Import needs an empty account, and this one already has recipes,"
                    + " ingredients, planned meals, shopping lists, or logged meals."
                    + " Import into a fresh account instead.");
        }

        Run run = new Run(importer);
        run.importIngredients(present(document.getIngredients(), "ingredients"));
        run.importRecipes(present(document.getRecipes(), "recipes"));
        run.importPlans(present(document.getMealPlans(), "mealPlans"));
        run.importShoppingLists(present(document.getShoppingLists(), "shoppingLists"));
        run.importLogs(present(document.getMealLogs(), "mealLogs"));
        return new ImportReport()
                .sourceVersion(upgraded.sourceVersion())
                .recipes(run.recipeIds.size())
                .plannedMeals(run.plannedMeals)
                .shoppingLists(run.lists)
                .mealLogs(run.logs)
                .warnings(run.warnings());
    }

    private AccountExport bind(JsonNode document) {
        try {
            return objectMapper.treeToValue(document, AccountExport.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "The export is malformed at " + path(exception) + ": a value has the wrong type or is not allowed.",
                    exception);
        }
    }

    /** The JSON path of a binding failure, as {@code recipes[2].servings}. */
    private static String path(JacksonException exception) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : exception.getPath()) {
            if (reference.getPropertyName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.isEmpty() ? "the top level" : path.toString();
    }

    /** One import: the id mapping and what had to be left behind. */
    private final class Run {

        private final UUID household;
        private final UUID profile;
        /** Exported recipe id to the id it was imported as. */
        private final Map<UUID, UUID> recipeIds = new HashMap<>();
        /** Library slugs resolved so far; empty when the slug is not in this library. */
        private final Map<String, Optional<UUID>> librarySlugs = new HashMap<>();
        /** Per missing library slug: planned meals skipped and logged meals unlinked. */
        private final Map<String, int[]> missingSlugs = new LinkedHashMap<>();
        /** The export's own ingredient slugs to the slugs they were imported as. */
        private final Map<String, String> ingredientSlugs = new HashMap<>();
        /** Catalog slugs not in this instance's catalog, and the recipes that linked them. */
        private final Map<String, Set<String>> missingCatalogSlugs = new LinkedHashMap<>();

        private int plannedMeals;
        private int lists;
        private int logs;

        Run(UserProfileRecord importer) {
            this.household = importer.householdId();
            this.profile = importer.id();
        }

        void importIngredients(List<ExportedIngredient> exported) {
            eachAt("ingredients", exported, ingredient -> {
                String exportedSlug = present(ingredient.getSlug(), "slug");
                if (ingredientSlugs.containsKey(exportedSlug)) {
                    throw new IllegalArgumentException("slug " + exportedSlug + " is used by another ingredient");
                }
                ingredientSlugs.put(
                        exportedSlug,
                        ingredients
                                .create(
                                        household,
                                        new IngredientDraft(
                                                present(ingredient.getName(), "name"),
                                                NutritionBasis.valueOf(present(ingredient.getBasis(), "basis")
                                                        .name()),
                                                fromApiNutrition(present(ingredient.getNutrition(), "nutrition"))))
                                .slug());
            });
        }

        /** Restores each recipe after the recipes its lines link to, so every link has a recipe to point at. */
        void importRecipes(List<ExportedRecipe> exported) {
            Map<UUID, Integer> positions = new HashMap<>();
            eachAt("recipes", exported, recipe -> {
                UUID exportedId = present(recipe.getId(), "id");
                if (positions.containsKey(exportedId)) {
                    throw new IllegalArgumentException("id " + exportedId + " is used by another recipe");
                }
                positions.put(exportedId, positions.size());
            });
            for (int index : new LinkedFirst(exported, positions).order()) {
                ExportedRecipe recipe = exported.get(index);
                at("recipes[" + index + "]", () -> importRecipe(recipe));
            }
        }

        private void importRecipe(ExportedRecipe recipe) {
            UUID exportedId = present(recipe.getId(), "id");
            List<Ingredient> lines = present(recipe.getIngredients(), "ingredients").stream()
                    .map(ingredient -> {
                        UUID linked = present(ingredient, "ingredient").getRecipeId();
                        return new Ingredient(
                                fromBigDecimal(ingredient.getQuantity()),
                                ingredient.getUnit(),
                                ingredient.getName(),
                                ingredient.getNote(),
                                ingredient.getCatalogSlug(),
                                linked == null ? null : imported(linked));
                    })
                    .toList();
            List<Ingredient> linkable = relinked(recipe.getTitle(), lines);
            NutritionSource source = NutritionSource.valueOf(
                    present(recipe.getNutritionSource(), "nutritionSource").name());
            // A calculated recipe that lost a link keeps the nutrition it was exported with.
            if (unlinkedCount(linkable) > unlinkedCount(lines)) {
                source = NutritionSource.MANUAL;
            }
            Recipe restored = recipes.restore(
                    household,
                    profile,
                    new RecipeDraft(
                            recipe.getTitle(),
                            recipe.getDescription(),
                            present(recipe.getServings(), "servings"),
                            recipe.getPrepMinutes(),
                            recipe.getCookMinutes(),
                            present(recipe.getTags(), "tags"),
                            fromApiNutrition(present(recipe.getNutrition(), "nutrition")),
                            source,
                            linkable,
                            present(recipe.getSteps(), "steps").stream()
                                    .map(step ->
                                            new RecipeStep(present(step, "step").getInstruction()))
                                    .toList()),
                    present(recipe.getCreatedAt(), "createdAt").toInstant(),
                    present(recipe.getUpdatedAt(), "updatedAt").toInstant());
            recipeIds.put(exportedId, restored.id());
        }

        void importPlans(List<ExportedMealPlan> exported) {
            eachAt("mealPlans", exported, plan -> {
                LocalDate startDate = present(plan.getStartDate(), "startDate");
                eachAt("entries", present(plan.getEntries(), "entries"), entry -> {
                    UUID recipeId = resolvePlanned(entry);
                    if (recipeId == null) {
                        return;
                    }
                    mealPlans.addEntry(
                            household,
                            Set.of(profile),
                            startDate,
                            present(entry.getDate(), "date"),
                            MealType.valueOf(
                                    present(entry.getMealType(), "mealType").name()),
                            recipeId,
                            present(entry.getServings(), "servings").doubleValue());
                    plannedMeals++;
                });
            });
        }

        void importShoppingLists(List<ExportedShoppingList> exported) {
            Set<LocalDate> weeks = new HashSet<>();
            eachAt("shoppingLists", exported, list -> {
                LocalDate startDate = present(list.getStartDate(), "startDate");
                if (!weeks.add(startDate)) {
                    throw new IllegalArgumentException("another list is for the same week, " + startDate);
                }
                List<ShoppingListRepository.ItemRow> items = new ArrayList<>();
                eachAt("items", present(list.getItems(), "items"), item -> items.add(toItemRow(item)));
                shoppingLists.restore(
                        household,
                        startDate,
                        present(list.getGeneratedAt(), "generatedAt").toInstant(),
                        items);
                lists++;
            });
        }

        void importLogs(List<ExportedMealLog> exported) {
            eachAt("mealLogs", exported, log -> {
                mealLogs.restore(
                        profile,
                        household,
                        new MealLogDraft(
                                present(log.getDate(), "date"),
                                MealType.valueOf(
                                        present(log.getMealType(), "mealType").name()),
                                resolveLogged(log),
                                present(log.getServings(), "servings").doubleValue(),
                                present(log.getDescription(), "description"),
                                fromApiNutrition(present(log.getNutrition(), "nutrition"))),
                        present(log.getLoggedAt(), "loggedAt").toInstant());
                logs++;
            });
        }

        /** The planned meal's recipe here, or null when its library recipe is not in this library. */
        private @Nullable UUID resolvePlanned(ExportedPlanEntry entry) {
            UUID recipeId = entry.getRecipeId();
            String slug = entry.getLibrarySlug();
            if ((recipeId == null) == (slug == null)) {
                throw new IllegalArgumentException("a planned meal needs exactly one of recipeId and librarySlug");
            }
            if (recipeId != null) {
                return imported(recipeId);
            }
            Optional<UUID> library = library(slug);
            if (library.isEmpty()) {
                missing(slug)[0]++;
            }
            return library.orElse(null);
        }

        /** The logged meal's recipe here, if it had one that still exists. */
        private @Nullable UUID resolveLogged(ExportedMealLog log) {
            UUID recipeId = log.getRecipeId();
            String slug = log.getLibrarySlug();
            if (recipeId != null && slug != null) {
                throw new IllegalArgumentException("a logged meal has at most one of recipeId and librarySlug");
            }
            if (recipeId != null) {
                return imported(recipeId);
            }
            if (slug == null) {
                return null;
            }
            Optional<UUID> library = library(slug);
            if (library.isEmpty()) {
                missing(slug)[1]++;
            }
            return library.orElse(null);
        }

        private UUID imported(UUID exportedId) {
            UUID id = recipeIds.get(exportedId);
            if (id == null) {
                throw new IllegalArgumentException("recipeId " + exportedId + " is not one of the export's recipes");
            }
            return id;
        }

        /**
         * The lines with links to the export's own ingredients moved to their
         * imported slugs, and links this instance cannot resolve dropped.
         */
        private List<Ingredient> relinked(String recipeTitle, List<Ingredient> lines) {
            Set<String> shared = new HashSet<>();
            lines.forEach(line -> {
                String slug = line.catalogSlug();
                if (slug != null && !ingredientSlugs.containsKey(slug)) {
                    shared.add(slug);
                }
            });
            Set<String> known =
                    ingredients.findVisibleBySlugs(household, shared).keySet();
            return lines.stream()
                    .map(line -> {
                        String slug = line.catalogSlug();
                        if (slug == null || known.contains(slug)) {
                            return line;
                        }
                        String imported = ingredientSlugs.get(slug);
                        if (imported == null) {
                            missingCatalogSlugs
                                    .computeIfAbsent(slug, key -> new LinkedHashSet<>())
                                    .add(recipeTitle);
                        }
                        return new Ingredient(
                                line.quantity(), line.unit(), line.name(), line.note(), imported, line.recipeId());
                    })
                    .toList();
        }

        private static long unlinkedCount(List<Ingredient> lines) {
            return lines.stream().filter(line -> line.catalogSlug() == null).count();
        }

        private Optional<UUID> library(String slug) {
            return librarySlugs.computeIfAbsent(
                    slug, key -> recipes.findBySlug(key).map(Recipe::id));
        }

        private int[] missing(String slug) {
            return missingSlugs.computeIfAbsent(slug, key -> new int[2]);
        }

        List<String> warnings() {
            List<String> warnings = new ArrayList<>();
            missingSlugs.forEach((slug, counts) -> {
                List<String> effects = new ArrayList<>();
                if (counts[0] > 0) {
                    effects.add(count(counts[0], "planned meal") + " skipped");
                }
                if (counts[1] > 0) {
                    effects.add(count(counts[1], "logged meal") + " kept without the recipe link");
                }
                warnings.add("Library recipe \"" + slug + "\" is not in this instance's library: "
                        + String.join(", ", effects) + ".");
            });
            missingCatalogSlugs.forEach((slug, recipeTitles) -> warnings.add("Ingredient \"" + slug
                    + "\" is not in this instance's catalog, so it no longer counts toward nutrition in "
                    + String.join(
                            ", ",
                            recipeTitles.stream()
                                    .map(title -> "\"" + title + "\"")
                                    .toList())
                    + ", which keep the nutrition they were exported with."));
            return warnings;
        }
    }

    /**
     * The document's recipes in an order that puts each after the recipes
     * its lines link to (ADR-0018). A link to an id that is not one of the
     * document's recipes, or a recipe that uses itself through its links,
     * is a 400 naming where.
     */
    private static final class LinkedFirst {

        private final List<ExportedRecipe> recipes;
        private final Map<UUID, Integer> positions;
        private final List<Integer> order = new ArrayList<>();
        private final Set<Integer> placing = new HashSet<>();
        private final Set<Integer> placed = new HashSet<>();

        LinkedFirst(List<ExportedRecipe> recipes, Map<UUID, Integer> positions) {
            this.recipes = recipes;
            this.positions = positions;
        }

        List<Integer> order() {
            for (int index = 0; index < recipes.size(); index++) {
                place(index);
            }
            return order;
        }

        private void place(int index) {
            if (placed.contains(index)) {
                return;
            }
            String at = "recipes[" + index + "]";
            ExportedRecipe recipe = recipes.get(index);
            if (!placing.add(index)) {
                throw new IllegalArgumentException(at + ": \"" + recipe.getTitle()
                        + "\" uses itself as an ingredient, directly or through other recipes");
            }
            List<IngredientQuantity> lines = recipe.getIngredients() == null ? List.of() : recipe.getIngredients();
            for (int line = 0; line < lines.size(); line++) {
                IngredientQuantity ingredient = lines.get(line);
                UUID linked = ingredient == null ? null : ingredient.getRecipeId();
                if (linked == null) {
                    continue;
                }
                Integer position = positions.get(linked);
                if (position == null) {
                    throw new IllegalArgumentException(at + ".ingredients[" + line + "]: recipeId " + linked
                            + " is not one of the export's recipes");
                }
                place(position);
            }
            placing.remove(index);
            placed.add(index);
            order.add(index);
        }
    }

    private static ShoppingListRepository.ItemRow toItemRow(ExportedShoppingListItem item) {
        return new ShoppingListRepository.ItemRow(
                present(item.getName(), "name"),
                item.getUnit(),
                fromBigDecimal(item.getQuantity()),
                present(item.getCategory(), "category"),
                present(item.getChecked(), "checked"));
    }

    /**
     * Applies the action to each element, prefixing any validation failure
     * with where it happened ({@code mealPlans[2].entries[0]: ...}).
     */
    private static <T> void eachAt(String field, List<T> elements, Consumer<T> action) {
        for (int i = 0; i < elements.size(); i++) {
            String at = field + "[" + i + "]";
            T element = elements.get(i);
            if (element == null) {
                throw new IllegalArgumentException(at + " is missing");
            }
            at(at, () -> action.accept(element));
        }
    }

    /** Runs the action, prefixing any validation failure with {@code path}. */
    private static void at(String path, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | NoSuchElementException exception) {
            String message = String.valueOf(exception.getMessage());
            String separator = NESTED_PATH.matcher(message).lookingAt() ? "." : ": ";
            throw new IllegalArgumentException(path + separator + message, exception);
        }
    }

    /** A required value the document left out (or set to null). */
    private static <T> T present(@Nullable T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return value;
    }

    private static String count(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static Nutrition fromApiNutrition(org.openapitools.model.Nutrition nutrition) {
        return new Nutrition(
                fromBigDecimal(nutrition.getCalories()),
                fromBigDecimal(nutrition.getProteinG()),
                fromBigDecimal(nutrition.getCarbsG()),
                fromBigDecimal(nutrition.getFatG()));
    }

    private static @Nullable Double fromBigDecimal(@Nullable BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
