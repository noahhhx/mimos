package io.github.noahhhx.mimos.plugins;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Instance plugin configuration ({@code mimos.plugins[n].*}). Registered
 * plugins are enabled plugins; static misconfiguration (a bad URL, a
 * configured id that does not match the manifest, duplicate ids) fails
 * startup, while an unreachable plugin only logs and contributes nothing.
 */
@ConfigurationProperties("mimos")
public record PluginsProperties(List<PluginRegistration> plugins) {

    public PluginsProperties {
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
    }
}
