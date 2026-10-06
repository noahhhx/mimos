package io.github.noahhhx.mimos.api.household;

import io.github.noahhhx.mimos.api.identity.IdentityService;
import io.github.noahhhx.mimos.api.identity.UserProfileRecord;
import io.github.noahhhx.mimos.planning.logging.MealLogService;
import io.github.noahhhx.mimos.planning.plan.MealPlanService;
import io.github.noahhhx.mimos.planning.shopping.ShoppingListService;
import io.github.noahhhx.mimos.plugins.PluginOptInService;
import io.github.noahhhx.mimos.plugins.PluginSubjectService;
import io.github.noahhhx.mimos.recipes.recipe.IngredientService;
import io.github.noahhhx.mimos.recipes.recipe.RecipeService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who is in a household, and joining and leaving one (ADR-0019). Every
 * member is equal: any member invites, anyone may leave, nobody removes
 * anyone else. What a household owns in other modules moves or goes only
 * through those modules' public services.
 */
@Service
public class HouseholdService {

    static final Duration INVITE_LIFETIME = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;

    private final HouseholdRepository households;
    private final IdentityService identity;
    private final RecipeService recipes;
    private final IngredientService ingredients;
    private final MealPlanService mealPlans;
    private final ShoppingListService shoppingLists;
    private final MealLogService mealLogs;
    private final PluginOptInService pluginOptIns;
    private final PluginSubjectService pluginSubjects;
    private final Clock clock;
    private final SecureRandom random;

    public HouseholdService(
            HouseholdRepository households,
            IdentityService identity,
            RecipeService recipes,
            IngredientService ingredients,
            MealPlanService mealPlans,
            ShoppingListService shoppingLists,
            MealLogService mealLogs,
            PluginOptInService pluginOptIns,
            PluginSubjectService pluginSubjects,
            Clock clock,
            SecureRandom random) {
        this.households = households;
        this.identity = identity;
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.mealPlans = mealPlans;
        this.shoppingLists = shoppingLists;
        this.mealLogs = mealLogs;
        this.pluginOptIns = pluginOptIns;
        this.pluginSubjects = pluginSubjects;
        this.clock = clock;
        this.random = random;
    }

    public List<HouseholdRepository.Member> members(UUID householdId) {
        return households.members(householdId);
    }

    /** Whether anyone else is in the household. */
    public boolean isShared(UUID householdId) {
        return households.memberCount(householdId) > 1;
    }

    /** A new single-use invite to the inviter's household. Only the token's hash is stored. */
    @Transactional
    public CreatedInvite createInvite(UserProfileRecord inviter) {
        byte[] secret = new byte[TOKEN_BYTES];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(INVITE_LIFETIME);
        households.insertInvite(hash(token), inviter.householdId(), inviter.id(), now, expiresAt);
        return new CreatedInvite(token, expiresAt);
    }

    /** What joining with the invite would mean for the caller, without using it. */
    public InvitePreview preview(UserProfileRecord caller, String token) {
        HouseholdRepository.Invite invite = requireUsable(hash(token));
        return new InvitePreview(
                households.members(invite.householdId()),
                invite.expiresAt(),
                invite.householdId().equals(caller.householdId()),
                isShared(caller.householdId()));
    }

    /**
     * Uses the invite and moves the caller into its household. From a
     * household of one, its recipes and ingredients come along and the
     * rest of it is deleted; from a shared household, the caller leaves it
     * first and brings nothing. Returns the household joined.
     */
    @Transactional
    public UUID join(UserProfileRecord caller, String token) {
        String tokenHash = hash(token);
        UUID target = requireUsable(tokenHash).householdId();
        UUID current = households.lockHouseholdOf(caller.id());
        if (current.equals(target)) {
            throw new MembershipConflictException("You are already in this household.");
        }
        Stream.of(current, target).sorted().forEach(identity::lockHousehold);
        if (!households.markUsed(tokenHash, clock.instant())) {
            throw new InviteNoLongerValidException("This invite link was already used. Ask for a new one.");
        }
        if (isShared(current)) {
            stepOut(caller.id(), current);
            households.moveProfile(caller.id(), target);
        } else {
            recipes.moveAll(current, target);
            ingredients.moveAll(current, target);
            mealPlans.deleteAll(current);
            shoppingLists.deleteAll(current);
            pluginOptIns.deleteAll(current);
            pluginSubjects.deleteAll(current);
            households.moveProfile(caller.id(), target);
            households.deleteHousehold(current);
        }
        return target;
    }

    /**
     * Takes the caller out of their shared household into a new household
     * of one. The household keeps everything it owns; the caller keeps
     * their meal log. Returns the new household.
     */
    @Transactional
    public UUID leave(UserProfileRecord caller) {
        UUID current = households.lockHouseholdOf(caller.id());
        identity.lockHousehold(current);
        if (!isShared(current)) {
            throw new MembershipConflictException(
                    "You are the only one in your household, so there is nothing to leave.");
        }
        stepOut(caller.id(), current);
        UUID fresh = households.createHousehold();
        households.moveProfile(caller.id(), fresh);
        return fresh;
    }

    /** Undoes what tied a member to a household they are leaving, all of which the household owns. */
    private void stepOut(UUID profileId, UUID householdId) {
        mealPlans.removeDiner(householdId, profileId);
        mealLogs.unlinkRecipesOf(profileId, householdId);
    }

    private HouseholdRepository.Invite requireUsable(String tokenHash) {
        HouseholdRepository.Invite invite = households
                .findInvite(tokenHash)
                .orElseThrow(() -> new NoSuchElementException("This invite link is not valid. Ask for a new one."));
        if (invite.usedAt() != null) {
            throw new InviteNoLongerValidException("This invite link was already used. Ask for a new one.");
        }
        if (!clock.instant().isBefore(invite.expiresAt())) {
            throw new InviteNoLongerValidException("This invite link has expired. Ask for a new one.");
        }
        return invite;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("every Java runtime provides SHA-256", exception);
        }
    }

    record CreatedInvite(String token, Instant expiresAt) {}

    record InvitePreview(
            List<HouseholdRepository.Member> members,
            Instant expiresAt,
            boolean alreadyMember,
            boolean currentHouseholdShared) {}
}
