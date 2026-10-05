package io.github.noahhhx.mimos.api.security;

import io.github.noahhhx.mimos.api.support.RequestLoggingFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless OIDC resource server. Health and the public read-only library
 * stay unauthenticated (compose probes, SEO pages), as does the protected
 * resource metadata MCP clients sign in with; everything else, including
 * {@code /mcp} and the actuator's other endpoints, requires a bearer token.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final ProblemDetailSecurityHandlers problemDetailHandlers;
    private final KeycloakRolesConverter keycloakRolesConverter;
    private final SecurityProperties properties;
    private final String issuerUri;

    public SecurityConfig(
            ProblemDetailSecurityHandlers problemDetailHandlers,
            KeycloakRolesConverter keycloakRolesConverter,
            SecurityProperties properties,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        this.problemDetailHandlers = problemDetailHandlers;
        this.keycloakRolesConverter = keycloakRolesConverter;
        this.properties = properties;
        this.issuerUri = issuerUri;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health")
                        .permitAll()
                        // Public, read-only library access backing the SEO
                        // recipe pages (ADR-0005). Personal recipes are never
                        // exposed under /api/v1/public.
                        .requestMatchers("/api/v1/public/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(problemDetailHandlers)
                        .accessDeniedHandler(problemDetailHandlers))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                jwt -> jwt.jwtAuthenticationConverter(keycloakRolesConverter))
                        // Protected Resource Metadata (RFC 9728) at
                        // /.well-known/oauth-protected-resource[/<path>], unauthenticated:
                        // how MCP clients find Keycloak (ADR-0014).
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                                resource -> resource.authorizationServer(issuerUri)
                                        .scope("openid")
                                        .resourceName("Mimos")
                                        .tlsClientCertificateBoundAccessTokens(false))));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.corsAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", RequestLoggingFilter.HEADER));
        // Without this, browsers hide the echoed request ID from the web app.
        configuration.setExposedHeaders(List.of(RequestLoggingFilter.HEADER));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
