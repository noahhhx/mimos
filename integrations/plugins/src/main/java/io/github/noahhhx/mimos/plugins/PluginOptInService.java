package io.github.noahhhx.mimos.plugins;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Per-user plugin opt-in (ADR-0013). Plugins start off for everyone: an
 * instance owner registering a plugin makes it available, and each user
 * decides whether it sees their plan context and suggests into it. A row
 * in {@code plugin_opt_in} means on. Plugin ids come from instance
 * configuration, so a row may outlive its plugin; it is ignored until a
 * plugin with that id is registered again.
 */
@Service
public class PluginOptInService {

    private final JdbcTemplate jdbc;
    private final PluginRegistry registry;

    public PluginOptInService(JdbcTemplate jdbc, PluginRegistry registry) {
        this.jdbc = jdbc;
        this.registry = registry;
    }

    /** The instance's available plugins, in registration order, each with this user's choice. */
    public List<UserPlugin> plugins(UUID profileId) {
        Set<String> enabled = enabledPluginIds(profileId);
        return registry.availablePlugins().stream()
                .map(manifest -> new UserPlugin(
                        manifest.id(), manifest.name(), manifest.homepageUrl(), enabled.contains(manifest.id())))
                .toList();
    }

    /** Ids of the plugins this user turned on, whether or not they are available right now. */
    public Set<String> enabledPluginIds(UUID profileId) {
        return new HashSet<>(
                jdbc.queryForList("select plugin_id from plugin_opt_in where profile_id = ?", String.class, profileId));
    }

    /**
     * Turns a plugin on or off for this user. Turning one on needs it to be
     * available ({@link NoSuchElementException} otherwise); turning one off
     * always succeeds, so a choice can be withdrawn while the plugin is
     * unreachable or after it was removed.
     */
    public void setEnabled(UUID profileId, String pluginId, boolean enabled) {
        if (!enabled) {
            jdbc.update("delete from plugin_opt_in where profile_id = ? and plugin_id = ?", profileId, pluginId);
            return;
        }
        boolean available = registry.availablePlugins().stream()
                .anyMatch(manifest -> manifest.id().equals(pluginId));
        if (!available) {
            throw new NoSuchElementException("no plugin '" + pluginId + "' is available on this instance");
        }
        jdbc.update("""
                insert into plugin_opt_in (profile_id, plugin_id)
                values (?, ?)
                on conflict (profile_id, plugin_id) do nothing
                """, profileId, pluginId);
    }
}
