package io.github.noahhhx.mimos.api.identity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.openapitools.api.MeApi;
import org.openapitools.model.UserProfile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated user's own profile. Implements the contract-generated
 * {@link MeApi} interface, so any drift between spec and code fails the
 * build (ADR-0003).
 */
@RestController
public class MeController implements MeApi {

    private final CurrentUserService currentUser;

    public MeController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<UserProfile> getMe() {
        UserProfileRecord profile = currentUser.requireProfile();
        return ResponseEntity.ok(new UserProfile()
                .id(profile.id())
                .subjectId(profile.subjectId())
                .displayName(profile.displayName())
                .createdAt(OffsetDateTime.ofInstant(profile.createdAt(), ZoneOffset.UTC)));
    }
}
