package io.github.noahhhx.mimos.api.household;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.api.identity.UserProfileRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.openapitools.api.HouseholdApi;
import org.openapitools.model.Household;
import org.openapitools.model.HouseholdInvite;
import org.openapitools.model.HouseholdInvitePreview;
import org.openapitools.model.HouseholdInviteToken;
import org.openapitools.model.Person;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The caller's household, invites, joining, and leaving (ADR-0019) over the contract-generated {@link HouseholdApi}. */
@RestController
public class HouseholdController implements HouseholdApi {

    private final CurrentUserService currentUser;
    private final HouseholdService households;

    public HouseholdController(CurrentUserService currentUser, HouseholdService households) {
        this.currentUser = currentUser;
        this.households = households;
    }

    @Override
    public ResponseEntity<Household> getHousehold() {
        UserProfileRecord caller = currentUser.requireProfile();
        return ResponseEntity.ok(household(caller.householdId(), caller.id()));
    }

    @Override
    public ResponseEntity<HouseholdInvite> createHouseholdInvite() {
        HouseholdService.CreatedInvite invite = households.createInvite(currentUser.requireProfile());
        return ResponseEntity.status(201)
                .body(new HouseholdInvite().token(invite.token()).expiresAt(utc(invite.expiresAt())));
    }

    @Override
    public ResponseEntity<HouseholdInvitePreview> previewHouseholdInvite(HouseholdInviteToken token) {
        UserProfileRecord caller = currentUser.requireProfile();
        HouseholdService.InvitePreview preview = households.preview(caller, token.getToken());
        return ResponseEntity.ok(new HouseholdInvitePreview()
                .members(people(preview.members(), caller.id()))
                .expiresAt(utc(preview.expiresAt()))
                .alreadyMember(preview.alreadyMember())
                .currentHouseholdShared(preview.currentHouseholdShared()));
    }

    @Override
    public ResponseEntity<Household> joinHousehold(HouseholdInviteToken token) {
        UserProfileRecord caller = currentUser.requireProfile();
        return ResponseEntity.ok(household(households.join(caller, token.getToken()), caller.id()));
    }

    @Override
    public ResponseEntity<Household> leaveHousehold() {
        UserProfileRecord caller = currentUser.requireProfile();
        return ResponseEntity.ok(household(households.leave(caller), caller.id()));
    }

    private Household household(UUID householdId, UUID callerId) {
        return new Household().members(people(households.members(householdId), callerId));
    }

    private static List<Person> people(List<HouseholdRepository.Member> members, UUID callerId) {
        return members.stream()
                .map(member -> new Person()
                        .id(member.profileId())
                        .displayName(member.displayName())
                        .you(member.profileId().equals(callerId)))
                .toList();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
