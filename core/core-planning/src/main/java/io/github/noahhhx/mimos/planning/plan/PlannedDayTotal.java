package io.github.noahhhx.mimos.planning.plan;

import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One person's planned calories and macros for a day (ADR-0022): their
 * share of each meal they eat. {@code meals} counts every meal they eat
 * that day; {@code mealsWithoutNutrition} counts those whose recipe or
 * ingredient lacks any value. Each value in {@code total} sums the meals
 * that know it, as Log records them, and is unknown when none does.
 */
public record PlannedDayTotal(LocalDate date, Nutrition total, int meals, int mealsWithoutNutrition) {

    static List<PlannedDayTotal> forRange(
            LocalDate from,
            LocalDate to,
            UUID dinerProfileId,
            List<MealPlanRepository.PlannedMealInput> entries,
            Map<PlannedItem, Nutrition> perUnitByItem) {
        return from.datesUntil(to.plusDays(1))
                .map(date -> forDay(date, dinerProfileId, entries, perUnitByItem))
                .toList();
    }

    static double shareOf(double servings, int diners) {
        return diners == 1 ? servings : Math.max(0.1, Math.round(servings / diners * 10) / 10.0);
    }

    private static PlannedDayTotal forDay(
            LocalDate date,
            UUID dinerProfileId,
            List<MealPlanRepository.PlannedMealInput> entries,
            Map<PlannedItem, Nutrition> perUnitByItem) {
        Nutrition total = Nutrition.UNKNOWN;
        int meals = 0;
        int mealsWithoutNutrition = 0;
        for (MealPlanRepository.PlannedMealInput entry : entries) {
            if (!entry.date().equals(date) || !entry.dinerProfileIds().contains(dinerProfileId)) {
                continue;
            }
            meals++;
            Nutrition perUnit = perUnitByItem.getOrDefault(entry.item(), Nutrition.UNKNOWN);
            if (perUnit.calories() == null
                    || perUnit.proteinG() == null
                    || perUnit.carbsG() == null
                    || perUnit.fatG() == null) {
                mealsWithoutNutrition++;
            }
            double share = shareOf(entry.amount(), entry.dinerProfileIds().size());
            total = new Nutrition(
                    plus(total.calories(), perUnit.calories(), share),
                    plus(total.proteinG(), perUnit.proteinG(), share),
                    plus(total.carbsG(), perUnit.carbsG(), share),
                    plus(total.fatG(), perUnit.fatG(), share));
        }
        return new PlannedDayTotal(date, total, meals, mealsWithoutNutrition);
    }

    private static @Nullable Double plus(@Nullable Double sum, @Nullable Double perUnit, double share) {
        if (perUnit == null) {
            return sum;
        }
        double meal = tenth(perUnit * share);
        return sum == null ? meal : tenth(sum + meal);
    }

    private static double tenth(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
