package io.github.noahhhx.mimos.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Week panel parsing (ADR-0017): untrusted plugin output becomes a panel
 * only within the contract's limits. Each limit is checked at its bound and
 * one past it, so an off-by-one in either direction fails a test.
 */
class WeekPanelParserTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final PluginManifest MANIFEST = new PluginManifest(
            PluginManifest.SCHEMA, "stub-plugin", "Stub Plugin", "1.0.0", List.of("1"), List.of("week-panel"), null);
    // Eight code points, sixteen UTF-16 units: four flags.
    private static final String EIGHT_CODE_POINT_ICON = "🇵🇪🇵🇪🇵🇪🇵🇪";

    private static WeekPanel parse(String json) {
        return WeekPanelParser.parse(MANIFEST, MAPPER.readTree(json));
    }

    private static List<PanelBlock> blocks(String... blockJson) {
        return parse("{\"blocks\":[" + String.join(",", blockJson) + "]}").blocks();
    }

    private static void assertKept(String blockJson) {
        assertThat(blocks(blockJson)).as(blockJson).hasSize(1);
    }

    private static void assertDropped(String blockJson) {
        assertThat(blocks(blockJson)).as(blockJson).isEmpty();
    }

    private static String text(String text) {
        return "{\"type\":\"text\",\"text\":\"" + text + "\"}";
    }

    private static String wheel(int segments, String landing) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            items.add("{\"label\":\"Country " + i + "\"}");
        }
        return "{\"type\":\"wheel\",\"segments\":[" + String.join(",", items) + "]" + landing + "}";
    }

    private static String actions(String... buttons) {
        return "{\"type\":\"actions\",\"actions\":[" + String.join(",", buttons) + "]}";
    }

    private static String button(String id, String label) {
        return "{\"id\":\"" + id + "\",\"label\":\"" + label + "\"}";
    }

    @Test
    void everyBlockTypeParsesIntoItsRecord() {
        WeekPanel panel = parse("""
                {"summary":{"icon":"🇵🇪","label":"Peru"},
                 "blocks":[
                   {"type":"text","text":"Spin to pick a country."},
                   {"type":"highlight","icon":"🇵🇪","title":"Peru","text":"South America"},
                   {"type":"wheel","segments":[{"label":"Peru","icon":"🇵🇪"},{"label":"Chile"}],"landing":1},
                   {"type":"actions","actions":[
                     {"id":"choose","label":"Choose","value":"PE","primary":true},
                     {"id":"skip","label":"Skip"}]}]}
                """);
        assertThat(panel.pluginId()).isEqualTo("stub-plugin");
        assertThat(panel.pluginName()).isEqualTo("Stub Plugin");
        assertThat(panel.summary()).isEqualTo(new PanelSummary("🇵🇪", "Peru"));
        assertThat(panel.blocks())
                .containsExactly(
                        new PanelBlock.Text("Spin to pick a country."),
                        new PanelBlock.Highlight("🇵🇪", "Peru", "South America"),
                        new PanelBlock.Wheel(
                                List.of(new WheelSegment("Peru", "🇵🇪"), new WheelSegment("Chile", null)), 1),
                        new PanelBlock.Actions(List.of(
                                new PanelButton("choose", "Choose", "PE", true),
                                new PanelButton("skip", "Skip", null, false))));
    }

    @Test
    void aBodyThatIsNotAPanelIsAProtocolError() {
        for (String body : List.of("[]", "\"panel\"", "{}", "{\"blocks\":{}}", "{\"blocks\":null}")) {
            assertThatThrownBy(() -> parse(body)).as(body).isInstanceOf(PluginProtocolException.class);
        }
        assertThatThrownBy(() -> WeekPanelParser.parse(MANIFEST, null)).isInstanceOf(PluginProtocolException.class);
    }

    @Test
    void unknownAndMalformedBlocksAreDroppedAndTheRestKept() {
        List<PanelBlock> result = blocks(
                "{\"type\":\"video\",\"url\":\"https://example.com\"}",
                "{\"text\":\"no type\"}",
                "\"just a string\"",
                "{\"type\":\"text\"}",
                "{\"type\":\"text\",\"text\":\"   \"}",
                "{\"type\":\"highlight\",\"text\":\"no title\"}",
                text("kept"));
        assertThat(result).containsExactly(new PanelBlock.Text("kept"));
    }

    @Test
    void aPanelHasAtMostEightBlocksCountingOnlyValidOnes() {
        List<String> nine = new ArrayList<>();
        nine.add("{\"type\":\"text\"}");
        for (int i = 0; i < 9; i++) {
            nine.add(text("block " + i));
        }
        List<PanelBlock> result = blocks(nine.toArray(String[]::new));
        assertThat(result).hasSize(8);
        assertThat(result.get(0)).isEqualTo(new PanelBlock.Text("block 0"));
        assertThat(result.get(7)).isEqualTo(new PanelBlock.Text("block 7"));
    }

    @Test
    void textIsAtMost280Characters() {
        assertKept(text("x".repeat(280)));
        assertDropped(text("x".repeat(281)));
    }

    @Test
    void highlightLimits() {
        assertKept("{\"type\":\"highlight\",\"title\":\"" + "x".repeat(80) + "\"}");
        assertDropped("{\"type\":\"highlight\",\"title\":\"" + "x".repeat(81) + "\"}");
        assertKept("{\"type\":\"highlight\",\"title\":\"Peru\",\"text\":\"" + "x".repeat(200) + "\"}");
        assertDropped("{\"type\":\"highlight\",\"title\":\"Peru\",\"text\":\"" + "x".repeat(201) + "\"}");
        assertKept("{\"type\":\"highlight\",\"title\":\"Peru\",\"icon\":\"" + EIGHT_CODE_POINT_ICON + "\"}");
        assertDropped("{\"type\":\"highlight\",\"title\":\"Peru\",\"icon\":\"" + "x".repeat(9) + "\"}");
    }

    @Test
    void wheelLimits() {
        assertDropped(wheel(1, ""));
        assertKept(wheel(2, ""));
        assertKept(wheel(60, ""));
        assertDropped(wheel(61, ""));
        assertKept(wheel(3, ",\"landing\":0"));
        assertKept(wheel(3, ",\"landing\":2"));
        assertDropped(wheel(3, ",\"landing\":3"));
        assertDropped(wheel(3, ",\"landing\":-1"));
        assertDropped(wheel(3, ",\"landing\":1.5"));
        assertDropped(wheel(3, ",\"landing\":\"1\""));
        assertThat(((PanelBlock.Wheel) blocks(wheel(3, "")).get(0)).landing()).isNull();
        assertKept("{\"type\":\"wheel\",\"segments\":[{\"label\":\"" + "x".repeat(60) + "\"},{\"label\":\"b\"}]}");
        assertDropped("{\"type\":\"wheel\",\"segments\":[{\"label\":\"" + "x".repeat(61) + "\"},{\"label\":\"b\"}]}");
        assertDropped("{\"type\":\"wheel\",\"segments\":[{\"icon\":\"🇵🇪\"},{\"label\":\"b\"}]}");
        assertDropped("{\"type\":\"wheel\",\"segments\":[{\"label\":\"a\",\"icon\":\"" + "x".repeat(9)
                + "\"},{\"label\":\"b\"}]}");
        assertDropped("{\"type\":\"wheel\",\"segments\":[\"a\",\"b\"]}");
    }

    @Test
    void actionsLimits() {
        assertDropped(actions());
        assertKept(actions(button("a", "A"), button("b", "B"), button("c", "C"), button("d", "D")));
        assertDropped(
                actions(button("a", "A"), button("b", "B"), button("c", "C"), button("d", "D"), button("e", "E")));
        assertKept(actions(button("a" + "-".repeat(39), "Long id")));
        assertDropped(actions(button("a" + "-".repeat(40), "Long id")));
        assertDropped(actions(button("Spin", "Uppercase")));
        assertDropped(actions(button("-spin", "Leading hyphen")));
        assertDropped(actions(button("spin it", "Space")));
        assertKept(actions(button("spin", "x".repeat(40))));
        assertDropped(actions(button("spin", "x".repeat(41))));
        assertDropped(actions("{\"id\":\"spin\"}"));
        assertKept(actions("{\"id\":\"spin\",\"label\":\"Spin\",\"value\":\"" + "x".repeat(100) + "\"}"));
        assertDropped(actions("{\"id\":\"spin\",\"label\":\"Spin\",\"value\":\"" + "x".repeat(101) + "\"}"));
    }

    @Test
    void anInvalidSummaryIsDroppedAndTheBlocksKept() {
        String block = text("kept");
        for (String summary : List.of(
                "{\"label\":\"" + "x".repeat(61) + "\"}",
                "{\"icon\":\"🇵🇪\"}",
                "{\"label\":\"Peru\",\"icon\":\"" + "x".repeat(9) + "\"}",
                "\"Peru\"")) {
            WeekPanel panel = parse("{\"summary\":" + summary + ",\"blocks\":[" + block + "]}");
            assertThat(panel.summary()).as(summary).isNull();
            assertThat(panel.blocks()).as(summary).hasSize(1);
        }
        WeekPanel atLimit = parse("{\"summary\":{\"label\":\"" + "x".repeat(60) + "\",\"icon\":\""
                + EIGHT_CODE_POINT_ICON + "\"},\"blocks\":[]}");
        assertThat(atLimit.summary()).isNotNull();
    }

    @Test
    void anActionFromTheBrowserFollowsTheButtonRules() {
        assertThat(new PanelAction("choose", "x".repeat(100)).value()).hasSize(100);
        assertThat(new PanelAction("choose", null).value()).isNull();
        for (String id : List.of("", "Choose", "-choose", "a".repeat(41), "choose!")) {
            assertThatThrownBy(() -> new PanelAction(id, null)).as(id).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new PanelAction("choose", "x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("value must be at most 100 characters");
    }
}
