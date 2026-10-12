package io.github.noahhhx.mimos.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** The format gate and the upgrade chain, mostly with made-up steps. */
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
    void versions2And3KeepTheirData() {
        for (int version = 2; version <= 3; version++) {
            ObjectNode old = document(version);
            old.putArray("recipes").addObject().put("title", "Soup").put("nutritionSource", "INGREDIENTS");
            old.putArray("mealPlans")
                    .addObject()
                    .put("startDate", "2026-09-28")
                    .putArray("entries")
                    .addObject()
                    .put("date", "2026-09-28")
                    .put("mealType", "DINNER")
                    .put("librarySlug", "shakshuka")
                    .put("servings", 2);

            ObjectNode upgraded = ExportUpgrader.standard().upgrade(old).document();

            assertThat(upgraded.remove("version").asInt()).isEqualTo(ExportUpgrader.CURRENT_VERSION);
            assertThat(upgraded.remove("trainingSessions")).isEmpty();
            assertEveryRecipeGainedNoPictures(upgraded);
            old.remove("version");
            assertThat(upgraded).as("version %d", version).isEqualTo(old);
        }
    }

    @Test
    void version4GainsAnEmptyTrainingSectionAndNoFuelProfile() throws IOException {
        ObjectNode v4;
        try (InputStream in = new ClassPathResource("export/v4.json").getInputStream()) {
            v4 = (ObjectNode) MAPPER.readTree(in);
        }

        ObjectNode upgraded = ExportUpgrader.standard().upgrade(v4).document();

        assertThat(upgraded.remove("version").asInt()).isEqualTo(ExportUpgrader.CURRENT_VERSION);
        assertThat(upgraded.remove("trainingSessions")).isEmpty();
        assertThat(upgraded.has("fuelProfile")).isFalse();
        assertEveryRecipeGainedNoPictures(upgraded);
        v4.remove("version");
        assertThat(upgraded).isEqualTo(v4);
    }

    @Test
    void version5GivesEveryRecipeAnEmptyPictureList() throws IOException {
        ObjectNode v5;
        try (InputStream in = new ClassPathResource("export/v5.json").getInputStream()) {
            v5 = (ObjectNode) MAPPER.readTree(in);
        }

        ObjectNode upgraded = ExportUpgrader.standard().upgrade(v5).document();

        assertThat(upgraded.remove("version").asInt()).isEqualTo(6);
        assertThat(upgraded.get("recipes")).hasSize(4);
        assertEveryRecipeGainedNoPictures(upgraded);
        v5.remove("version");
        assertThat(upgraded).isEqualTo(v5);
    }

    /** Checks each recipe now has an empty pictures list, then takes it off so the rest can be compared. */
    private static void assertEveryRecipeGainedNoPictures(ObjectNode upgraded) {
        upgraded.path("recipes").forEach(recipe -> {
            JsonNode pictures = ((ObjectNode) recipe).remove("pictures");
            assertThat(pictures).isNotNull();
            assertThat(pictures.isArray()).isTrue();
            assertThat(pictures).isEmpty();
        });
    }

    @Test
    void theStandardUpgraderExportsTheCurrentVersion() {
        assertThat(ExportUpgrader.standard().currentVersion()).isEqualTo(ExportUpgrader.CURRENT_VERSION);
    }

    private static ObjectNode document(int version) {
        return MAPPER.createObjectNode().put("format", ExportUpgrader.FORMAT).put("version", version);
    }
}
