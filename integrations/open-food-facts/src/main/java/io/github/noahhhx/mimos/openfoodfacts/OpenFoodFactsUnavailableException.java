package io.github.noahhhx.mimos.openfoodfacts;

/** Open Food Facts did not answer, or answered with an error, so the barcode could not be looked up. */
public class OpenFoodFactsUnavailableException extends RuntimeException {

    OpenFoodFactsUnavailableException(String message) {
        super(message);
    }
}
