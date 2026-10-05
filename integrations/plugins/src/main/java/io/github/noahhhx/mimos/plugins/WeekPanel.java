package io.github.noahhhx.mimos.plugins;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One plugin's validated panel for a week, attributed to the plugin
 * (ADR-0017).
 *
 * @param summary the line shown while the panel is collapsed, or {@code null}
 */
public record WeekPanel(
        String pluginId, String pluginName, @Nullable PanelSummary summary, List<PanelBlock> blocks) {

    static final int MAX_BLOCKS = 8;

    public WeekPanel {
        blocks = List.copyOf(blocks);
        if (blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("a panel has at most " + MAX_BLOCKS + " blocks");
        }
    }
}
