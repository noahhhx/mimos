package io.github.noahhhx.mimos.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Reads a plugin's {@code /v1/week-panel} response into a {@link WeekPanel}
 * (ADR-0017). The response is untrusted: a body that is not a panel at all
 * is a {@link PluginProtocolException}, while an invalid summary or block,
 * or a block of a type this Mimos does not know, is dropped so a newer
 * plugin degrades on an older core. The limits themselves live in the
 * domain records; this class only reads the tree into them.
 */
final class WeekPanelParser {

    private static final Logger log = LoggerFactory.getLogger(WeekPanelParser.class);

    private WeekPanelParser() {}

    static WeekPanel parse(PluginManifest manifest, @Nullable JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new PluginProtocolException("week panel from " + manifest.id() + " is not a JSON object");
        }
        JsonNode blocksNode = body.get("blocks");
        if (blocksNode == null || !blocksNode.isArray()) {
            throw new PluginProtocolException("week panel from " + manifest.id() + " has no 'blocks' array");
        }
        List<PanelBlock> blocks = new ArrayList<>();
        for (JsonNode blockNode : blocksNode) {
            if (blocks.size() == WeekPanel.MAX_BLOCKS) {
                break;
            }
            try {
                blocks.add(block(blockNode));
            } catch (IllegalArgumentException invalid) {
                log.debug("plugin {} sent an invalid panel block; dropped: {}", manifest.id(), invalid.getMessage());
            }
        }
        PanelSummary summary = null;
        JsonNode summaryNode = body.get("summary");
        if (summaryNode != null && !summaryNode.isNull()) {
            try {
                summary = summary(summaryNode);
            } catch (IllegalArgumentException invalid) {
                log.debug("plugin {} sent an invalid panel summary; dropped: {}", manifest.id(), invalid.getMessage());
            }
        }
        return new WeekPanel(manifest.id(), manifest.name(), summary, blocks);
    }

    private static PanelSummary summary(JsonNode node) {
        requireObject(node, "summary");
        return new PanelSummary(Json.text(node, "icon"), requiredText(node, "label"));
    }

    private static PanelBlock block(JsonNode node) {
        requireObject(node, "block");
        String type = Json.text(node, "type");
        return switch (type == null ? "" : type) {
            case "text" -> new PanelBlock.Text(requiredText(node, "text"));
            case "highlight" ->
                new PanelBlock.Highlight(Json.text(node, "icon"), requiredText(node, "title"), Json.text(node, "text"));
            case "wheel" ->
                new PanelBlock.Wheel(
                        objects(
                                node,
                                "segments",
                                segment ->
                                        new WheelSegment(requiredText(segment, "label"), Json.text(segment, "icon"))),
                        landing(node));
            case "actions" -> new PanelBlock.Actions(objects(node, "actions", WeekPanelParser::button));
            default -> throw new IllegalArgumentException("unknown block type '" + type + "'");
        };
    }

    private static PanelButton button(JsonNode node) {
        JsonNode primary = node.get("primary");
        return new PanelButton(
                requiredText(node, "id"),
                requiredText(node, "label"),
                Json.text(node, "value"),
                primary != null && primary.isBoolean() && primary.booleanValue());
    }

    private static @Nullable Integer landing(JsonNode wheel) {
        JsonNode landing = wheel.get("landing");
        if (landing == null || landing.isNull()) {
            return null;
        }
        if (!landing.isIntegralNumber() || !landing.canConvertToInt()) {
            throw new IllegalArgumentException("landing must be an integer");
        }
        return landing.intValue();
    }

    private static <T> List<T> objects(JsonNode node, String field, Function<JsonNode, T> read) {
        JsonNode array = node.get(field);
        if (array == null || !array.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        List<T> result = new ArrayList<>(array.size());
        for (JsonNode item : array) {
            requireObject(item, field + " item");
            result.add(read.apply(item));
        }
        return result;
    }

    private static String requiredText(JsonNode node, String field) {
        String text = Json.text(node, field);
        if (text == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }

    private static void requireObject(JsonNode node, String what) {
        if (!node.isObject()) {
            throw new IllegalArgumentException(what + " must be an object");
        }
    }
}
