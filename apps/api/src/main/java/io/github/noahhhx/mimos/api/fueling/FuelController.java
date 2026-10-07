package io.github.noahhhx.mimos.api.fueling;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.fueling.FuelProfile;
import io.github.noahhhx.mimos.fueling.FuelService;
import io.github.noahhhx.mimos.fueling.Goal;
import io.github.noahhhx.mimos.fueling.Intensity;
import io.github.noahhhx.mimos.fueling.Kind;
import io.github.noahhhx.mimos.fueling.Sex;
import io.github.noahhhx.mimos.fueling.Sport;
import io.github.noahhhx.mimos.fueling.TrainingSession;
import io.github.noahhhx.mimos.fueling.TrainingSessionDraft;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.openapitools.api.FuelApi;
import org.openapitools.model.FuelGoal;
import org.openapitools.model.FuelProfileInput;
import org.openapitools.model.FuelSex;
import org.openapitools.model.TrainingIntensity;
import org.openapitools.model.TrainingKind;
import org.openapitools.model.TrainingSessionInput;
import org.openapitools.model.TrainingSource;
import org.openapitools.model.TrainingSport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FuelController implements FuelApi {

    private final FuelService fuel;
    private final CurrentUserService currentUser;

    public FuelController(FuelService fuel, CurrentUserService currentUser) {
        this.fuel = fuel;
        this.currentUser = currentUser;
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
