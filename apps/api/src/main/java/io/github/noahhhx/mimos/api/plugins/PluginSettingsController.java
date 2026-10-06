package io.github.noahhhx.mimos.api.plugins;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.plugins.PluginOptInService;
import io.github.noahhhx.mimos.plugins.UserPlugin;
import java.util.List;
import java.util.UUID;
import org.openapitools.api.PluginSettingsApi;
import org.openapitools.model.UserPluginSetting;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's plugin choices (ADR-0013): which of the instance's plugins
 * they turned on. Plugins start off; only the ones turned on see the
 * caller's plan context.
 */
@RestController
public class PluginSettingsController implements PluginSettingsApi {

    private final PluginOptInService optIns;
    private final CurrentUserService currentUser;

    public PluginSettingsController(PluginOptInService optIns, CurrentUserService currentUser) {
        this.optIns = optIns;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<List<org.openapitools.model.UserPlugin>> listMyPlugins() {
        UUID householdId = currentUser.requireProfile().householdId();
        return ResponseEntity.ok(optIns.plugins(householdId).stream()
                .map(PluginSettingsController::toApiPlugin)
                .toList());
    }

    @Override
    public ResponseEntity<Void> updateMyPlugin(String pluginId, UserPluginSetting userPluginSetting) {
        // The generated model leaves a required boolean nullable; an absent
        // one is a bad request, not an unboxing failure.
        Boolean enabled = userPluginSetting.getEnabled();
        if (enabled == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        UUID householdId = currentUser.requireProfile().householdId();
        optIns.setEnabled(householdId, pluginId, enabled);
        return ResponseEntity.noContent().build();
    }

    private static org.openapitools.model.UserPlugin toApiPlugin(UserPlugin plugin) {
        return new org.openapitools.model.UserPlugin()
                .id(plugin.id())
                .name(plugin.name())
                .homepageUrl(plugin.homepageUrl())
                .enabled(plugin.enabled());
    }
}
