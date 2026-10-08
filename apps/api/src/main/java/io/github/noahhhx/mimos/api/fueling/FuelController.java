package io.github.noahhhx.mimos.api.fueling;

import static java.util.Objects.requireNonNull;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.api.identity.UserProfileRecord;
import io.github.noahhhx.mimos.api.recipes.RecipeApiMapper;
import io.github.noahhhx.mimos.fueling.FuelProfile;
import io.github.noahhhx.mimos.fueling.FuelService;
import io.github.noahhhx.mimos.fueling.Goal;
import io.github.noahhhx.mimos.fueling.Intensity;
import io.github.noahhhx.mimos.fueling.Kind;
import io.github.noahhhx.mimos.fueling.Sex;
import io.github.noahhhx.mimos.fueling.Sport;
import io.github.noahhhx.mimos.fueling.TrainingSession;
import io.github.noahhhx.mimos.fueling.TrainingSessionDraft;
import io.github.noahhhx.mimos.fueling.targets.DailyTarget;
import io.github.noahhhx.mimos.fueling.targets.SessionEnergy;
import io.github.noahhhx.mimos.fueling.targets.TargetBasis;
import io.github.noahhhx.mimos.fueling.targets.TargetCalculator;
import io.github.noahhhx.mimos.planning.logging.DailyLogSummary;
import io.github.noahhhx.mimos.planning.logging.MealLogService;
import io.github.noahhhx.mimos.planning.plan.MealPlanService;
import io.github.noahhhx.mimos.planning.plan.PlannedDayTotal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.FuelApi;
import org.openapitools.model.BodyFatSource;
import org.openapitools.model.EnergyMethod;
import org.openapitools.model.FuelDay;
import org.openapitools.model.FuelDays;
import org.openapitools.model.FuelGoal;
import org.openapitools.model.FuelProfileInput;
import org.openapitools.model.FuelSex;
import org.openapitools.model.FuelTarget;
import org.openapitools.model.FuelTargetBasis;
import org.openapitools.model.LoadBand;
import org.openapitools.model.Nutrition;
import org.openapitools.model.TrainingIntensity;
import org.openapitools.model.TrainingKind;
import org.openapitools.model.TrainingSessionInput;
import org.openapitools.model.TrainingSource;
import org.openapitools.model.TrainingSport;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FuelController implements FuelApi {

    private final FuelService fuel;
    private final MealPlanService mealPlans;
    private final MealLogService mealLogs;
    private final CurrentUserService currentUser;

    public FuelController(
            FuelService fuel, MealPlanService mealPlans, MealLogService mealLogs, CurrentUserService currentUser) {
        this.fuel = fuel;
        this.mealPlans = mealPlans;
        this.mealLogs = mealLogs;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<FuelDays> getMyFuel(LocalDate from, LocalDate to) {
        UserProfileRecord caller = currentUser.requireProfile();
        Optional<FuelProfile> profile = fuel.profile(caller.id()).filter(FuelProfile::enabled);
        Map<LocalDate, List<TrainingSession>> sessions =
                fuel.sessions(caller.id(), from, to).stream().collect(Collectors.groupingBy(TrainingSession::date));
        Map<LocalDate, DailyLogSummary> logged = mealLogs.summarize(caller.id(), from, to).stream()
                .collect(Collectors.toMap(DailyLogSummary::date, Function.identity()));
        Map<LocalDate, PlannedDayTotal> planned = new HashMap<>();
        for (LocalDate monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                !monday.isAfter(to);
                monday = monday.plusWeeks(1)) {
            mealPlans
                    .plannedTotals(caller.householdId(), monday, caller.id())
                    .forEach(day -> planned.put(day.date(), day));
        }

        List<FuelDay> days = from.datesUntil(to.plusDays(1))
                .map(date -> {
                    List<TrainingSession> day = sessions.getOrDefault(date, List.of());
                    @Nullable DailyLogSummary log = logged.get(date);
                    return new FuelDay()
                            .date(date)
                            .target(profile.map(
                                            enabled -> toApiTarget(TargetCalculator.target(date, enabled, day, null)))
                                    .orElse(null))
                            .planned(toApiPlanned(
                                    requireNonNull(planned.get(date), "plannedTotals covers every day of its week")))
                            .logged(
                                    log == null
                                            ? total(0, 0, 0, 0)
                                            : total(log.calories(), log.proteinG(), log.carbsG(), log.fatG()))
                            .sessions(day.stream()
                                    .map(FuelController::toApiSession)
                                    .toList());
                })
                .toList();
        return ResponseEntity.ok(new FuelDays().from(from).to(to).days(days));
    }

    @Override
    public ResponseEntity<org.openapitools.model.FuelProfile> getMyFuelProfile() {
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(fuel.profile(profileId)
                .map(FuelController::toApiProfile)
                .orElseGet(() -> new org.openapitools.model.FuelProfile()
                        .enabled(false)
                        .sex(FuelSex.UNSPECIFIED)
                        .goal(FuelGoal.FUEL)
                        .proteinGPerKg(BigDecimal.valueOf(Goal.FUEL.defaultProteinGPerKg()))));
    }

    @Override
    public ResponseEntity<org.openapitools.model.FuelProfile> updateMyFuelProfile(FuelProfileInput input) {
        UUID profileId = currentUser.requireProfile().id();
        Goal goal = Goal.valueOf(present(input.getGoal(), "goal").name());
        BigDecimal protein = input.getProteinGPerKg();
        FuelProfile saved = fuel.saveProfile(new FuelProfile(
                profileId,
                present(input.getEnabled(), "enabled"),
                present(input.getWeightKg(), "weightKg").doubleValue(),
                Sex.valueOf(present(input.getSex(), "sex").name()),
                fromBigDecimal(input.getBodyFatPercent()),
                input.getFtpWatts(),
                goal,
                protein == null ? goal.defaultProteinGPerKg() : protein.doubleValue()));
        return ResponseEntity.ok(toApiProfile(saved));
    }

    @Override
    public ResponseEntity<List<org.openapitools.model.TrainingSession>> listMyTraining(LocalDate from, LocalDate to) {
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(fuel.sessions(profileId, from, to).stream()
                .map(FuelController::toApiSession)
                .toList());
    }

    @Override
    public ResponseEntity<org.openapitools.model.TrainingSession> addMyTraining(TrainingSessionInput input) {
        UUID profileId = currentUser.requireProfile().id();
        TrainingKind kind = input.getKind();
        TrainingSession session = fuel.addManual(
                profileId,
                new TrainingSessionDraft(
                        present(input.getDate(), "date"),
                        kind == null ? Kind.PLANNED : Kind.valueOf(kind.name()),
                        Sport.valueOf(present(input.getSport(), "sport").name()),
                        present(input.getDurationMinutes(), "durationMinutes"),
                        fromBigDecimal(input.getWorkKj()),
                        Intensity.valueOf(
                                present(input.getIntensity(), "intensity").name())));
        return ResponseEntity.status(201).body(toApiSession(session));
    }

    @Override
    public ResponseEntity<org.openapitools.model.TrainingSession> getMyTrainingSession(UUID sessionId) {
        UUID profileId = currentUser.requireProfile().id();
        return ResponseEntity.ok(toApiSession(fuel.session(profileId, sessionId)));
    }

    @Override
    public ResponseEntity<Void> deleteMyTraining(UUID sessionId) {
        UUID profileId = currentUser.requireProfile().id();
        fuel.deleteManual(profileId, sessionId);
        return ResponseEntity.noContent().build();
    }

    private static org.openapitools.model.FuelProfile toApiProfile(FuelProfile profile) {
        Double bodyFat = profile.bodyFatPercent();
        return new org.openapitools.model.FuelProfile()
                .enabled(profile.enabled())
                .weightKg(BigDecimal.valueOf(profile.weightKg()))
                .sex(FuelSex.valueOf(profile.sex().name()))
                .bodyFatPercent(bodyFat == null ? null : BigDecimal.valueOf(bodyFat))
                .ftpWatts(profile.ftpWatts())
                .goal(FuelGoal.valueOf(profile.goal().name()))
                .proteinGPerKg(BigDecimal.valueOf(profile.proteinGPerKg()));
    }

    private static org.openapitools.model.TrainingSession toApiSession(TrainingSession session) {
        Double workKj = session.workKj();
        Intensity intensity = session.intensity();
        return new org.openapitools.model.TrainingSession()
                .id(session.id())
                .date(session.date())
                .source(TrainingSource.valueOf(session.source().name()))
                .kind(TrainingKind.valueOf(session.kind().name()))
                .sport(TrainingSport.valueOf(session.sport().name()))
                .durationMinutes(session.durationMinutes())
                .workKj(workKj == null ? null : BigDecimal.valueOf(workKj))
                .intensity(intensity == null ? null : TrainingIntensity.valueOf(intensity.name()));
    }

    private static FuelTarget toApiTarget(DailyTarget target) {
        TargetBasis basis = target.basis();
        return new FuelTarget()
                .calories(whole(target.calories()))
                .proteinG(whole(target.proteinG()))
                .carbsG(whole(target.carbsG()))
                .fatG(whole(target.fatG()))
                .basis(new FuelTargetBasis()
                        .energyAvailabilityKcalPerKgFfm(basis.energyAvailabilityKcalPerKgFfm())
                        .bodyFatPercent(BigDecimal.valueOf(basis.bodyFatPercent()))
                        .bodyFatSource(
                                BodyFatSource.valueOf(basis.bodyFatSource().name()))
                        .fatFreeMassKg(BigDecimal.valueOf(basis.fatFreeMassKg()).setScale(1, RoundingMode.HALF_UP))
                        .exerciseKcal(whole(basis.exerciseKcal()))
                        .sessions(basis.countedSessions().stream()
                                .map(FuelController::toApiSessionEnergy)
                                .toList())
                        .loadBand(LoadBand.valueOf(basis.loadBand().name()))
                        .carbsLeftBand(basis.carbsLeftBand()));
    }

    private static org.openapitools.model.SessionEnergy toApiSessionEnergy(SessionEnergy energy) {
        return new org.openapitools.model.SessionEnergy()
                .sessionId(energy.sessionId())
                .kcal(whole(energy.kcal()))
                .method(EnergyMethod.valueOf(energy.method().name()));
    }

    private static org.openapitools.model.PlannedDayTotal toApiPlanned(PlannedDayTotal day) {
        return new org.openapitools.model.PlannedDayTotal()
                .date(day.date())
                .total(RecipeApiMapper.toApiNutrition(day.total()))
                .meals(day.meals())
                .mealsWithoutNutrition(day.mealsWithoutNutrition());
    }

    private static Nutrition total(double calories, double proteinG, double carbsG, double fatG) {
        return new Nutrition()
                .calories(BigDecimal.valueOf(calories))
                .proteinG(BigDecimal.valueOf(proteinG))
                .carbsG(BigDecimal.valueOf(carbsG))
                .fatG(BigDecimal.valueOf(fatG));
    }

    private static int whole(double value) {
        return (int) Math.round(value);
    }

    private static <T> T present(@Nullable T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static @Nullable Double fromBigDecimal(@Nullable BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
