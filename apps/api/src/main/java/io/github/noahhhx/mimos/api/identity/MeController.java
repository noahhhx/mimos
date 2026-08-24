package io.github.noahhhx.mimos.api.identity;

import static java.util.Objects.requireNonNull;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.openapitools.api.MeApi;
import org.openapitools.model.UserProfile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated user's own profile — the first protected endpoint.
 * Implements the contract-generated {@link MeApi} interface, so any drift
 * between spec and code fails the build (ADR-0004).
 */
@RestController
public class MeController implements MeApi {

    private final IdentityService identityService;

    public MeController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Override
    public ResponseEntity<UserProfile> getMe() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        requireNonNull(authentication, "the security chain guarantees an authenticated principal");
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        String subject = requireNonNull(jwt.getSubject(), "JWT must carry a subject");
        String preferred = jwt.getClaimAsString("preferred_username");
        UserProfileRecord profile = identityService.ensureProfile(subject, preferred != null ? preferred : subject);
        return ResponseEntity.ok(new UserProfile()
                .id(profile.id())
                .subjectId(profile.subjectId())
                .displayName(profile.displayName())
                .createdAt(OffsetDateTime.ofInstant(profile.createdAt(), ZoneOffset.UTC)));
    }
}
