package io.github.noahhhx.mimos.recipes.library;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.recipes.recipe.CatalogIngredient;
import io.github.noahhhx.mimos.recipes.recipe.NutritionBasis;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * The shared ingredient catalog seed (ADR-0015): every entry the seeder will
 * load, read the way the seeder reads it, is well formed and its numbers are
 * plausible for what it names.
 */
class IngredientSeedTests {

    /** The catalog as first shipped. Recipes, exports, and tests link to these slugs, so none may go. */
    private static final Set<String> ORIGINAL_SLUGS = Set.of(
            "all-purpose-flour",
            "almonds",
            "baking-powder",
            "baking-soda",
            "banana",
            "beef-mince",
            "bell-pepper",
            "berries",
            "brown-lentils",
            "butter",
            "buttermilk",
            "cabbage",
            "canned-beans",
            "canned-tomatoes",
            "carrot",
            "cayenne",
            "celery-stalk",
            "chicken-thigh-bone-in",
            "chicken-thigh-boneless",
            "chili-flakes",
            "cinnamon",
            "coconut-milk",
            "coriander",
            "cucumber",
            "cumin",
            "curry-powder",
            "egg",
            "feta",
            "garlic-clove",
            "ginger",
            "honey",
            "lemon",
            "lime",
            "milk",
            "oats",
            "olive-oil",
            "olives",
            "onion",
            "oregano",
            "paprika",
            "parmesan",
            "parsley",
            "pasta",
            "potato",
            "red-lentils",
            "rice-vinegar",
            "salmon-fillet",
            "salt",
            "scallion",
            "sesame-seeds",
            "soy-sauce",
            "sugar",
            "tomato",
            "vanilla-extract",
            "vegetable-oil",
            "white-rice",
            "yogurt");

    /**
     * Entries whose calories honestly differ from 4/4/9 kcal per gram of
     * protein, carbs, and fat by more than the tolerance.
     */
    private static final Set<String> ENERGY_EXCEPTIONS = Set.of(
            // USDA counts fibre in carbs; it yields little energy, and these are a quarter to
            // a third fibre: dried spices, cocoa, dried yeast, dried mushrooms.
            "allspice",
            "black-pepper",
            "caraway-seeds",
            "cardamom",
            "cayenne",
            "chili-flakes",
            "chili-powder",
            "cinnamon",
            "coriander",
            "coriander-seeds",
            "cumin",
            "cumin-seeds",
            "curry-powder",
            "fennel-seeds",
            "garam-masala",
            "ground-cloves",
            "mixed-spice",
            "paprika",
            "smoked-paprika",
            "cocoa-powder",
            "active-dry-yeast",
            "dried-shiitake",
            // Alcohol carries 7 kcal per gram and is none of the three.
            "beer",
            "red-wine",
            "white-wine",
            "vanilla-extract",
            // Most of USDA's carbs for baking powder are its mineral salts, which yield nothing.
            "baking-powder");

    /** User-facing copy never uses one (AGENTS.md). */
    private static final int EM_DASH = 0x2014;

    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private static final List<CatalogIngredient> SEED = readSeed();

    private static List<CatalogIngredient> readSeed() {
        try (InputStream in = new ClassPathResource(IngredientCatalogSeeder.SEED_RESOURCE).getInputStream()) {
            return List.of(new ObjectMapper().readValue(in, CatalogIngredient[].class));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void everyOriginalEntryIsStillSeeded() {
        assertThat(SEED).extracting(CatalogIngredient::slug).containsAll(ORIGINAL_SLUGS);
    }

    @Test
    void slugsAreUniqueKebabCaseAndSorted() {
        List<String> slugs = SEED.stream().map(CatalogIngredient::slug).toList();
        assertThat(slugs).doesNotHaveDuplicates().isSorted();
        assertThat(slugs).allMatch(slug -> KEBAB_CASE.matcher(slug).matches(), "kebab-case");
    }

    @Test
    void namesAreUniqueAndFitForUsers() {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = SEED.stream()
                .map(entry -> entry.name().toLowerCase(Locale.ROOT))
                .filter(name -> !seen.add(name))
                .toList();
        assertThat(duplicates).isEmpty();
        assertThat(SEED).extracting(CatalogIngredient::name).noneMatch(name -> name.indexOf(EM_DASH) >= 0);
    }

    @Test
    void weighedEntriesHoldAtMostTheirOwnWeightInMacros() {
        List<String> overweight = SEED.stream()
                .filter(entry -> entry.basis() == NutritionBasis.PER_100_G)
                .filter(entry -> entry.proteinG() + entry.carbsG() + entry.fatG() > 100)
                .map(CatalogIngredient::slug)
                .toList();
        assertThat(overweight).isEmpty();
    }

    @Test
    void caloriesAgreeWithTheMacros() {
        List<String> implausible = SEED.stream()
                .filter(entry -> !ENERGY_EXCEPTIONS.contains(entry.slug()))
                .filter(IngredientSeedTests::caloriesDisagree)
                .map(entry ->
                        "%s (%.0f kcal, macros give %.0f)".formatted(entry.slug(), entry.calories(), fromMacros(entry)))
                .toList();
        assertThat(implausible).isEmpty();
    }

    @Test
    void everyEnergyExceptionIsSeededAndStillNeeded() {
        List<String> needed = SEED.stream()
                .filter(IngredientSeedTests::caloriesDisagree)
                .map(CatalogIngredient::slug)
                .toList();
        assertThat(needed).containsExactlyInAnyOrderElementsOf(ENERGY_EXCEPTIONS);
    }

    /** Off by more than 20 kcal and 15 %: a typo, a swapped column, or values for the wrong basis. */
    private static boolean caloriesDisagree(CatalogIngredient entry) {
        double expected = fromMacros(entry);
        return Math.abs(entry.calories() - expected) > Math.max(20, 0.15 * expected);
    }

    private static double fromMacros(CatalogIngredient entry) {
        return 4 * entry.proteinG() + 4 * entry.carbsG() + 9 * entry.fatG();
    }
}
