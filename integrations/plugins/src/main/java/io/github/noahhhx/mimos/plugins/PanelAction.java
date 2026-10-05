package io.github.noahhhx.mimos.plugins;

import org.jspecify.annotations.Nullable;

/**
 * A button the user pressed: the {@code id} and {@code value} of a
 * {@link PanelButton} (ADR-0017). It comes from the user's browser, so it
 * must pass the button rules ({@link IllegalArgumentException} otherwise),
 * and the plugin still treats it as untrusted.
 */
public record PanelAction(String id, @Nullable String value) {

    public PanelAction {
        id = PanelButton.requireActionId(id);
        value = PanelText.optional(value, PanelButton.MAX_VALUE_LENGTH, "value");
    }
}
