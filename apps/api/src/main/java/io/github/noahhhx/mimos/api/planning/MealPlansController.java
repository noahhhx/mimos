package io.github.noahhhx.mimos.api.planning;

import io.github.noahhhx.mimos.api.household.HouseholdMember;
import io.github.noahhhx.mimos.api.household.HouseholdService;
import io.github.noahhhx.mimos.api.household.HouseholdService.Membership;
import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.api.identity.UserProfileRecord;
import io.github.noahhhx.mimos.planning.plan.MealPlan;
import io.github.noahhhx.mimos.planning.plan.MealPlanService;
import io.github.noahhhx.mimos.planning.plan.MealType;
import io.github.noahhhx.mimos.planning.plan.PlannedMeal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.openapitools.api.PlanningApi;
import org.openapitools.model.MealPlanEntry;
import org.openapitools.model.MealPlanEntryInput;
import org.openapitools.model.MealPlanEntryPatch;
import org.openapitools.model.Person;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meal planning endpoints over the core-planning domain (ADR-0003), with
 * diners from the household (ADR-0019). A write that names diners holds
 * the household's membership for its whole transaction, so the diners are
 * still members when it commits.
 */
@RestController
public class MealPlansController implements PlanningApi {

    private final MealPlanService mealPlanService;
    private final CurrentUserService currentUser;
    private final HouseholdService households;

    public MealPlansController(
            MealPlanService mealPlanService, CurrentUserService currentUser, HouseholdService households) {
        this.mealPlanService = mealPlanService;
        this.currentUser = currentUser;
        this.households = households;
    }

    @Override
    public ResponseEntity<org.openapitools.model.MealPlan> getMealPlan(LocalDate startDate) {
        UserProfileRecord caller = currentUser.requireProfile();
        MealPlan plan = mealPlanService.planFor(caller.householdId(), startDate);
        List<HouseholdMember> members = households.members(caller.householdId());
        List<MealPlanEntry> entries = plan.entries().stream()
                .map(entry -> toApiEntry(entry, members, caller.id()))
                .toList();
        return ResponseEntity.ok(new org.openapitools.model.MealPlan()
                .startDate(plan.startDate())
                .entries(entries));
    }

    @Override
    @Transactional
    public ResponseEntity<MealPlanEntry> addMealPlanEntry(LocalDate startDate, MealPlanEntryInput mealPlanEntryInput) {
        UserProfileRecord caller = currentUser.requireProfile();
        Membership household = households.holdMembership(caller);
        MealType mealType = MealType.valueOf(mealPlanEntryInput.getMealType().name());
        PlannedMeal entry = mealPlanService.addEntry(
                household.householdId(),
                Diners.forNewEntry(mealType, mealPlanEntryInput.getDiners(), household.memberIds(), caller.id()),
                startDate,
                mealPlanEntryInput.getDate(),
                mealType,
                mealPlanEntryInput.getRecipeId(),
                mealPlanEntryInput.getServings().doubleValue());
        return ResponseEntity.status(201).body(toApiEntry(entry, household.members(), caller.id()));
    }

    @Override
    @Transactional
    public ResponseEntity<MealPlanEntry> updateMealPlanEntry(
            LocalDate startDate, UUID entryId, MealPlanEntryPatch mealPlanEntryPatch) {
        UserProfileRecord caller = currentUser.requireProfile();
        BigDecimal servings = mealPlanEntryPatch.getServings();
        List<UUID> diners = mealPlanEntryPatch.getDiners();
        if (servings == null && diners == null) {
            throw new IllegalArgumentException("Send servings, diners, or both.");
        }
        Membership household = households.holdMembership(caller);
        PlannedMeal entry = mealPlanService.updateEntry(
                household.householdId(),
                startDate,
                entryId,
                servings == null ? null : servings.doubleValue(),
                diners == null ? null : Diners.requireMembers(diners, household.memberIds()));
        return ResponseEntity.ok(toApiEntry(entry, household.members(), caller.id()));
    }

    @Override
    public ResponseEntity<Void> deleteMealPlanEntry(LocalDate startDate, UUID entryId) {
        UUID householdId = currentUser.requireProfile().householdId();
        mealPlanService.removeEntry(householdId, startDate, entryId);
        return ResponseEntity.noContent().build();
    }

    /** The entry with its diners named, in the household's member order. */
    private static MealPlanEntry toApiEntry(PlannedMeal entry, List<HouseholdMember> members, UUID callerId) {
        List<Person> diners = members.stream()
                .filter(member -> entry.dinerProfileIds().contains(member.profileId()))
                .map(member -> new Person()
                        .id(member.profileId())
                        .displayName(member.displayName())
                        .you(member.profileId().equals(callerId)))
                .toList();
        return new MealPlanEntry()
                .id(entry.id())
                .date(entry.date())
                .mealType(
                        org.openapitools.model.MealType.valueOf(entry.mealType().name()))
                .recipeId(entry.recipeId())
                .recipeTitle(entry.recipeTitle())
                .servings(BigDecimal.valueOf(entry.servings()))
                .diners(diners);
    }
}
