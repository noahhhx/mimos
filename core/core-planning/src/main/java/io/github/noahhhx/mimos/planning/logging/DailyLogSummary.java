package io.github.noahhhx.mimos.planning.logging;

import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/** Per-day calorie and macro totals for a date range. */
public record DailyLogSummary(LocalDate date, double calories, double proteinG, double carbsG, double fatG) {

    /** Adds another day's totals onto this one (same date assumed). */
    public DailyLogSummary plus(DailyLogSummary other) {
        return new DailyLogSummary(
                date,
                calories + other.calories(),
                proteinG + other.proteinG(),
                carbsG + other.carbsG(),
                fatG + other.fatG());
    }

    /** Totals per day, in date order; days without logs are omitted. */
    public static List<DailyLogSummary> summarize(List<MealLog> logs) {
        Map<LocalDate, DailyLogSummary> byDay = new TreeMap<>();
        for (MealLog log : logs) {
            Nutrition nutrition = log.nutrition();
            DailyLogSummary addend = new DailyLogSummary(
                    log.date(),
                    orZero(nutrition.calories()),
                    orZero(nutrition.proteinG()),
                    orZero(nutrition.carbsG()),
                    orZero(nutrition.fatG()));
            byDay.merge(log.date(), addend, DailyLogSummary::plus);
        }
        return List.copyOf(byDay.values());
    }

    private static double orZero(@Nullable Double value) {
        return value == null ? 0 : value;
    }
}
