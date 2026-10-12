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
 * that day; {@code mealsWithoutNutrition} counts those whose recipe lacks
 * any value. Each value in {@code total} sums the meals that know it, as
 * Log records them, and is unknown when none does.
 */
public record PlannedDayTotal(LocalDate date, Nutrition total, int meals, int mealsWithoutNutrition) {

    static List<PlannedDayTotal> forRange(
            LocalDate from,
            LocalDate to,
            UUID dinerProfileId,
            List<MealPlanRepository.PlannedMealInput> entries,
            Map<UUID, Nutrition> perServingByRecipe) {
        return from.datesUntil(to.plusDays(1))
                .map(date -> forDay(date, dinerProfileId, entries, perServingByRecipe))
                .toList();
    }

    static double shareOf(double servings, int diners) {
        return diners == 1 ? servings : Math.max(0.1, Math.round(servings / diners * 10) / 10.0);
    }

    private static PlannedDayTotal forDay(
            LocalDate date,
            UUID dinerProfileId,
            List<MealPlanRepository.PlannedMealInput> entries,
            Map<UUID, Nutrition> perServingByRecipe) {
        Nutrition total = Nutrition.UNKNOWN;
        int meals = 0;
        int mealsWithoutNutrition = 0;
        for (MealPlanRepository.PlannedMealInput entry : entries) {
            if (!entry.date().equals(date) || !entry.dinerProfileIds().contains(dinerProfileId)) {
                continue;
            }
            meals++;
            Nutrition perServing = perServingByRecipe.getOrDefault(entry.recipeId(), Nutrition.UNKNOWN);
            if (perServing.calories() == null
                    || perServing.proteinG() == null
                    || perServing.carbsG() == null
                    || perServing.fatG() == null) {
                mealsWithoutNutrition++;
            }
            double share = shareOf(entry.servings(), entry.dinerProfileIds().size());
            total = new Nutrition(
                    plus(total.calories(), perServing.calories(), share),
                    plus(total.proteinG(), perServing.proteinG(), share),
                    plus(total.carbsG(), perServing.carbsG(), share),
                    plus(total.fatG(), perServing.fatG(), share));
        }
        return new PlannedDayTotal(date, total, meals, mealsWithoutNutrition);
    }

    private static @Nullable Double plus(@Nullable Double sum, @Nullable Double perServing, double share) {
        if (perServing == null) {
            return sum;
        }
        double meal = tenth(perServing * share);
        return sum == null ? meal : tenth(sum + meal);
    }

    private static double tenth(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
