package io.github.noahhhx.mimos.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * The instance's registered plugins, built from {@link PluginsProperties}
 * (ADR-0006). Static misconfiguration — a configured id that does not match
 * the manifest, or duplicate ids across the registry — fails startup; a
 * plugin that is unreachable or malformed never does: it logs, contributes
 * nothing, and is retried lazily on the next request.
 */
@Component
public class PluginRegistry implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    private final List<Plugin> plugins;

    public PluginRegistry(PluginsProperties properties) {
        this.plugins = properties.plugins().stream()
                .map(registration -> new Plugin(new PluginClient(registration)))
                .toList();
    }

    /** All registered plugins, in registration (config) order. */
    public List<Plugin> plugins() {
        return plugins;
    }

    /**
     * Plugins whose resolved manifest declares {@code capability}. Manifests
     * are fetched when missing; resolution failures are contained (the
     * plugin contributes nothing this request).
     */
    public List<Plugin> pluginsWithCapability(String capability) {
        return usablePlugins().stream()
                .filter(usable -> usable.manifest().capabilities().contains(capability))
                .map(Usable::plugin)
                .toList();
    }

    /**
     * Manifests of the plugins a user can turn on (ADR-0013): resolved,
     * speaking the current extension API, and declaring at least one
     * capability this version of Mimos calls. In registration order.
     */
    public List<PluginManifest> availablePlugins() {
        return usablePlugins().stream()
                .map(Usable::manifest)
                .filter(manifest ->
                        manifest.capabilities().stream().anyMatch(PluginManifest.KNOWN_CAPABILITIES::contains))
                .toList();
    }

    /**
     * Resolved plugins that speak the current extension API version, with
     * the manifest they were judged by (a concurrent fan-out failure may
     * invalidate the plugin's cached one meanwhile), resolving missing
     * manifests.
     */
    private List<Usable> usablePlugins() {
        Map<String, Plugin> resolved = new HashMap<>();
        List<Usable> result = new ArrayList<>();
        for (Plugin plugin : plugins) {
            PluginManifest manifest = plugin.manifest();
            if (manifest == null) {
                try {
                    manifest = resolve(plugin, resolved);
                } catch (RuntimeException exception) {
                    // Lazily-discovered misconfiguration or an unreachable
                    // plugin: skip with an error, never fail the request.
                    log.error("skipping plugin at {}: {}", plugin.registration().url(), exception.getMessage());
                    continue;
                }
            }
            if (!manifest.apiVersions().contains(PluginManifest.CURRENT_API_MAJOR_VERSION)) {
                log.warn(
                        "plugin {} does not speak extension API version {}; skipping",
                        manifest.id(),
                        PluginManifest.CURRENT_API_MAJOR_VERSION);
                continue;
            }
            result.add(new Usable(plugin, manifest));
        }
        return List.copyOf(result);
    }

    private record Usable(Plugin plugin, PluginManifest manifest) {}

    @Override
    public void run(ApplicationArguments args) {
        warmUp();
    }

    /**
     * Startup warm-up: resolve every manifest. Unreachable or malformed
     * plugins only log (the API boots regardless); misconfiguration —
     * duplicate resolved ids or a configured-id mismatch — throws and fails
     * startup (ADR-0006).
     */
    void warmUp() {
        Map<String, Plugin> resolved = new HashMap<>();
        int unavailable = 0;
        for (Plugin plugin : plugins) {
            try {
                resolve(plugin, resolved);
            } catch (IllegalStateException exception) {
                // Static misconfiguration: caught loudly at boot.
                throw exception;
            } catch (RuntimeException exception) {
                unavailable++;
                log.warn(
                        "plugin at {} is unreachable or malformed; it will be retried on the next request: {}",
                        plugin.registration().url(),
                        exception.getMessage());
            }
        }
        log.info("Plugin registry: {} registered ({} unavailable at startup)", plugins.size(), unavailable);
    }

    private PluginManifest resolve(Plugin plugin, Map<String, Plugin> resolved) {
        PluginManifest manifest = plugin.client().fetchManifest();
        String configuredId = plugin.registration().id();
        if (configuredId != null && !configuredId.equals(manifest.id())) {
            throw new IllegalStateException("plugin at " + plugin.registration().url() + " has manifest id '"
                    + manifest.id() + "' but is configured as '" + configuredId + "'");
        }
        Plugin existing = resolved.putIfAbsent(manifest.id(), plugin);
        if (existing != null) {
            throw new IllegalStateException("duplicate plugin id '" + manifest.id() + "' (also served at "
                    + existing.registration().url() + ")");
        }
        for (String capability : manifest.capabilities()) {
            if (!PluginManifest.KNOWN_CAPABILITIES.contains(capability)) {
                log.warn("plugin {} declares unknown capability '{}'; ignoring", manifest.id(), capability);
            }
        }
        plugin.setManifest(manifest);
        return manifest;
    }
}
