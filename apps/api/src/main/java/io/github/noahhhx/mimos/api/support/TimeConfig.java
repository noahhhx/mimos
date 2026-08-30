package io.github.noahhhx.mimos.api.support;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared infrastructure beans. Domain logic never touches a wall clock
 * directly (AGENTS.md); it injects this Clock, so tests can pin time.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
