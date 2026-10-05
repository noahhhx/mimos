package io.github.noahhhx.mimos.plugins;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A button in a panel's {@code actions} block (ADR-0017). Pressing it sends
 * its {@code id} and {@code value} back to the plugin as a {@link PanelAction}.
 *
 * @param primary whether to draw it as the panel's main action
 */
public record PanelButton(String id, String label, @Nullable String value, boolean primary) {

    static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,39}$");
    static final int MAX_LABEL_LENGTH = 40;
    static final int MAX_VALUE_LENGTH = 100;

    public PanelButton {
        id = requireActionId(id);
        label = PanelText.required(label, MAX_LABEL_LENGTH, "label");
        value = PanelText.optional(value, MAX_VALUE_LENGTH, "value");
    }

    static String requireActionId(String id) {
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "id must be 1 to 40 lowercase letters, digits, or hyphens, starting with a letter or digit");
        }
        return id;
    }
}
