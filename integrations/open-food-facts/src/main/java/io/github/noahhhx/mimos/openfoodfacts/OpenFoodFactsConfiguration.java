package io.github.noahhhx.mimos.openfoodfacts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenFoodFactsProperties.class)
public class OpenFoodFactsConfiguration {

    /** Absent while the feature is off, so nothing can call Open Food Facts. */
    @Bean
    @ConditionalOnBooleanProperty(name = "mimos.open-food-facts.enabled", matchIfMissing = true)
    OpenFoodFactsClient openFoodFactsClient(OpenFoodFactsProperties properties) {
        return new OpenFoodFactsClient(properties.baseUrl());
    }
}
