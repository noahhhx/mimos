package io.github.noahhhx.mimos.intervalsicu;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Instance configuration ({@code mimos.intervals-icu.*}, ADR-0022). The
 * base URL is owner configuration, never user input; tests and the
 * compose fake point it elsewhere. {@code secret-key} is
 * {@code MIMOS_SECRET_KEY}, the {@link SecretBox} key: without one the
 * feature is off, and a malformed one fails startup. {@code sync-interval}
 * is how long the scheduled sync waits between rounds, at least a minute.
 */
@ConfigurationProperties("mimos.intervals-icu")
public record IntervalsIcuProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://intervals.icu") URI baseUrl,
        @Nullable String secretKey,
        @DefaultValue("30m") Duration syncInterval) {

    static final Duration MIN_SYNC_INTERVAL = Duration.ofMinutes(1);

    public IntervalsIcuProperties {
        String scheme = baseUrl.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("mimos.intervals-icu.base-url must be http(s): " + baseUrl);
        }
        if (syncInterval.compareTo(MIN_SYNC_INTERVAL) < 0) {
            throw new IllegalArgumentException(
                    "mimos.intervals-icu.sync-interval must be at least a minute: " + syncInterval);
        }
        if (secretKey != null && secretKey.isBlank()) {
            secretKey = null;
        }
        if (secretKey != null) {
            SecretBox.parseKey(secretKey);
        }
    }

    /** Whether people can connect intervals.icu: switched on, with a key to encrypt theirs under. */
    public boolean active() {
        return enabled && secretKey != null;
    }

    @Override
    public String toString() {
        return "IntervalsIcuProperties[enabled=" + enabled + ", baseUrl=" + baseUrl + ", secretKey="
                + (secretKey == null ? "<unset>" : "<hidden>") + ", syncInterval=" + syncInterval + "]";
    }
}
