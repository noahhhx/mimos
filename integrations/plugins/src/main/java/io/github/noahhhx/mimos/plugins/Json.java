package io.github.noahhhx.mimos.plugins;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Defensive tree-model reads for plugin-facing JSON. Plugin responses are
 * untrusted: nothing is data-bound, and every field is extracted with its
 * validity checked by the caller (ADR-0006).
 */
final class Json {

    private Json() {}

    /** A non-blank textual field, or {@code null} when absent, blank, or not a string. */
    static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            return null;
        }
        String text = value.asString();
        return text.isBlank() ? null : text;
    }

    /**
     * A textual array field. With {@code requireElements} an absent or empty
     * array is a protocol error; otherwise an absent array reads as empty.
     */
    static List<String> stringArray(JsonNode node, String field, boolean requireElements) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            if (requireElements) {
                throw new PluginProtocolException("manifest field '" + field + "' must be a non-empty array");
            }
            return List.of();
        }
        List<String> result = new ArrayList<>(value.size());
        for (JsonNode item : value) {
            if (!item.isString() || item.asString().isBlank()) {
                throw new PluginProtocolException("manifest field '" + field + "' must contain only strings");
            }
            result.add(item.asString());
        }
        if (requireElements && result.isEmpty()) {
            throw new PluginProtocolException("manifest field '" + field + "' must be a non-empty array");
        }
        return List.copyOf(result);
    }

    /** A numeric field as a double, or {@code null} when absent or not a number. */
    static @Nullable Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }
}
