package io.github.noahhhx.mimos.planning.plan;

import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * One person's planned calories and macros for a day (ADR-0022): their
 * share of each meal they eat. {@code meals} counts every meal they eat
 * that day; {@code mealsWithoutNutrition} counts those whose recipe lacks
 * any value. A value the recipe lacks adds nothing to {@code total} and its
 * known values still count, as Log records them.
 */
public record PlannedDayTotal(LocalDate date, NutritionTotal total, int meals, int mealsWithoutNutrition) {

    static List<PlannedDayTotal> forWeek(
            LocalDate weekStart,
            UUID dinerProfileId,
            List<MealPlanRepository.PlannedMealInput> entries,
            Map<UUID, Nutrition> perServingByRecipe) {
        return IntStream.range(0, 7)
                .mapToObj(offset -> forDay(weekStart.plusDays(offset), dinerProfileId, entries, perServingByRecipe))
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
        double calories = 0;
        double proteinG = 0;
        double carbsG = 0;
        double fatG = 0;
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
            calories += scaled(perServing.calories(), share);
            proteinG += scaled(perServing.proteinG(), share);
            carbsG += scaled(perServing.carbsG(), share);
            fatG += scaled(perServing.fatG(), share);
        }
        NutritionTotal total = new NutritionTotal(tenth(calories), tenth(proteinG), tenth(carbsG), tenth(fatG));
        return new PlannedDayTotal(date, total, meals, mealsWithoutNutrition);
    }

    private static double scaled(@Nullable Double perServing, double share) {
        return perServing == null ? 0 : tenth(perServing * share);
    }

    private static double tenth(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
