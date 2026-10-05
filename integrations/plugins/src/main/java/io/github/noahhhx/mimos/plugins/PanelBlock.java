package io.github.noahhhx.mimos.plugins;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One block of a week panel: the closed vocabulary core validates and web
 * renders as plain text (ADR-0017). Each record enforces the contract's
 * limits, so a block that exists is a block a user may see.
 */
public sealed interface PanelBlock {

    record Text(String text) implements PanelBlock {

        static final int MAX_TEXT_LENGTH = 280;

        public Text {
            text = PanelText.required(text, MAX_TEXT_LENGTH, "text");
        }
    }

    /** One thing shown large, such as a chosen country. */
    record Highlight(
            @Nullable String icon, String title, @Nullable String text) implements PanelBlock {

        static final int MAX_TITLE_LENGTH = 80;
        static final int MAX_TEXT_LENGTH = 200;

        public Highlight {
            icon = PanelText.optional(icon, PanelText.MAX_ICON_LENGTH, "icon");
            title = PanelText.required(title, MAX_TITLE_LENGTH, "title");
            text = PanelText.optional(text, MAX_TEXT_LENGTH, "text");
        }
    }

    /**
     * A wheel of segments. With {@code landing}, the wheel spins and stops on
     * that segment: the plugin chose it, web only animates to it.
     */
    record Wheel(List<WheelSegment> segments, @Nullable Integer landing) implements PanelBlock {

        static final int MIN_SEGMENTS = 2;
        static final int MAX_SEGMENTS = 60;

        public Wheel {
            segments = List.copyOf(segments);
            if (segments.size() < MIN_SEGMENTS || segments.size() > MAX_SEGMENTS) {
                throw new IllegalArgumentException("a wheel has " + MIN_SEGMENTS + " to " + MAX_SEGMENTS + " segments");
            }
            if (landing != null && (landing < 0 || landing >= segments.size())) {
                throw new IllegalArgumentException("landing must be the index of a segment");
            }
        }
    }

    /** A row of buttons. */
    record Actions(List<PanelButton> buttons) implements PanelBlock {

        static final int MAX_BUTTONS = 4;

        public Actions {
            buttons = List.copyOf(buttons);
            if (buttons.isEmpty() || buttons.size() > MAX_BUTTONS) {
                throw new IllegalArgumentException("an actions block has 1 to " + MAX_BUTTONS + " buttons");
            }
        }
    }
}
