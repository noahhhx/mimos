package io.github.noahhhx.mimos.api.identity;

import static java.util.Objects.requireNonNull;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Resolves the authenticated caller's profile from the request's JWT.
 * Profiles are created lazily on first sight (see {@link IdentityService}).
 */
@Service
public class CurrentUserService {

    private final IdentityService identityService;

    public CurrentUserService(IdentityService identityService) {
        this.identityService = identityService;
    }

    /** The caller's profile, created on first authenticated request. */
    public UserProfileRecord requireProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        requireNonNull(authentication, "the security chain guarantees an authenticated principal");
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        String subject = requireNonNull(jwt.getSubject(), "JWT must carry a subject");
        String preferred = jwt.getClaimAsString("preferred_username");
        return identityService.ensureProfile(subject, preferred != null ? preferred : subject);
    }
}
