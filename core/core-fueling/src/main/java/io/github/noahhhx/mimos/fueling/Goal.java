package io.github.noahhhx.mimos.fueling;

public enum Goal {
    FUEL(45, 1.6),
    GRADUAL_LOSS(40, 2.0),
    FASTER_LOSS(35, 2.2),
    GAIN(50, 1.8);

    private final int energyAvailabilityKcalPerKgFfm;
    private final double defaultProteinGPerKg;

    Goal(int energyAvailabilityKcalPerKgFfm, double defaultProteinGPerKg) {
        this.energyAvailabilityKcalPerKgFfm = energyAvailabilityKcalPerKgFfm;
        this.defaultProteinGPerKg = defaultProteinGPerKg;
    }

    public int energyAvailabilityKcalPerKgFfm() {
        return energyAvailabilityKcalPerKgFfm;
    }

    public double defaultProteinGPerKg() {
        return defaultProteinGPerKg;
    }
}
