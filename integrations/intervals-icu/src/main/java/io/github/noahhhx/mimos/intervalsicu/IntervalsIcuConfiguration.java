package io.github.noahhhx.mimos.intervalsicu;

import java.security.SecureRandom;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(IntervalsIcuProperties.class)
public class IntervalsIcuConfiguration {

    @Bean
    IntervalsIcuClient intervalsIcuClient(IntervalsIcuProperties properties) {
        return new IntervalsIcuClient(properties.baseUrl());
    }

    /** Present only while the feature is {@linkplain IntervalsIcuProperties#active() active}. */
    @Bean
    @Conditional(Active.class)
    SecretBox secretBox(IntervalsIcuProperties properties, SecureRandom random) {
        return new SecretBox(Objects.requireNonNull(properties.secretKey()), random);
    }

    /** The scheduled sync's trigger, only while the feature is active. */
    @Configuration
    @Conditional(Active.class)
    @EnableScheduling
    static class Scheduling {}

    static final class Active implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return Binder.get(context.getEnvironment())
                    .bindOrCreate("mimos.intervals-icu", IntervalsIcuProperties.class)
                    .active();
        }
    }
}
