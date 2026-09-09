package io.github.noahhhx.mimos.plugins;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * One plugin registration from instance configuration
 * ({@code mimos.plugins[n].*}, ADR-0006). Enabled means registered; changes
 * take effect on restart.
 *
 * @param id optional; must match the manifest's id when set, so a URL
 *     serving the wrong plugin is caught as misconfiguration
 * @param url the plugin's base URL (http or https; owner configuration,
 *     never user input — that is the SSRF boundary)
 * @param sharedSecret optional static bearer token for plugins hosted off
 *     the local network
 * @param timeout per-plugin call timeout (connect and read)
 */
public record PluginRegistration(
        @Nullable String id,
        String url,
        @Nullable String sharedSecret,
        @DefaultValue("2s") Duration timeout) {

    static final Duration MIN_TIMEOUT = Duration.ofMillis(100);

    public PluginRegistration {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("plugin url must be http(s): " + url);
        }
        if (timeout == null || timeout.isNegative() || timeout.compareTo(MIN_TIMEOUT) < 0) {
            throw new IllegalArgumentException("plugin timeout must be at least " + MIN_TIMEOUT.toMillis() + "ms");
        }
    }
}
