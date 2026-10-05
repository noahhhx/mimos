package io.github.noahhhx.mimos.plugins;

import java.util.regex.Pattern;

/**
 * A plugin's {@code GET /manifest} response, validated at parse time
 * (ADR-0006). Static misconfiguration — a configured id that does not
 * match, or duplicate ids across the registry — fails startup; an
 * unreachable or malformed manifest only logs and disables the plugin.
 *
 * @param schema the manifest schema version
 * @param id unique per instance; the plugin's slug
 * @param name human-readable name, shown as attribution on suggestion cards
 * @param version the plugin's own version
 * @param apiVersions extension API major versions this plugin speaks
 * @param capabilities capabilities the plugin provides; unknown ones are
 *     ignored with a warning so a newer plugin degrades, not fails
 * @param homepageUrl documentation or project page; an absolute http(s)
 *     URL, or {@code null} (anything else is dropped at parse time, since
 *     the web app renders it as a link)
 */
public record PluginManifest(
        String schema,
        String id,
        String name,
        String version,
        java.util.List<String> apiVersions,
        java.util.List<String> capabilities,
        @org.jspecify.annotations.Nullable String homepageUrl) {

    public static final String SCHEMA = "mimos.plugin.manifest/v1";
    public static final String CAPABILITY_PLAN_SUGGESTIONS = "plan-suggestions";
    public static final String CAPABILITY_WEEK_PANEL = "week-panel";

    static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_HOMEPAGE_URL_LENGTH = 2048;

    /** Known capabilities this version of Mimos can call. */
    static final java.util.Set<String> KNOWN_CAPABILITIES =
            java.util.Set.of(CAPABILITY_PLAN_SUGGESTIONS, CAPABILITY_WEEK_PANEL);

    static final String CURRENT_API_MAJOR_VERSION = "1";

    public PluginManifest {
        apiVersions = java.util.List.copyOf(apiVersions);
        capabilities = java.util.List.copyOf(capabilities);
    }

    /** Parses and validates a manifest; {@link PluginProtocolException} on anything malformed. */
    public static PluginManifest fromJson(tools.jackson.databind.JsonNode node) {
        String schema = Json.text(node, "schema");
        if (!SCHEMA.equals(schema)) {
            throw new PluginProtocolException("unsupported manifest schema: " + schema);
        }
        String id = Json.text(node, "id");
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new PluginProtocolException("invalid manifest id: " + id);
        }
        String name = Json.text(node, "name");
        if (name == null || name.length() > MAX_NAME_LENGTH) {
            throw new PluginProtocolException("manifest name is required (at most " + MAX_NAME_LENGTH + " characters)");
        }
        String version = Json.text(node, "version");
        if (version == null) {
            throw new PluginProtocolException("manifest version is required");
        }
        java.util.List<String> apiVersions = Json.stringArray(node, "apiVersions", true);
        java.util.List<String> capabilities = Json.stringArray(node, "capabilities", false);
        // schema equals SCHEMA (checked above), so pass the constant.
        return new PluginManifest(
                SCHEMA, id, name, version, apiVersions, capabilities, httpUrlOrNull(Json.text(node, "homepageUrl")));
    }

    /**
     * The URL when it is an absolute http(s) URL with a host, else {@code
     * null}. A bad homepage is dropped rather than failing the manifest: it
     * is cosmetic, but it becomes a link in users' browsers, so a {@code
     * javascript:} or other scheme must never get through.
     */
    static @org.jspecify.annotations.Nullable String httpUrlOrNull(@org.jspecify.annotations.Nullable String url) {
        if (url == null || url.length() > MAX_HOMEPAGE_URL_LENGTH) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return http && uri.getHost() != null ? url : null;
        } catch (java.net.URISyntaxException exception) {
            return null;
        }
    }
}
