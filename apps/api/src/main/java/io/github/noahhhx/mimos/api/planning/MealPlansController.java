package io.github.noahhhx.mimos.api.planning;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Meal planning endpoints over the core-planning domain (ADR-0003). */
@RestController
public class MealPlansController implements PlanningApi {

    private final MealPlanService mealPlanService;
    private final CurrentUserService currentUser;

    public MealPlansController(MealPlanService mealPlanService, CurrentUserService currentUser) {
        this.mealPlanService = mealPlanService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<org.openapitools.model.MealPlan> getMealPlan(LocalDate startDate) {
        UUID profileId = currentUser.requireProfile().id();
        MealPlan plan = mealPlanService.planFor(profileId, startDate);
        return ResponseEntity.ok(toApiPlan(plan));
    }

    @Override
    public ResponseEntity<MealPlanEntry> addMealPlanEntry(LocalDate startDate, MealPlanEntryInput mealPlanEntryInput) {
        UUID profileId = currentUser.requireProfile().id();
        PlannedMeal entry = mealPlanService.addEntry(
                profileId,
                startDate,
                mealPlanEntryInput.getDate(),
                MealType.valueOf(mealPlanEntryInput.getMealType().name()),
                mealPlanEntryInput.getRecipeId(),
                mealPlanEntryInput.getServings().doubleValue());
        return ResponseEntity.status(201).body(toApiEntry(entry));
    }

    @Override
    public ResponseEntity<MealPlanEntry> updateMealPlanEntry(
            LocalDate startDate, UUID entryId, MealPlanEntryPatch mealPlanEntryPatch) {
        UUID profileId = currentUser.requireProfile().id();
        PlannedMeal entry = mealPlanService.updateServings(
                profileId, startDate, entryId, mealPlanEntryPatch.getServings().doubleValue());
        return ResponseEntity.ok(toApiEntry(entry));
    }

    @Override
    public ResponseEntity<Void> deleteMealPlanEntry(LocalDate startDate, UUID entryId) {
        UUID profileId = currentUser.requireProfile().id();
        mealPlanService.removeEntry(profileId, startDate, entryId);
        return ResponseEntity.noContent().build();
    }

    static org.openapitools.model.MealPlan toApiPlan(MealPlan plan) {
        List<MealPlanEntry> entries =
                plan.entries().stream().map(MealPlansController::toApiEntry).toList();
        return new org.openapitools.model.MealPlan().startDate(plan.startDate()).entries(entries);
    }

    static MealPlanEntry toApiEntry(PlannedMeal entry) {
        return new MealPlanEntry()
                .id(entry.id())
                .date(entry.date())
                .mealType(
                        org.openapitools.model.MealType.valueOf(entry.mealType().name()))
                .recipeId(entry.recipeId())
                .recipeTitle(entry.recipeTitle())
                .servings(BigDecimal.valueOf(entry.servings()));
    }
}
