package io.github.noahhhx.mimos.api.security;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Maps Keycloak's role claims to Spring authorities:
 *
 * <ul>
 *   <li>realm roles ({@code realm_access.roles}) become {@code ROLE_<role>}
 *   <li>client roles ({@code resource_access.<client>.roles}) become
 *       {@code ROLE_<client>_<role>}
 * </ul>
 */
@Component
public final class KeycloakRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String subject = requireNonNull(jwt.getSubject(), "JWT must carry a subject");
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt), subject);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        realmRoles(jwt.getClaimAsMap("realm_access")).forEach(role -> authorities.add(role(null, role)));
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.forEach((client, value) -> {
                if (value instanceof Map<?, ?> access) {
                    realmRoles(asStringMap(access)).forEach(role -> authorities.add(role(client, role)));
                }
            });
        }
        return authorities;
    }

    private List<String> realmRoles(@Nullable Map<String, Object> access) {
        if (access == null) {
            return List.of();
        }
        Object roles = access.get("roles");
        if (!(roles instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asStringMap(Map<?, ?> access) {
        return (Map<String, Object>) access;
    }

    private SimpleGrantedAuthority role(@Nullable String client, String role) {
        String prefix = client == null ? "" : client + "_";
        return new SimpleGrantedAuthority("ROLE_" + prefix + role);
    }
}
