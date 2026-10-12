package io.github.noahhhx.mimos.intervalsicu;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IntervalsIcuProperties.class)
public class IntervalsIcuConfiguration {

    @Bean
    IntervalsIcuClient intervalsIcuClient(IntervalsIcuProperties properties) {
        return new IntervalsIcuClient(properties.baseUrl());
    }
}
