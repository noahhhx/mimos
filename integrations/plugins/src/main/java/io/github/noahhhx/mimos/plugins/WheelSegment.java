package io.github.noahhhx.mimos.plugins;

import org.jspecify.annotations.Nullable;

/** One segment of a panel's wheel (ADR-0017). */
public record WheelSegment(String label, @Nullable String icon) {

    static final int MAX_LABEL_LENGTH = 60;

    public WheelSegment {
        label = PanelText.required(label, MAX_LABEL_LENGTH, "label");
        icon = PanelText.optional(icon, PanelText.MAX_ICON_LENGTH, "icon");
    }
}
