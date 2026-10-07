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

    public FuelProfile {
        List<String> problems = new ArrayList<>();
        if (!within(weightKg, 30, 250)) {
            problems.add("weightKg must be between 30 and 250");
        }
        if (bodyFatPercent != null && !within(bodyFatPercent, 3, 60)) {
            problems.add("bodyFatPercent must be between 3 and 60");
        }
        if (ftpWatts != null && !within(ftpWatts, 50, 600)) {
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
