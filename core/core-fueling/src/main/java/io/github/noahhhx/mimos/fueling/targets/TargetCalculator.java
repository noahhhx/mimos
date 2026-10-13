package io.github.noahhhx.mimos.fueling.targets;

import io.github.noahhhx.mimos.fueling.FuelProfile;
import io.github.noahhhx.mimos.fueling.Intensity;
import io.github.noahhhx.mimos.fueling.Kind;
import io.github.noahhhx.mimos.fueling.Sport;
import io.github.noahhhx.mimos.fueling.TrainingSession;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class TargetCalculator {

    private static final double STARTING_FAT_SHARE = 0.25;
    private static final double MIN_FAT_SHARE = 0.20;
    private static final double MAX_FAT_SHARE = 0.35;
    private static final double FLOAT_TOLERANCE_G = 1e-6;

    private static final double KCAL_PER_G_PROTEIN = 4;
    private static final double KCAL_PER_G_CARBS = 4;
    private static final double KCAL_PER_G_FAT = 9;

    private TargetCalculator() {}

    public static DailyTarget target(
            LocalDate date, FuelProfile profile, List<TrainingSession> day, @Nullable Double syncedBodyFatPercent) {
        BodyFat bodyFat = bodyFat(profile, syncedBodyFatPercent);
        double weightKg = profile.weightKg();
        double fatFreeMassKg = weightKg * (1 - bodyFat.percent() / 100);

        List<TrainingSession> counted = counted(day);
        List<SessionEnergy> countedSessions =
                counted.stream().map(session -> energy(session, profile)).toList();
        LoadBand band = LoadBand.forMinutes(
                counted.stream().mapToInt(TrainingSession::durationMinutes).sum());

        int level = profile.goal().energyAvailabilityKcalPerKgFfm();
        double calories = level * fatFreeMassKg
                + countedSessions.stream().mapToDouble(SessionEnergy::kcal).sum();
        double proteinG = profile.proteinGPerKg() * weightKg;
        double nonProteinKcal = calories - proteinG * KCAL_PER_G_PROTEIN;
        double carbsInBand = Math.clamp(
                (nonProteinKcal - STARTING_FAT_SHARE * calories) / KCAL_PER_G_CARBS,
                band.carbsMinGPerKg() * weightKg,
                band.carbsMaxGPerKg() * weightKg);
        double carbsG = carbsKeepingFatInShare(carbsInBand, calories, nonProteinKcal);
        double fatG = (nonProteinKcal - carbsG * KCAL_PER_G_CARBS) / KCAL_PER_G_FAT;

        TargetBasis basis = new TargetBasis(
                level,
                bodyFat.percent(),
                bodyFat.source(),
                fatFreeMassKg,
                countedSessions,
                band,
                Math.abs(carbsG - carbsInBand) > FLOAT_TOLERANCE_G);
        return new DailyTarget(date, calories, proteinG, carbsG, fatG, basis);
    }

    private record BodyFat(double percent, BodyFatSource source) {}

    private static BodyFat bodyFat(FuelProfile profile, @Nullable Double syncedPercent) {
        Double profilePercent = profile.bodyFatPercent();
        if (profilePercent != null) {
            return new BodyFat(profilePercent, BodyFatSource.PROFILE);
        }
        if (syncedPercent != null) {
            return new BodyFat(syncedPercent, BodyFatSource.INTERVALS_ICU);
        }
        double byDefault =
                switch (profile.sex()) {
                    case MALE -> 15;
                    case FEMALE -> 25;
                    case UNSPECIFIED -> 20;
                };
        return new BodyFat(byDefault, BodyFatSource.DEFAULT);
    }

    /**
     * Moves carbs the least amount that puts fat within its share of
     * calories. Protein above 80% of calories leaves no such amount, so
     * carbs stop at zero and fat stays under 20%: calories never move.
     */
    private static double carbsKeepingFatInShare(double carbsG, double calories, double nonProteinKcal) {
        return Math.max(
                0,
                Math.clamp(
                        carbsG,
                        (nonProteinKcal - MAX_FAT_SHARE * calories) / KCAL_PER_G_CARBS,
                        (nonProteinKcal - MIN_FAT_SHARE * calories) / KCAL_PER_G_CARBS));
    }

    private static List<TrainingSession> counted(List<TrainingSession> day) {
        List<TrainingSession> completed =
                day.stream().filter(session -> session.kind() == Kind.COMPLETED).toList();
        return completed.isEmpty() ? day : completed;
    }

    private static SessionEnergy energy(TrainingSession session, FuelProfile profile) {
        Double workKj = session.workKj();
        if (workKj != null) {
            return new SessionEnergy(
                    session.id(),
                    kcalFromWorkKj(workKj),
                    session.kind() == Kind.COMPLETED ? EnergyMethod.COMPLETED_WORK : EnergyMethod.PLANNED_WORK);
        }
        Intensity sessionIntensity = session.intensity();
        Intensity intensity = sessionIntensity == null ? Intensity.ENDURANCE : sessionIntensity;
        Integer ftpWatts = profile.ftpWatts();
        if (ftpWatts != null && session.sport() == Sport.RIDE) {
            double workKjAtFtp = ftpWatts * intensityFactor(intensity) * session.durationMinutes() * 60 / 1000;
            return new SessionEnergy(session.id(), kcalFromWorkKj(workKjAtFtp), EnergyMethod.FTP_INTENSITY);
        }
        Integer reportedKcal = session.reportedKcal();
        if (reportedKcal != null) {
            return new SessionEnergy(session.id(), reportedKcal, EnergyMethod.REPORTED_CALORIES);
        }
        double kcalPerMinute = mets(intensity) * 3.5 * profile.weightKg() / 200;
        return new SessionEnergy(session.id(), kcalPerMinute * session.durationMinutes(), EnergyMethod.ESTIMATE);
    }

    /** At a gross efficiency near 24%, the 4.184 kJ per kcal cancels out (ADR-0022). */
    private static double kcalFromWorkKj(double workKj) {
        return workKj;
    }

    private static double intensityFactor(Intensity intensity) {
        return switch (intensity) {
            case EASY -> 0.55;
            case ENDURANCE -> 0.68;
            case HARD -> 0.85;
        };
    }

    /** Compendium of Physical Activities values that hold across sports, converted per ACSM. */
    private static double mets(Intensity intensity) {
        return switch (intensity) {
            case EASY -> 4;
            case ENDURANCE -> 7;
            case HARD -> 10;
        };
    }
}
