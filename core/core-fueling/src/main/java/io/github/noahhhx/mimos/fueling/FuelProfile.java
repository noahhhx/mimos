package io.github.noahhhx.mimos.fueling;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FuelProfile(
        UUID profileId,
        boolean enabled,
        double weightKg,
        Sex sex,
        @Nullable Double bodyFatPercent,
        @Nullable Integer ftpWatts,
        Goal goal,
        double proteinGPerKg) {

    public static final double MIN_WEIGHT_KG = 30;
    public static final double MAX_WEIGHT_KG = 250;
    public static final double MIN_BODY_FAT_PERCENT = 3;
    public static final double MAX_BODY_FAT_PERCENT = 60;
    public static final int MIN_FTP_WATTS = 50;
    public static final int MAX_FTP_WATTS = 600;

    public FuelProfile {
        List<String> problems = new ArrayList<>();
        if (!within(weightKg, MIN_WEIGHT_KG, MAX_WEIGHT_KG)) {
            problems.add("weightKg must be between 30 and 250");
        }
        if (bodyFatPercent != null && !within(bodyFatPercent, MIN_BODY_FAT_PERCENT, MAX_BODY_FAT_PERCENT)) {
            problems.add("bodyFatPercent must be between 3 and 60");
        }
        if (ftpWatts != null && !within(ftpWatts, MIN_FTP_WATTS, MAX_FTP_WATTS)) {
            problems.add("ftpWatts must be between 50 and 600");
        }
        if (!within(proteinGPerKg, 1.2, 3.0)) {
            problems.add("proteinGPerKg must be between 1.2 and 3.0");
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }

    private static boolean within(double value, double min, double max) {
        return value >= min && value <= max;
    }
}
