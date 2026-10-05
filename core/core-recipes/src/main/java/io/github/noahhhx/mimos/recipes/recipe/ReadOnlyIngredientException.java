package io.github.noahhhx.mimos.recipes.recipe;

/** Raised when a shared catalog ingredient is targeted for mutation; only the seed pipeline writes them. */
public class ReadOnlyIngredientException extends RuntimeException {

    public ReadOnlyIngredientException(String message) {
        super(message);
    }
}
