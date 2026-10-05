package io.github.noahhhx.mimos.plugins;

import org.jspecify.annotations.Nullable;

/** The line a week panel shows while it is collapsed, such as the week's country and its flag (ADR-0017). */
public record PanelSummary(@Nullable String icon, String label) {

    static final int MAX_LABEL_LENGTH = 60;

    public PanelSummary {
        icon = PanelText.optional(icon, PanelText.MAX_ICON_LENGTH, "icon");
        label = PanelText.required(label, MAX_LABEL_LENGTH, "label");
    }
}
