package io.github.noahhhx.mimos.plugins;

import org.jspecify.annotations.Nullable;

/**
 * A registered plugin at runtime: its configuration, its client, and its
 * last successfully fetched manifest. The manifest is fetched at startup
 * (best effort) and re-fetched lazily when missing or after a failed
 * fan-out, so a plugin that starts after the API is still picked up
 * (ADR-0006).
 */
final class Plugin {

    private final PluginClient client;
    private volatile @Nullable PluginManifest manifest;

    Plugin(PluginClient client) {
        this.client = client;
    }

    PluginClient client() {
        return client;
    }

    PluginRegistration registration() {
        return client.registration();
    }

    /** The last resolved manifest, or {@code null} when unresolved. */
    @Nullable
    PluginManifest manifest() {
        return manifest;
    }

    void setManifest(PluginManifest manifest) {
        this.manifest = manifest;
    }

    /** Drops the cached manifest so the next fan-out re-fetches it. */
    void invalidate() {
        manifest = null;
    }
}
