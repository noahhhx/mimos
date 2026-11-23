package io.github.noahhhx.mimos.api.support;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.IdGenerator;
import org.springframework.util.JdkIdGenerator;

/**
 * Shared infrastructure beans. Domain logic never touches a wall clock, a
 * source of randomness, or {@code UUID.randomUUID()} directly; it injects
 * these, so tests can pin them. {@code ArchitectureTest} enforces it.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    IdGenerator idGenerator() {
        return new JdkIdGenerator();
    }
}
