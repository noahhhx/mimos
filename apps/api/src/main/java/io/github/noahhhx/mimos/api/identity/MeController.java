package io.github.noahhhx.mimos.api.identity;

import java.time.Instant;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated user's own profile — the first protected endpoint. */
@RestController
public class MeController {

    private final IdentityService identityService;

    public MeController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/api/v1/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        String subject = Objects.requireNonNull(jwt.getSubject(), "JWT must carry a subject");
        String preferred = jwt.getClaimAsString("preferred_username");
        UserProfile profile = identityService.ensureProfile(subject, preferred != null ? preferred : subject);
        return ResponseEntity.ok(new MeResponse(profile.subjectId(), profile.displayName(), profile.createdAt()));
    }

    record MeResponse(String subjectId, String displayName, Instant createdAt) {}
}
