package io.github.noahhhx.mimos.openfoodfacts;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Instance configuration ({@code mimos.open-food-facts.*}, ADR-0025). On by
 * default, since Open Food Facts needs no key; switched off, no barcode is
 * ever sent to it. The base URL is owner configuration, never user input;
 * tests and the compose fake point it elsewhere.
 */
@ConfigurationProperties("mimos.open-food-facts")
public record OpenFoodFactsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://world.openfoodfacts.org") URI baseUrl) {

    public OpenFoodFactsProperties {
        String scheme = baseUrl.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("mimos.open-food-facts.base-url must be http(s): " + baseUrl);
        }
    }
}
