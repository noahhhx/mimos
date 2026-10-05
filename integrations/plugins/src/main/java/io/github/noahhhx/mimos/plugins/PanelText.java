package io.github.noahhhx.mimos.plugins;

import org.jspecify.annotations.Nullable;

/**
 * Length rules for the strings in a week panel (ADR-0017). Lengths count
 * Unicode code points, as the contract's JSON Schema {@code maxLength}
 * does, so a flag emoji is two characters rather than four.
 */
final class PanelText {

    static final int MAX_ICON_LENGTH = 8;

    private PanelText() {}

    /** {@code value}, or {@link IllegalArgumentException} when blank or longer than {@code max}. */
    static String required(String value, int max, String field) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        requireAtMost(value, max, field);
        return value;
    }

    /** {@code value}, or {@link IllegalArgumentException} when longer than {@code max}. */
    static @Nullable String optional(@Nullable String value, int max, String field) {
        if (value != null) {
            requireAtMost(value, max, field);
        }
        return value;
    }

    private static void requireAtMost(String value, int max, String field) {
        if (value.codePointCount(0, value.length()) > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        }
    }
}
