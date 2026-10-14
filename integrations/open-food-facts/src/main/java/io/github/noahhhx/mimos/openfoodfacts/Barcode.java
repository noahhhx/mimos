package io.github.noahhhx.mimos.openfoodfacts;

import java.util.regex.Pattern;

/**
 * A product barcode: 8 to 14 digits (EAN-8, UPC-A, EAN-13, GTIN-14). Only a
 * value of this shape ever reaches Open Food Facts' URL.
 */
public record Barcode(String digits) {

    private static final Pattern DIGITS = Pattern.compile("[0-9]{8,14}");

    public Barcode {
        if (!DIGITS.matcher(digits).matches()) {
            throw new IllegalArgumentException("A barcode is 8 to 14 digits");
        }
    }

    @Override
    public String toString() {
        return digits;
    }
}
