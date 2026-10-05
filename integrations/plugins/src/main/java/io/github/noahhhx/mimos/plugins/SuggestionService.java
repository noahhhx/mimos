package io.github.noahhhx.mimos.plugins;

import io.github.noahhhx.mimos.planning.plan.MealPlanService;
import io.github.noahhhx.mimos.planning.plan.MealType;
import io.github.noahhhx.mimos.recipes.recipe.Recipe;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The plan-suggestions capability (ADR-0006): assembles the context from
 * core's public interfaces, fans out to the registered plugins, and
 * validates every card before it reaches a user. Only the plugins the
 * owner turned on are called (ADR-0013); one plugin's failure never
 * affects another's; a week with no healthy plugins simply has no
 * suggestions.
 */
@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    static final int MAX_TITLE_LENGTH = 80;
    static final int MAX_BLURB_LENGTH = 200;
    static final int MAX_ICON_LENGTH = 8;
    static final int MAX_CARDS_PER_PLUGIN = 5;
    static final int MAX_ENTRIES_PER_CARD = 7;

    private final MealPlanService mealPlanService;
    private final RecipeService recipeService;
    private final PluginRegistry registry;
    private final PluginOptInService optIns;

    public SuggestionService(
            MealPlanService mealPlanService,
            RecipeService recipeService,
            PluginRegistry registry,
            PluginOptInService optIns) {
        this.mealPlanService = mealPlanService;
        this.recipeService = recipeService;
        this.registry = registry;
        this.optIns = optIns;
    }

    /** Validated, attributed suggestion cards for the owner's week, in registration order. */
    public List<PlanSuggestion> suggestions(UUID ownerProfileId, LocalDate startDate) {
        requireMonday(startDate);
        // Opt-in is consent (ADR-0013): a plugin the owner has not turned on
        // never receives their context.
        Set<String> enabled = optIns.enabledPluginIds(ownerProfileId);
        List<Plugin> plugins = registry.pluginsWithCapability(PluginManifest.CAPABILITY_PLAN_SUGGESTIONS).stream()
                .filter(plugin -> {
                    PluginManifest manifest = plugin.manifest();
                    return manifest != null && enabled.contains(manifest.id());
                })
                .toList();
        if (plugins.isEmpty()) {
            return List.of();
        }

        List<Recipe> library = recipeService.findLibrary(null);
        Map<UUID, Recipe> libraryById = new HashMap<>();
        for (Recipe recipe : library) {
            libraryById.put(recipe.id(), recipe);
        }
        // Personal recipes contribute their slot shape only — never their
        // identity or content (ADR-0006).
        List<PlannedSlot> plannedSlots = mealPlanService.plannedMeals(ownerProfileId, startDate).stream()
                .map(meal -> new PlannedSlot(
                        meal.date(),
                        meal.mealType(),
                        meal.servings(),
                        // recipeTitle is the user's own data; only the id of
                        // a *library* recipe may cross the boundary.
                        libraryById.containsKey(meal.recipeId()) ? meal.recipeId() : null))
                .toList();
        List<LibraryRecipe> catalog = library.stream()
                .map(recipe -> new LibraryRecipe(recipe.id(), recipe.title(), recipe.tags(), recipe.servings()))
                .toList();
        SuggestionContext context = new SuggestionContext(startDate, plannedSlots, catalog);

        List<PlanSuggestion> result = new ArrayList<>();
        for (Plugin plugin : plugins) {
            PluginManifest manifest = plugin.manifest();
            if (manifest == null) {
                continue;
            }
            try {
                result.addAll(validatedCards(manifest, plugin.client().fetchSuggestions(context), context));
            } catch (RuntimeException exception) {
                // Contained: this plugin contributes nothing this request.
                // Invalidate the manifest — the plugin may have restarted
                // with a different one (ADR-0006).
                plugin.invalidate();
                log.warn("plugin {} failed during suggestion fan-out: {}", manifest.id(), exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    /** Validates and truncates one plugin's cards against the context it was given. */
    static List<PlanSuggestion> validatedCards(
            PluginManifest manifest, List<PluginCard> cards, SuggestionContext context) {
        Map<UUID, String> titlesById = new HashMap<>();
        for (LibraryRecipe recipe : context.libraryRecipes()) {
            titlesById.put(recipe.id(), recipe.title());
        }
        LocalDate start = context.weekStartDate();
        List<PlanSuggestion> result = new ArrayList<>();
        for (PluginCard card : cards) {
            if (result.size() == MAX_CARDS_PER_PLUGIN) {
                break;
            }
            String title = card.title();
            if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
                continue;
            }
            String blurb = card.blurb();
            if (blurb != null && blurb.length() > MAX_BLURB_LENGTH) {
                continue;
            }
            String icon = card.icon();
            if (icon != null && icon.length() > MAX_ICON_LENGTH) {
                continue;
            }
            List<SuggestedEntry> entries = new ArrayList<>();
            for (PluginCardEntry entry : card.entries()) {
                if (entries.size() == MAX_ENTRIES_PER_CARD) {
                    break;
                }
                SuggestedEntry validated = validateEntry(entry, start, titlesById);
                if (validated != null) {
                    entries.add(validated);
                }
            }
            if (entries.isEmpty()) {
                continue;
            }
            result.add(new PlanSuggestion(manifest.id(), manifest.name(), title, blurb, icon, List.copyOf(entries)));
        }
        return List.copyOf(result);
    }

    /**
     * One entry's rules (ADR-0006): inside the plan week, a valid meal type,
     * servings in the plan domain's bound, and a recipe from the catalog
     * that was sent. Invalid entries are dropped.
     */
    static @Nullable SuggestedEntry validateEntry(
            PluginCardEntry entry, LocalDate start, Map<UUID, String> titlesById) {
        String dateText = entry.date();
        LocalDate date = null;
        try {
            date = dateText == null ? null : LocalDate.parse(dateText);
        } catch (RuntimeException exception) {
            date = null;
        }
        if (date == null || date.isBefore(start) || date.isAfter(start.plusDays(6))) {
            return null;
        }
        MealType mealType = null;
        if (entry.mealType() != null) {
            try {
                mealType = MealType.valueOf(entry.mealType());
            } catch (IllegalArgumentException exception) {
                mealType = null;
            }
        }
        if (mealType == null) {
            return null;
        }
        UUID recipeId = null;
        if (entry.recipeId() != null) {
            try {
                recipeId = UUID.fromString(entry.recipeId());
            } catch (IllegalArgumentException exception) {
                recipeId = null;
            }
        }
        if (recipeId == null) {
            return null;
        }
        String recipeTitle = titlesById.get(recipeId);
        if (recipeTitle == null) {
            return null;
        }
        Double servings = entry.servings();
        if (servings == null || servings.isNaN() || servings <= 0 || servings > MealPlanService.MAX_SERVINGS) {
            return null;
        }
        return new SuggestedEntry(date, mealType, recipeId, recipeTitle, servings);
    }

    private static void requireMonday(LocalDate startDate) {
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("startDate must be the Monday of the week");
        }
    }
}
