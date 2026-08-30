package io.github.noahhhx.mimos.api.planning;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.planning.logging.MealLogDraft;
import io.github.noahhhx.mimos.planning.logging.MealLogService;
import io.github.noahhhx.mimos.planning.plan.MealType;
import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.LoggingApi;
import org.openapitools.model.MealLogInput;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Calorie and macro logging endpoints over the core-planning domain (ADR-0003). */
@RestController
public class LoggingController implements LoggingApi {

    private final MealLogService mealLogService;
    private final CurrentUserService currentUser;

    public LoggingController(MealLogService mealLogService, CurrentUserService currentUser) {
        this.mealLogService = mealLogService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<org.openapitools.model.MealLog> createMealLog(MealLogInput mealLogInput) {
        UUID profileId = currentUser.requireProfile().id();
        io.github.noahhhx.mimos.planning.logging.MealLog log = mealLogService.create(profileId, toDraft(mealLogInput));
        return ResponseEntity.status(201).body(toApiLog(log));
    }

    @Override
    public ResponseEntity<List<org.openapitools.model.MealLog>> listMealLogs(LocalDate from, LocalDate to) {
        UUID profileId = currentUser.requireProfile().id();
        List<org.openapitools.model.MealLog> logs = mealLogService.findRange(profileId, from, to).stream()
                .map(LoggingController::toApiLog)
                .toList();
        return ResponseEntity.ok(logs);
    }

    @Override
    public ResponseEntity<Void> deleteMealLog(UUID logId) {
        UUID profileId = currentUser.requireProfile().id();
        mealLogService.delete(profileId, logId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<org.openapitools.model.DailyLogSummary>> summarizeMealLogs(
            LocalDate from, LocalDate to) {
        UUID profileId = currentUser.requireProfile().id();
        List<org.openapitools.model.DailyLogSummary> summary = mealLogService.summarize(profileId, from, to).stream()
                .map(day -> new org.openapitools.model.DailyLogSummary()
                        .date(day.date())
                        .calories(BigDecimal.valueOf(day.calories()))
                        .proteinG(BigDecimal.valueOf(day.proteinG()))
                        .carbsG(BigDecimal.valueOf(day.carbsG()))
                        .fatG(BigDecimal.valueOf(day.fatG())))
                .toList();
        return ResponseEntity.ok(summary);
    }

    private static MealLogDraft toDraft(MealLogInput input) {
        return new MealLogDraft(
                input.getDate(),
                MealType.valueOf(input.getMealType().name()),
                input.getRecipeId(),
                input.getServings() != null ? input.getServings().doubleValue() : 1,
                input.getDescription(),
                fromApiNutrition(input.getNutrition()));
    }

    private static org.openapitools.model.MealLog toApiLog(io.github.noahhhx.mimos.planning.logging.MealLog log) {
        return new org.openapitools.model.MealLog()
                .id(log.id())
                .date(log.date())
                .mealType(org.openapitools.model.MealType.valueOf(log.mealType().name()))
                .recipeId(log.recipeId())
                .description(log.description())
                .servings(BigDecimal.valueOf(log.servings()))
                .nutrition(toApiNutrition(log.nutrition()));
    }

    private static org.openapitools.model.Nutrition toApiNutrition(Nutrition nutrition) {
        return new org.openapitools.model.Nutrition()
                .calories(toBigDecimal(nutrition.calories()))
                .proteinG(toBigDecimal(nutrition.proteinG()))
                .carbsG(toBigDecimal(nutrition.carbsG()))
                .fatG(toBigDecimal(nutrition.fatG()));
    }

    private static Nutrition fromApiNutrition(org.openapitools.model.@Nullable Nutrition nutrition) {
        if (nutrition == null) {
            return Nutrition.UNKNOWN;
        }
        return new Nutrition(
                fromBigDecimal(nutrition.getCalories()),
                fromBigDecimal(nutrition.getProteinG()),
                fromBigDecimal(nutrition.getCarbsG()),
                fromBigDecimal(nutrition.getFatG()));
    }

    private static @Nullable BigDecimal toBigDecimal(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static @Nullable Double fromBigDecimal(@Nullable BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
