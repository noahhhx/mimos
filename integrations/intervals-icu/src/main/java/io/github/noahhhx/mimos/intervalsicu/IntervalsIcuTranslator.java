package io.github.noahhhx.mimos.intervalsicu;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import io.github.noahhhx.mimos.fueling.Intensity;
import io.github.noahhhx.mimos.fueling.Kind;
import io.github.noahhhx.mimos.fueling.Sex;
import io.github.noahhhx.mimos.fueling.Source;
import io.github.noahhhx.mimos.fueling.Sport;
import io.github.noahhhx.mimos.fueling.TrainingSession;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns intervals.icu's records into core-fueling types (ADR-0022): planned
 * workouts into {@code PLANNED} sessions, activities into
 * {@code COMPLETED} ones, and wellness and the athlete record into
 * {@link ProfileHints}. A record a session cannot be built from is left
 * out, never guessed at.
 */
final class IntervalsIcuTranslator {

    private static final Logger log = LoggerFactory.getLogger(IntervalsIcuTranslator.class);

    /**
     * {@code icu_intensity} is a percentage of FTP. These are the midpoints
     * between the calculator's intensity factors (55, 68, and 85%), so each
     * workout takes the nearest one.
     */
    private static final double ENDURANCE_FROM_PERCENT = 61.5;

    private static final double HARD_FROM_PERCENT = 76.5;

    /** E-bikes stay out of {@code RIDE}: a motor's help is not the rider's FTP. */
    private static final Map<String, Sport> SPORTS = Map.ofEntries(
            Map.entry("Ride", Sport.RIDE),
            Map.entry("VirtualRide", Sport.RIDE),
            Map.entry("GravelRide", Sport.RIDE),
            Map.entry("MountainBikeRide", Sport.RIDE),
            Map.entry("TrackRide", Sport.RIDE),
            Map.entry("Cyclocross", Sport.RIDE),
            Map.entry("Velomobile", Sport.RIDE),
            Map.entry("Handcycle", Sport.RIDE),
            Map.entry("Run", Sport.RUN),
            Map.entry("VirtualRun", Sport.RUN),
            Map.entry("TrailRun", Sport.RUN),
            Map.entry("Swim", Sport.SWIM),
            Map.entry("OpenWaterSwim", Sport.SWIM),
            Map.entry("WeightTraining", Sport.STRENGTH));

    private IntervalsIcuTranslator() {}

    static List<TrainingSession> planned(UUID profileId, List<IcuJson.Event> events) {
        List<TrainingSession> sessions = new ArrayList<>();
        for (IcuJson.Event event : events) {
            Long id = event.id();
            TrainingSession session = session(
                    profileId,
                    Kind.PLANNED,
                    id == null ? null : id.toString(),
                    event.startDateLocal(),
                    event.type(),
                    event.movingTime(),
                    event.joules(),
                    intensity(event.icuIntensity()));
            if (session != null) {
                sessions.add(session);
            }
        }
        return List.copyOf(sessions);
    }

    static List<TrainingSession> completed(UUID profileId, List<IcuJson.Activity> activities) {
        List<TrainingSession> sessions = new ArrayList<>();
        for (IcuJson.Activity activity : activities) {
            TrainingSession session = session(
                    profileId,
                    Kind.COMPLETED,
                    activity.id(),
                    activity.startDateLocal(),
                    activity.type(),
                    activity.movingTime(),
                    activity.icuJoules(),
                    null);
            if (session != null) {
                sessions.add(session);
            }
        }
        return List.copyOf(sessions);
    }

    /** The latest wellness value of each kind wins over the athlete record's. */
    static ProfileHints hints(List<IcuJson.Wellness> wellness, IcuJson.Athlete athlete) {
        List<IcuJson.Wellness> newestFirst = wellness.stream()
                .sorted(comparing(IcuJson.Wellness::id, nullsFirst(naturalOrder()))
                        .reversed())
                .toList();
        Double weightKg = latest(newestFirst, IcuJson.Wellness::weight);
        Double athleteWeight = athlete.weight();
        if (weightKg == null && athleteWeight != null && athleteWeight > 0) {
            weightKg = athleteWeight;
        }
        return new ProfileHints(
                weightKg, latest(newestFirst, IcuJson.Wellness::bodyFat), sex(athlete.sex()), rideFtp(athlete));
    }

    private static @Nullable TrainingSession session(
            UUID profileId,
            Kind kind,
            @Nullable String externalId,
            @Nullable String startDateLocal,
            @Nullable String type,
            @Nullable Integer movingSeconds,
            @Nullable Long joules,
            @Nullable Intensity intensity) {
        if (externalId == null || startDateLocal == null || movingSeconds == null) {
            return null;
        }
        try {
            return new TrainingSession(
                    sessionId(profileId, kind, externalId),
                    profileId,
                    LocalDate.parse(startDateLocal.substring(0, Math.min(10, startDateLocal.length()))),
                    Source.INTERVALS_ICU,
                    kind,
                    sport(type),
                    Math.round(movingSeconds / 60f),
                    joules == null ? null : joules / 1000.0,
                    intensity,
                    externalId);
        } catch (IllegalArgumentException | DateTimeException e) {
            log.debug("Left out intervals.icu {} session {}: {}", kind, externalId, e.getMessage());
            return null;
        }
    }

    /** The same record always gets the same id, so a session keeps its id across syncs. */
    private static UUID sessionId(UUID profileId, Kind kind, String externalId) {
        return UUID.nameUUIDFromBytes((profileId + "/" + kind + "/" + externalId).getBytes(StandardCharsets.UTF_8));
    }

    private static Sport sport(@Nullable String type) {
        return type == null ? Sport.OTHER : SPORTS.getOrDefault(type, Sport.OTHER);
    }

    private static @Nullable Intensity intensity(@Nullable Double percent) {
        if (percent == null || percent <= 0) {
            return null;
        }
        if (percent < ENDURANCE_FROM_PERCENT) {
            return Intensity.EASY;
        }
        return percent < HARD_FROM_PERCENT ? Intensity.ENDURANCE : Intensity.HARD;
    }

    private static @Nullable Double latest(
            List<IcuJson.Wellness> newestFirst, Function<IcuJson.Wellness, @Nullable Double> field) {
        return newestFirst.stream()
                .map(field)
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .findFirst()
                .orElse(null);
    }

    private static @Nullable Sex sex(@Nullable String sex) {
        if ("M".equals(sex)) {
            return Sex.MALE;
        }
        return "F".equals(sex) ? Sex.FEMALE : null;
    }

    private static @Nullable Integer rideFtp(IcuJson.Athlete athlete) {
        List<IcuJson.SportSettings> settings = athlete.sportSettings();
        if (settings == null) {
            return null;
        }
        for (IcuJson.SportSettings setting : settings) {
            List<String> types = setting.types();
            Integer ftp = setting.ftp();
            if (types != null && types.contains("Ride") && ftp != null && ftp > 0) {
                return ftp;
            }
        }
        return null;
    }
}
