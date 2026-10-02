package io.github.noahhhx.mimos.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** The format gate and the upgrade chain, with made-up steps (production has none yet). */
class ExportUpgraderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** v1 renames "notes" to "remarks"; v2 adds an empty "goals" section. */
    private static final List<UnaryOperator<ObjectNode>> STEPS = List.of(
            document -> {
                document.set("remarks", document.remove("notes"));
                return document;
            },
            document -> {
                document.set("goals", MAPPER.createArrayNode());
                return document;
            });

    private final ExportUpgrader upgrader = new ExportUpgrader(STEPS);

    @Test
    void upgradesAnOldDocumentStepByStep() {
        ObjectNode v1 = document(1).put("notes", "kept");

        ExportUpgrader.Upgraded upgraded = upgrader.upgrade(v1);

        assertThat(upgraded.sourceVersion()).isEqualTo(1);
        assertThat(upgraded.document().get("version").asInt()).isEqualTo(3);
        assertThat(upgraded.document().get("remarks").asString()).isEqualTo("kept");
        assertThat(upgraded.document().has("notes")).isFalse();
        assertThat(upgraded.document().get("goals").isArray()).isTrue();
        // The caller's document is left as it was.
        assertThat(v1.get("version").asInt()).isEqualTo(1);
        assertThat(v1.has("notes")).isTrue();
    }

    @Test
    void startsFromTheDocumentsOwnVersion() {
        ExportUpgrader.Upgraded upgraded = upgrader.upgrade(document(2));

        assertThat(upgraded.sourceVersion()).isEqualTo(2);
        assertThat(upgraded.document().get("version").asInt()).isEqualTo(3);
        assertThat(upgraded.document().has("remarks")).isFalse();
        assertThat(upgraded.document().get("goals").isArray()).isTrue();
    }

    @Test
    void passesTheCurrentVersionThrough() {
        ObjectNode current = document(3).put("notes", "untouched");

        assertThat(upgrader.upgrade(current).document()).isEqualTo(current);
    }

    @Test
    void refusesANewerVersion() {
        assertThatThrownBy(() -> upgrader.upgrade(document(4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newer version of Mimos")
                .hasMessageContaining("up to 3");
    }

    @Test
    void refusesWhatIsNotAnExport() {
        assertThatThrownBy(() -> upgrader.upgrade(MAPPER.createArrayNode()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a Mimos export");
        assertThatThrownBy(() -> upgrader.upgrade(MAPPER.createObjectNode().put("format", "other")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a Mimos export");
        assertThatThrownBy(() -> upgrader.upgrade(document(1).put("version", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no valid format version");
        assertThatThrownBy(() -> upgrader.upgrade(document(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no valid format version");
    }

    @Test
    void theStandardUpgraderExportsTheCurrentVersion() {
        assertThat(ExportUpgrader.standard().currentVersion()).isEqualTo(ExportUpgrader.CURRENT_VERSION);
    }

    private static ObjectNode document(int version) {
        return MAPPER.createObjectNode().put("format", ExportUpgrader.FORMAT).put("version", version);
    }
}
