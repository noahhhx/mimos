package io.github.noahhhx.mimos.api.account;

import java.util.List;
import java.util.function.UnaryOperator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Brings an account export of any supported format version up to the
 * current one (ADR-0011). Each step turns version N into N + 1 on the raw
 * JSON tree, before anything is bound to the current model, so an old
 * document is upgraded rather than misread.
 *
 * <p>Changing the export's shape means: append a step here (the current
 * version follows from the number of steps), update {@code AccountExport}
 * in the OpenAPI contract, and add a frozen {@code export/v<N>.json}
 * fixture. Never edit or remove a step, or old exports stop importing.
 */
public final class ExportUpgrader {

    public static final String FORMAT = "mimos.export";

    /** {@code STEPS.get(i)} upgrades version {@code i + 1} to {@code i + 2}. */
    private static final List<UnaryOperator<ObjectNode>> STEPS =
            List.of(ExportUpgrader::markNutritionManual, ExportUpgrader::allowRecipeLinks);

    /** The version this instance exports. */
    public static final int CURRENT_VERSION = STEPS.size() + 1;

    private final List<UnaryOperator<ObjectNode>> steps;

    ExportUpgrader(List<UnaryOperator<ObjectNode>> steps) {
        this.steps = List.copyOf(steps);
    }

    /** The upgrader with every step this instance knows. */
    public static ExportUpgrader standard() {
        return new ExportUpgrader(STEPS);
    }

    int currentVersion() {
        return steps.size() + 1;
    }

    /** Checks the document is a supported export and upgrades a copy of it to the current version. */
    public Upgraded upgrade(JsonNode document) {
        if (!(document instanceof ObjectNode exported)
                || !document.path("format").isString()
                || !FORMAT.equals(document.path("format").asString())) {
            throw new IllegalArgumentException("This is not a Mimos export: it needs \"format\": \"" + FORMAT + "\".");
        }
        JsonNode versionNode = exported.path("version");
        if (!versionNode.isIntegralNumber() || !versionNode.canConvertToInt() || versionNode.asInt() < 1) {
            throw new IllegalArgumentException("The export has no valid format version.");
        }
        int version = versionNode.asInt();
        if (version > currentVersion()) {
            throw new IllegalArgumentException("This export was made by a newer version of Mimos (format version "
                    + version + "); this instance reads versions up to " + currentVersion()
                    + ". Upgrade Mimos, then import again.");
        }
        ObjectNode upgraded = exported.deepCopy();
        for (int from = version; from < currentVersion(); from++) {
            upgraded = steps.get(from - 1).apply(upgraded);
            upgraded.put("version", from + 1);
        }
        return new Upgraded(version, upgraded);
    }

    /**
     * Version 2 (ADR-0015, ADR-0016): every recipe says where its nutrition
     * comes from, and all version 1 nutrition was typed; the user's own
     * ingredients get a section, empty before they existed.
     */
    private static ObjectNode markNutritionManual(ObjectNode document) {
        document.putArray("ingredients");
        document.path("recipes").forEach(recipe -> {
            if (recipe instanceof ObjectNode exported) {
                exported.put("nutritionSource", "MANUAL");
            }
        });
        return document;
    }

    /**
     * Version 3 (ADR-0018): an ingredient line may link another recipe in
     * the document by its {@code id}. Version 2 had no such links, so its
     * data comes through unchanged.
     */
    private static ObjectNode allowRecipeLinks(ObjectNode document) {
        return document;
    }

    /** A document at the current version, and the version it was exported in. */
    public record Upgraded(int sourceVersion, ObjectNode document) {}
}
