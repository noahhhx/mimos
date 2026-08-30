package io.github.noahhhx.mimos.recipes.recipe;

/**
 * Raised when a curated library recipe is targeted for mutation; library
 * recipes are read-only for everyone (only the seed pipeline writes them).
 */
public class ReadOnlyRecipeException extends RuntimeException {

    public ReadOnlyRecipeException(String message) {
        super(message);
    }
}
