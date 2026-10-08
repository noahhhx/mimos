package io.github.noahhhx.mimos.fueling.targets;

/** A day's training load by its total minutes, with its carbohydrate range (Burke et al. 2011). */
public enum LoadBand {
    LIGHT(0, 3, 5),
    MODERATE(1, 5, 7),
    HIGH(75, 6, 10),
    VERY_HIGH(240, 8, 12);

    private final int minMinutes;
    private final int carbsMinGPerKg;
    private final int carbsMaxGPerKg;

    LoadBand(int minMinutes, int carbsMinGPerKg, int carbsMaxGPerKg) {
        this.minMinutes = minMinutes;
        this.carbsMinGPerKg = carbsMinGPerKg;
        this.carbsMaxGPerKg = carbsMaxGPerKg;
    }

    public static LoadBand forMinutes(int minutes) {
        LoadBand band = LIGHT;
        for (LoadBand candidate : values()) {
            if (minutes >= candidate.minMinutes) {
                band = candidate;
            }
        }
        return band;
    }

    public int carbsMinGPerKg() {
        return carbsMinGPerKg;
    }

    public int carbsMaxGPerKg() {
        return carbsMaxGPerKg;
    }
}
