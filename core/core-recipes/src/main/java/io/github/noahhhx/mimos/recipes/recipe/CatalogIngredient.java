package io.github.noahhhx.mimos.recipes.recipe;

/**
 * An entry in the instance's shared ingredient catalog (ADR-0015): its
 * nutrition for one {@link NutritionBasis}, every value known.
 */
public record CatalogIngredient(
        String slug, String name, NutritionBasis basis, double calories, double proteinG, double carbsG, double fatG) {

    public CatalogIngredient {
        if (slug.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("a catalog ingredient needs a slug and a name");
        }
        if (calories < 0 || proteinG < 0 || carbsG < 0 || fatG < 0) {
            throw new IllegalArgumentException("catalog ingredient " + slug + " has a negative nutrition value");
        }
    }

    public Nutrition nutrition() {
        return new Nutrition(calories, proteinG, carbsG, fatG);
    }
}
