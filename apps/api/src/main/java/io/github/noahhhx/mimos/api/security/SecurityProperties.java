package io.github.noahhhx.mimos.api.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security-related settings.
 *
 * @param corsAllowedOrigins browser origins allowed to call the API (the web
 *     frontend's origin); comma-separated via the environment variable
 *     {@code MIMOS_SECURITY_CORS_ALLOWED_ORIGINS}
 */
@ConfigurationProperties("mimos.security")
public record SecurityProperties(List<String> corsAllowedOrigins) {

    public SecurityProperties {
        corsAllowedOrigins = corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()
                ? List.of("http://localhost:3000")
                : List.copyOf(corsAllowedOrigins);
    }
}
