package io.github.noahhhx.mimos.intervalsicu;

import static org.assertj.core.api.Assertions.assertThat;

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

class IntervalsIcuTranslatorTests {

    private static final UUID PROFILE = UUID.fromString("7d0c6f4e-2b1a-4c55-9e0e-0f6d1d5f0a01");

    @Test
    void aPlannedRideWithJoulesCarriesItsWork() {
        TrainingSession ride = IntervalsIcuTranslator.planned(PROFILE, events()).get(0);

        assertThat(ride.profileId()).isEqualTo(PROFILE);
        assertThat(ride.date()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(ride.source()).isEqualTo(Source.INTERVALS_ICU);
        assertThat(ride.kind()).isEqualTo(Kind.PLANNED);
        assertThat(ride.sport()).isEqualTo(Sport.RIDE);
        assertThat(ride.durationMinutes()).isEqualTo(90);
        assertThat(ride.workKj()).isEqualTo(1360.8);
        assertThat(ride.intensity()).isEqualTo(Intensity.HARD);
        assertThat(ride.externalId()).isEqualTo("41870231");
    }

    @Test
    void aPlannedRideWithoutJoulesHasNoWork() {
        TrainingSession ride = IntervalsIcuTranslator.planned(PROFILE, events()).get(1);

        assertThat(ride.date()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(ride.sport()).isEqualTo(Sport.RIDE);
        assertThat(ride.durationMinutes()).isEqualTo(120);
        assertThat(ride.workKj()).isNull();
        assertThat(ride.intensity()).isEqualTo(Intensity.ENDURANCE);
        assertThat(ride.externalId()).isEqualTo("41870232");
    }

    @Test
    void aCompletedActivityCarriesItsIcuJoules() {
        List<TrainingSession> sessions = IntervalsIcuTranslator.completed(
                PROFILE, List.of(Fixtures.read("activities.json", IcuJson.Activity[].class)));

        assertThat(sessions).singleElement().satisfies(gravel -> {
            assertThat(gravel.date()).isEqualTo(LocalDate.of(2026, 10, 11));
            assertThat(gravel.source()).isEqualTo(Source.INTERVALS_ICU);
            assertThat(gravel.kind()).isEqualTo(Kind.COMPLETED);
            assertThat(gravel.sport()).isEqualTo(Sport.RIDE);
            assertThat(gravel.durationMinutes()).isEqualTo(186);
            assertThat(gravel.workKj()).isEqualTo(2203.56);
            assertThat(gravel.externalId()).isEqualTo("i98765432");
            assertThat(gravel.reportedKcal()).isEqualTo(2410);
        });
    }

    @Test
    void aRunWithOnlyCaloriesCarriesThemAsReported() {
        List<TrainingSession> sessions = IntervalsIcuTranslator.completed(
                PROFILE, List.of(new IcuJson.Activity("i5", "2026-10-11T07:00:00", "Run", 2700, null, 512)));

        assertThat(sessions).singleElement().satisfies(run -> {
            assertThat(run.sport()).isEqualTo(Sport.RUN);
            assertThat(run.workKj()).isNull();
            assertThat(run.reportedKcal()).isEqualTo(512);
        });
    }

    @Test
    void zeroOrWildCaloriesAreNoReport() {
        List<TrainingSession> sessions = IntervalsIcuTranslator.completed(
                PROFILE,
                List.of(
                        new IcuJson.Activity("i5", "2026-10-11T07:00:00", "Run", 2700, null, 0),
                        new IcuJson.Activity("i6", "2026-10-11T08:00:00", "Run", 2700, null, 99_999)));

        assertThat(sessions)
                .hasSize(2)
                .allSatisfy(run -> assertThat(run.reportedKcal()).isNull());
    }

    @Test
    void wellnessAndTheAthleteRecordBecomeHints() {
        ProfileHints hints = IntervalsIcuTranslator.hints(
                List.of(Fixtures.read("wellness.json", IcuJson.Wellness[].class)),
                Fixtures.read("athlete.json", IcuJson.Athlete.class));

        assertThat(hints).isEqualTo(new ProfileHints(70.8, 13.6, Sex.MALE, 282));
    }

    @Test
    void withoutWellnessTheAthleteRecordGivesTheWeight() {
        ProfileHints hints =
                IntervalsIcuTranslator.hints(List.of(), Fixtures.read("athlete.json", IcuJson.Athlete.class));

        assertThat(hints).isEqualTo(new ProfileHints(72.0, null, Sex.MALE, 282));
    }

    @Test
    void hintsAFuelProfileWouldRefuseAreLeftOut() {
        ProfileHints hints = IntervalsIcuTranslator.hints(
                List.of(new IcuJson.Wellness("2026-10-11", 400.0, 1.5)),
                new IcuJson.Athlete("X", null, List.of(new IcuJson.SportSettings(List.of("Ride"), 2000))));

        assertThat(hints).isEqualTo(new ProfileHints(null, null, null, null));
    }

    @Test
    void activityTypesFoldOntoSports() {
        assertThat(sportOf("Ride")).isEqualTo(Sport.RIDE);
        assertThat(sportOf("VirtualRide")).isEqualTo(Sport.RIDE);
        assertThat(sportOf("TrailRun")).isEqualTo(Sport.RUN);
        assertThat(sportOf("OpenWaterSwim")).isEqualTo(Sport.SWIM);
        assertThat(sportOf("WeightTraining")).isEqualTo(Sport.STRENGTH);
        assertThat(sportOf("EBikeRide")).isEqualTo(Sport.OTHER);
        assertThat(sportOf("Yoga")).isEqualTo(Sport.OTHER);
        assertThat(sportOf(null)).isEqualTo(Sport.OTHER);
    }

    @Test
    void intensityTakesTheNearestLevel() {
        assertThat(intensityOf(50.0)).isEqualTo(Intensity.EASY);
        assertThat(intensityOf(61.4)).isEqualTo(Intensity.EASY);
        assertThat(intensityOf(61.5)).isEqualTo(Intensity.ENDURANCE);
        assertThat(intensityOf(76.4)).isEqualTo(Intensity.ENDURANCE);
        assertThat(intensityOf(76.5)).isEqualTo(Intensity.HARD);
        assertThat(intensityOf(null)).isNull();
    }

    @Test
    void recordsASessionCannotBeBuiltFromAreLeftOut() {
        List<IcuJson.Event> events = List.of(
                new IcuJson.Event(1L, "2026-10-13T00:00:00", "Ride", null, null, null),
                new IcuJson.Event(2L, "2026-10-13T00:00:00", "Ride", 0, null, null),
                new IcuJson.Event(3L, null, "Ride", 3600, null, null),
                new IcuJson.Event(null, "2026-10-13T00:00:00", "Ride", 3600, null, null),
                new IcuJson.Event(5L, "13/10/2026", "Ride", 3600, null, null),
                new IcuJson.Event(6L, "2026-10-13T00:00:00", "Ride", 3600, -5L, null),
                new IcuJson.Event(7L, "2026-10-13", "Ride", 3600, null, null));

        assertThat(IntervalsIcuTranslator.planned(PROFILE, events))
                .extracting(TrainingSession::externalId)
                .containsExactly("7");
    }

    @Test
    void aRecordKeepsItsSessionIdAcrossSyncs() {
        UUID first = IntervalsIcuTranslator.planned(PROFILE, events()).get(0).id();
        UUID again = IntervalsIcuTranslator.planned(PROFILE, events()).get(0).id();
        UUID otherPerson = IntervalsIcuTranslator.planned(UUID.randomUUID(), events())
                .get(0)
                .id();

        assertThat(again).isEqualTo(first);
        assertThat(otherPerson).isNotEqualTo(first);
    }

    private static List<IcuJson.Event> events() {
        return List.of(Fixtures.read("events.json", IcuJson.Event[].class));
    }

    private static Sport sportOf(@Nullable String type) {
        return IntervalsIcuTranslator.planned(
                        PROFILE, List.of(new IcuJson.Event(1L, "2026-10-13T00:00:00", type, 3600, null, null)))
                .get(0)
                .sport();
    }

    private static @Nullable Intensity intensityOf(@Nullable Double percent) {
        return IntervalsIcuTranslator.planned(
                        PROFILE, List.of(new IcuJson.Event(1L, "2026-10-13T00:00:00", "Ride", 3600, null, percent)))
                .get(0)
                .intensity();
    }
}
