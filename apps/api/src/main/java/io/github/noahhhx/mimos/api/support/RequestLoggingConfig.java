package io.github.noahhhx.mimos.api.support;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link RequestLoggingFilter} ahead of the security filter chain
 * (order -100), so requests the chain rejects get an ID and an access line.
 */
@Configuration
public class RequestLoggingConfig {

    @Bean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter(Clock clock) {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(
                new RequestLoggingFilter(() -> UUID.randomUUID().toString(), clock));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
