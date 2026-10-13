package io.github.noahhhx.mimos.fueling.targets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.noahhhx.mimos.fueling.FuelProfile;
import io.github.noahhhx.mimos.fueling.Goal;
import io.github.noahhhx.mimos.fueling.Intensity;
import io.github.noahhhx.mimos.fueling.Kind;
import io.github.noahhhx.mimos.fueling.Sex;
import io.github.noahhhx.mimos.fueling.Source;
import io.github.noahhhx.mimos.fueling.Sport;
import io.github.noahhhx.mimos.fueling.TrainingSession;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TargetCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    private static final UUID PROFILE_ID = UUID.randomUUID();

    @ParameterizedTest(name = "{0} {1} min at {2}")
    @CsvSource(textBlock = """
            FUEL,           0,   0,    2677.50, 112, 350.00,  92.17, false
            FUEL,           60,  0.9,  3584.70, 112, 490.00, 130.74, false
            FUEL,           90,  0.7,  3735.90, 112, 588.48, 103.77, false
            FUEL,           60,  0.85, 3534.30, 112, 490.00, 125.14, false
            FUEL,           240, 0.68, 5419.26, 112, 840.00, 179.03, false
            FUEL,           120, 0.78, 4249.98, 112, 684.87, 118.05, false
            GRADUAL_LOSS,   0,   0,    2380.00, 140, 306.25,  66.11, false
            GRADUAL_LOSS,   60,  0.9,  3287.20, 140, 476.35,  91.31, false
            GRADUAL_LOSS,   90,  0.7,  3438.40, 140, 504.70,  95.51, false
            GRADUAL_LOSS,   60,  0.85, 3236.80, 140, 466.90,  89.91, false
            GRADUAL_LOSS,   240, 0.68, 5121.76, 140, 820.33, 142.27, false
            GRADUAL_LOSS,   120, 0.78, 3952.48, 140, 601.09, 109.79, false
            FASTER_LOSS,    0,   0,    2082.50, 154, 236.47,  57.85, false
            FASTER_LOSS,    60,  0.9,  2989.70, 154, 406.57,  83.05, false
            FASTER_LOSS,    90,  0.7,  3140.90, 154, 434.92,  87.25, false
            FASTER_LOSS,    60,  0.85, 2939.30, 154, 397.12,  81.65, false
            FASTER_LOSS,    240, 0.68, 4824.26, 154, 750.55, 134.01, false
            FASTER_LOSS,    120, 0.78, 3654.98, 154, 531.31, 101.53, false
            GAIN,           0,   0,    2975.00, 126, 357.44, 115.69, true
            GAIN,           60,  0.9,  3882.20, 126, 504.86, 150.97, true
            GAIN,           90,  0.7,  4033.40, 126, 630.26, 112.04, false
            GAIN,           60,  0.85, 3831.80, 126, 496.67, 149.01, true
            GAIN,           240, 0.68, 5716.76, 126, 840.00, 205.86, false
            GAIN,           120, 0.78, 4547.48, 126, 700.00, 138.16, false
            """)
    void matchesTheGoldenWeekOfA70KgRider(
            Goal goal,
            int minutes,
            double intensityFactor,
            double calories,
            double proteinG,
            double carbsG,
            double fatG,
            boolean carbsLeftBand) {
        List<TrainingSession> day = minutes == 0
                ? List.of()
                : List.of(
                        session(Kind.PLANNED, Sport.RIDE, minutes, 280 * intensityFactor * minutes * 60 / 1000, null));

        DailyTarget target = TargetCalculator.target(DAY, goldenRider(goal), day, null);

        assertThat(target.calories()).isCloseTo(calories, within(1.0));
        assertThat(target.proteinG()).isCloseTo(proteinG, within(1.0));
        assertThat(target.carbsG()).isCloseTo(carbsG, within(1.0));
        assertThat(target.fatG()).isCloseTo(fatG, within(1.0));
        assertThat(target.basis().carbsLeftBand()).isEqualTo(carbsLeftBand);
        assertThat(target.basis().fatFreeMassKg()).isCloseTo(59.5, within(1e-9));
    }

    @Test
    void aManualFourHourEnduranceRideAtFtp280IsTheGoldenLongDay() {
        DailyTarget target = TargetCalculator.target(
                DAY,
                goldenRider(Goal.FUEL),
                List.of(session(Kind.PLANNED, Sport.RIDE, 240, null, Intensity.ENDURANCE)),
                null);

        assertThat(target.calories()).isCloseTo(5419.26, within(0.01));
        assertThat(target.carbsG()).isCloseTo(840, within(0.01));
        assertThat(target.basis().loadBand()).isEqualTo(LoadBand.VERY_HIGH);
        assertThat(target.basis().countedSessions()).singleElement().satisfies(energy -> {
            assertThat(energy.method()).isEqualTo(EnergyMethod.FTP_INTENSITY);
            assertThat(energy.kcal()).isCloseTo(2741.76, within(0.01));
        });
    }

    @Test
    void completedWorkIsTheExerciseEnergy() {
        TrainingSession ride = session(Kind.COMPLETED, Sport.RIDE, 90, 1234.0, Intensity.HARD);

        assertThat(onlySession(goldenRider(Goal.FUEL), ride))
                .isEqualTo(new SessionEnergy(ride.id(), 1234.0, EnergyMethod.COMPLETED_WORK));
    }

    @Test
    void plannedWorkIsTheExerciseEnergy() {
        TrainingSession ride = session(Kind.PLANNED, Sport.RIDE, 90, 1000.0, Intensity.HARD);

        assertThat(onlySession(goldenRider(Goal.FUEL), ride))
                .isEqualTo(new SessionEnergy(ride.id(), 1000.0, EnergyMethod.PLANNED_WORK));
    }

    @ParameterizedTest
    @CsvSource({"EASY, 0.55", "ENDURANCE, 0.68", "HARD, 0.85"})
    void withoutWorkARideIsPricedFromFtpAndIntensity(Intensity intensity, double factor) {
        TrainingSession ride = session(Kind.PLANNED, Sport.RIDE, 60, null, intensity);

        SessionEnergy energy = onlySession(goldenRider(Goal.FUEL), ride);

        assertThat(energy.method()).isEqualTo(EnergyMethod.FTP_INTENSITY);
        assertThat(energy.kcal()).isCloseTo(280 * factor * 60 * 60 / 1000, within(1e-9));
    }

    @ParameterizedTest
    @CsvSource({"EASY, 4", "ENDURANCE, 7", "HARD, 10"})
    void withoutWorkOrFtpARideIsEstimatedByIntensityAndWeight(Intensity intensity, double mets) {
        TrainingSession ride = session(Kind.PLANNED, Sport.RIDE, 60, null, intensity);

        SessionEnergy energy = onlySession(profile(Goal.FUEL, 70, Sex.MALE, 15.0, null), ride);

        assertThat(energy.method()).isEqualTo(EnergyMethod.ESTIMATE);
        assertThat(energy.kcal()).isCloseTo(mets * 3.5 * 70 / 200 * 60, within(1e-9));
    }

    @Test
    void aSportOtherThanCyclingIsEstimatedEvenWithFtp() {
        TrainingSession run = session(Kind.PLANNED, Sport.RUN, 60, null, Intensity.HARD);

        SessionEnergy energy = onlySession(goldenRider(Goal.FUEL), run);

        assertThat(energy.method()).isEqualTo(EnergyMethod.ESTIMATE);
        assertThat(energy.kcal()).isCloseTo(10 * 3.5 * 70 / 200 * 60, within(1e-9));
    }

    @Test
    void aSyncedSessionWithNoWorkOrIntensityCountsAsEndurance() {
        TrainingSession run = new TrainingSession(
                UUID.randomUUID(),
                PROFILE_ID,
                DAY,
                Source.INTERVALS_ICU,
                Kind.COMPLETED,
                Sport.RUN,
                60,
                null,
                null,
                "i1",
                null);

        assertThat(onlySession(goldenRider(Goal.FUEL), run).kcal()).isCloseTo(7 * 3.5 * 70 / 200 * 60, within(1e-9));
    }

    @Test
    void reportedCaloriesPriceASessionWithNoWorkAndNoFtpForIt() {
        TrainingSession run = synced(Sport.RUN, null, 640);

        assertThat(onlySession(goldenRider(Goal.FUEL), run))
                .isEqualTo(new SessionEnergy(run.id(), 640, EnergyMethod.REPORTED_CALORIES));
    }

    @Test
    void reportedCaloriesNeverComeAheadOfWorkOrFtp() {
        TrainingSession withWork = synced(Sport.RIDE, 900.0, 640);
        TrainingSession rideWithFtp = synced(Sport.RIDE, null, 640);
        FuelProfile withoutFtp = profile(Goal.FUEL, 70, Sex.MALE, 15.0, null);

        assertThat(onlySession(goldenRider(Goal.FUEL), withWork).method()).isEqualTo(EnergyMethod.COMPLETED_WORK);
        assertThat(onlySession(goldenRider(Goal.FUEL), rideWithFtp).method()).isEqualTo(EnergyMethod.FTP_INTENSITY);
        assertThat(onlySession(withoutFtp, rideWithFtp).method()).isEqualTo(EnergyMethod.REPORTED_CALORIES);
    }

    private static TrainingSession synced(Sport sport, @Nullable Double workKj, @Nullable Integer reportedKcal) {
        return new TrainingSession(
                UUID.randomUUID(),
                PROFILE_ID,
                DAY,
                Source.INTERVALS_ICU,
                Kind.COMPLETED,
                sport,
                60,
                workKj,
                null,
                "i" + UUID.randomUUID(),
                reportedKcal);
    }

    @Test
    void completedSessionsReplacePlannedOnesForTheDay() {
        TrainingSession planned = session(Kind.PLANNED, Sport.RIDE, 120, 1500.0, null);
        TrainingSession completed = session(Kind.COMPLETED, Sport.RIDE, 180, 2000.0, null);

        DailyTarget target = TargetCalculator.target(DAY, goldenRider(Goal.FUEL), List.of(planned, completed), null);

        assertThat(target.basis().countedSessions())
                .containsExactly(new SessionEnergy(completed.id(), 2000.0, EnergyMethod.COMPLETED_WORK));
        assertThat(target.basis().exerciseKcal()).isEqualTo(2000.0);
        assertThat(target.calories()).isCloseTo(45 * 59.5 + 2000, within(1e-9));
        assertThat(target.basis().loadBand()).isEqualTo(LoadBand.HIGH);
    }

    @Test
    void plannedSessionsAddUpWhenNoneIsCompleted() {
        TrainingSession morning = session(Kind.PLANNED, Sport.RIDE, 120, 1000.0, null);
        TrainingSession evening = session(Kind.PLANNED, Sport.RUN, 120, 800.0, null);

        DailyTarget target = TargetCalculator.target(DAY, goldenRider(Goal.FUEL), List.of(morning, evening), null);

        assertThat(target.basis().exerciseKcal()).isEqualTo(1800.0);
        assertThat(target.basis().loadBand()).isEqualTo(LoadBand.VERY_HIGH);
    }

    @Test
    void bodyFatComesFromTheProfileThenIntervalsIcuThenTheDefaultBySex() {
        FuelProfile withBodyFat = profile(Goal.FUEL, 70, Sex.MALE, 12.0, null);
        FuelProfile without = profile(Goal.FUEL, 70, Sex.MALE, null, null);

        TargetBasis fromProfile =
                TargetCalculator.target(DAY, withBodyFat, List.of(), 18.0).basis();
        TargetBasis synced =
                TargetCalculator.target(DAY, without, List.of(), 18.0).basis();
        TargetBasis byDefault =
                TargetCalculator.target(DAY, without, List.of(), null).basis();

        assertThat(fromProfile.bodyFatSource()).isEqualTo(BodyFatSource.PROFILE);
        assertThat(fromProfile.fatFreeMassKg()).isCloseTo(61.6, within(1e-9));
        assertThat(synced.bodyFatSource()).isEqualTo(BodyFatSource.INTERVALS_ICU);
        assertThat(synced.fatFreeMassKg()).isCloseTo(57.4, within(1e-9));
        assertThat(byDefault.bodyFatSource()).isEqualTo(BodyFatSource.DEFAULT);
        assertThat(byDefault.fatFreeMassKg()).isCloseTo(59.5, within(1e-9));
    }

    @ParameterizedTest
    @CsvSource({"MALE, 15", "FEMALE, 25", "UNSPECIFIED, 20"})
    void theDefaultBodyFatFollowsSex(Sex sex, double bodyFatPercent) {
        TargetBasis basis = TargetCalculator.target(DAY, profile(Goal.FUEL, 80, sex, null, null), List.of(), null)
                .basis();

        assertThat(basis.bodyFatPercent()).isEqualTo(bodyFatPercent);
        assertThat(basis.fatFreeMassKg()).isCloseTo(80 * (1 - bodyFatPercent / 100), within(1e-9));
    }

    @ParameterizedTest
    @CsvSource({"0, LIGHT", "1, MODERATE", "74, MODERATE", "75, HIGH", "239, HIGH", "240, VERY_HIGH", "1440, VERY_HIGH"
    })
    void theLoadBandFollowsTheDaysMinutes(int minutes, LoadBand band) {
        assertThat(LoadBand.forMinutes(minutes)).isEqualTo(band);
    }

    @Test
    void fatStaysWithinItsShareAndCaloriesFollowEnergyAvailability() {
        int checked = 0;
        for (Goal goal : Goal.values()) {
            for (Sex sex : Sex.values()) {
                for (Integer ftp : new @Nullable Integer[] {null, 280}) {
                    for (Double bodyFat : new @Nullable Double[] {null, 3.0, 30.0, 50.0}) {
                        for (double protein : new double[] {1.2, 1.5, 1.8, 2.1, 2.4, 2.7, 3.0}) {
                            for (int weight = 45; weight <= 120; weight++) {
                                FuelProfile profile =
                                        new FuelProfile(PROFILE_ID, true, weight, sex, bodyFat, ftp, goal, protein);
                                for (int minutes : new int[] {0, 30, 74, 75, 150, 239, 240, 360}) {
                                    for (Intensity intensity : Intensity.values()) {
                                        List<TrainingSession> day = minutes == 0
                                                ? List.of()
                                                : List.of(session(Kind.PLANNED, Sport.RIDE, minutes, null, intensity));
                                        assertHoldsInvariants(
                                                TargetCalculator.target(DAY, profile, day, null), profile);
                                        checked++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(checked).isEqualTo(4 * 3 * 2 * 4 * 7 * 76 * 8 * 3);
    }

    @Test
    void whenProteinAloneIsOverEightyPercentCarbsAreZeroAndCaloriesStay() {
        FuelProfile profile = new FuelProfile(PROFILE_ID, true, 100, Sex.FEMALE, 60.0, null, Goal.FASTER_LOSS, 3.0);

        DailyTarget target = TargetCalculator.target(DAY, profile, List.of(), null);

        assertThat(target.calories()).isCloseTo(35 * 40, within(1e-9));
        assertThat(target.proteinG()).isCloseTo(300, within(1e-9));
        assertThat(target.carbsG()).isZero();
        assertThat(target.fatG()).isCloseTo(200.0 / 9, within(1e-9));
        assertThat(target.basis().carbsLeftBand()).isTrue();
    }

    private static void assertHoldsInvariants(DailyTarget target, FuelProfile profile) {
        TargetBasis basis = target.basis();
        String context = profile + " " + basis;
        assertThat(target.calories())
                .as(context)
                .isCloseTo(
                        profile.goal().energyAvailabilityKcalPerKgFfm() * basis.fatFreeMassKg() + basis.exerciseKcal(),
                        within(1e-6));
        assertThat(4 * target.proteinG() + 4 * target.carbsG() + 9 * target.fatG())
                .as(context)
                .isCloseTo(target.calories(), within(1e-6));
        double fatShare = 9 * target.fatG() / target.calories();
        assertThat(fatShare).as(context).isBetween(0.20 - 1e-9, 0.35 + 1e-9);
        double carbsGPerKg = target.carbsG() / profile.weightKg();
        boolean inBand = carbsGPerKg >= basis.loadBand().carbsMinGPerKg() - 1e-9
                && carbsGPerKg <= basis.loadBand().carbsMaxGPerKg() + 1e-9;
        assertThat(basis.carbsLeftBand()).as(context).isEqualTo(!inBand);
    }

    private static SessionEnergy onlySession(FuelProfile profile, TrainingSession session) {
        List<SessionEnergy> sessions = TargetCalculator.target(DAY, profile, List.of(session), null)
                .basis()
                .countedSessions();
        assertThat(sessions).hasSize(1);
        return sessions.getFirst();
    }

    private static FuelProfile goldenRider(Goal goal) {
        return profile(goal, 70, Sex.MALE, 15.0, 280);
    }

    private static FuelProfile profile(
            Goal goal, double weightKg, Sex sex, @Nullable Double bodyFatPercent, @Nullable Integer ftpWatts) {
        return new FuelProfile(
                PROFILE_ID, true, weightKg, sex, bodyFatPercent, ftpWatts, goal, goal.defaultProteinGPerKg());
    }

    private static TrainingSession session(
            Kind kind, Sport sport, int minutes, @Nullable Double workKj, @Nullable Intensity intensity) {
        return new TrainingSession(
                UUID.randomUUID(), PROFILE_ID, DAY, Source.MANUAL, kind, sport, minutes, workKj, intensity, null, null);
    }
}
