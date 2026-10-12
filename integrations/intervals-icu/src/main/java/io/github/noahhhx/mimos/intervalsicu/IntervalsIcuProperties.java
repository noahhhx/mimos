package io.github.noahhhx.mimos.intervalsicu;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Instance configuration ({@code mimos.intervals-icu.*}, ADR-0022). The
 * base URL is owner configuration, never user input; tests and the
 * compose fake point it elsewhere.
 */
@ConfigurationProperties("mimos.intervals-icu")
public record IntervalsIcuProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://intervals.icu") URI baseUrl) {

    public IntervalsIcuProperties {
        String scheme = baseUrl.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("mimos.intervals-icu.base-url must be http(s): " + baseUrl);
        }
    }
}
