package io.github.noahhhx.mimos.fueling.targets;

import java.util.List;

public record TargetBasis(
        int energyAvailabilityKcalPerKgFfm,
        double bodyFatPercent,
        BodyFatSource bodyFatSource,
        double fatFreeMassKg,
        List<SessionEnergy> countedSessions,
        LoadBand loadBand,
        boolean carbsLeftBand) {

    public TargetBasis {
        countedSessions = List.copyOf(countedSessions);
    }

    public double exerciseKcal() {
        return countedSessions.stream().mapToDouble(SessionEnergy::kcal).sum();
    }
}
