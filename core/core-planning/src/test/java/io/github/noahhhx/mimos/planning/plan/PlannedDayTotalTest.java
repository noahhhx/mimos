package io.github.noahhhx.mimos.planning.plan;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.noahhhx.mimos.recipes.recipe.Nutrition;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlannedDayTotalTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final UUID ME = UUID.randomUUID();
    private static final UUID PARTNER = UUID.randomUUID();
    private static final UUID STEW = UUID.randomUUID();
    private static final UUID SOUP = UUID.randomUUID();
    private static final UUID SOUP_WITH_CALORIES = UUID.randomUUID();
    private static final UUID CRUMB = UUID.randomUUID();
    private static final UUID NO_PROTEIN = UUID.randomUUID();
    private static final UUID NO_CARBS = UUID.randomUUID();
    private static final UUID NO_FAT = UUID.randomUUID();
    private static final UUID BROTH = UUID.randomUUID();
    private static final Map<UUID, Nutrition> PER_SERVING = Map.of(
            STEW, new Nutrition(400.0, 20.0, 40.0, 10.0),
            SOUP, new Nutrition(null, 5.0, 12.0, 3.0),
            SOUP_WITH_CALORIES, new Nutrition(100.0 / 3, 0.1, 0.0, 1.0),
            CRUMB, new Nutrition(0.05, 0.05, 0.05, 0.05),
            NO_PROTEIN, new Nutrition(300.0, null, 30.0, 10.0),
            NO_CARBS, new Nutrition(200.0, 15.0, null, 5.0),
            NO_FAT, new Nutrition(100.0, 10.0, 20.0, null),
            BROTH, new Nutrition(null, 0.0, 0.0, 0.0));

    @Test
    void aMealEatenAloneCountsEveryServing() {
        PlannedDayTotal monday = monday(entry(MONDAY, STEW, 2, ME));

        assertThat(monday.total()).isEqualTo(new Nutrition(800.0, 40.0, 80.0, 20.0));
        assertThat(monday.meals()).isEqualTo(1);
        assertThat(monday.mealsWithoutNutrition()).isZero();
    }

    @Test
    void aSharedMealCountsTheServingsDividedByItsDiners() {
        PlannedDayTotal monday = monday(entry(MONDAY, STEW, 3, ME, PARTNER));

        assertThat(monday.total()).isEqualTo(new Nutrition(600.0, 30.0, 60.0, 15.0));
    }

    @Test
    void aMealOnlySomeoneElseEatsIsNotCounted() {
        PlannedDayTotal monday = monday(entry(MONDAY, STEW, 4, ME, PARTNER), entry(MONDAY, STEW, 1, PARTNER));

        assertThat(monday.total().calories()).isEqualTo(800.0);
        assertThat(monday.meals()).isEqualTo(1);
    }

    @Test
    void anUnknownValueAddsNothingWhileTheMealsKnownValuesCount() {
        PlannedDayTotal monday = monday(entry(MONDAY, STEW, 1, ME), entry(MONDAY, SOUP, 1, ME));

        assertThat(monday.total()).isEqualTo(new Nutrition(400.0, 25.0, 52.0, 13.0));
        assertThat(monday.meals()).isEqualTo(2);
        assertThat(monday.mealsWithoutNutrition()).isEqualTo(1);
    }

    @Test
    void aValueNoMealThatDayKnowsIsUnknownWhileAKnownZeroStaysZero() {
        PlannedDayTotal monday = monday(entry(MONDAY, BROTH, 2, ME));

        assertThat(monday.total()).isEqualTo(new Nutrition(null, 0.0, 0.0, 0.0));
        assertThat(monday.mealsWithoutNutrition()).isEqualTo(1);
    }

    @Test
    void aMealLackingAnyOneValueIsCountedAsWithoutNutrition() {
        PlannedDayTotal monday = monday(
                entry(MONDAY, SOUP, 1, ME),
                entry(MONDAY, NO_PROTEIN, 1, ME),
                entry(MONDAY, NO_CARBS, 1, ME),
                entry(MONDAY, NO_FAT, 1, ME));

        assertThat(monday.total()).isEqualTo(new Nutrition(600.0, 30.0, 62.0, 18.0));
        assertThat(monday.meals()).isEqualTo(4);
        assertThat(monday.mealsWithoutNutrition()).isEqualTo(4);
    }

    @Test
    void eachMealIsRoundedToATenthBeforeTheDayAddsThemUp() {
        PlannedDayTotal monday = monday(entry(MONDAY, CRUMB, 1, ME), entry(MONDAY, CRUMB, 1, ME));

        assertThat(monday.total()).isEqualTo(new Nutrition(0.2, 0.2, 0.2, 0.2));
    }

    @Test
    void aMealWhoseRecipeIsGoneIsCountedAsWithoutNutrition() {
        PlannedDayTotal monday = monday(entry(MONDAY, UUID.randomUUID(), 1, ME));

        assertThat(monday.total()).isEqualTo(Nutrition.UNKNOWN);
        assertThat(monday.mealsWithoutNutrition()).isEqualTo(1);
    }

    @Test
    void aMealEatenAloneScalesEachValueAsItsLogWould() {
        assertThat(PlannedDayTotal.shareOf(1.25, 1)).isEqualTo(1.25);
        PlannedDayTotal monday = monday(entry(MONDAY, SOUP_WITH_CALORIES, 1.25, ME));

        assertThat(monday.total()).isEqualTo(new Nutrition(41.7, 0.1, 0.0, 1.3));
    }

    @Test
    void theShareOfASharedMealIsRoundedToATenthOfAServingAndNeverBelowIt() {
        assertThat(PlannedDayTotal.shareOf(4, 2)).isEqualTo(2.0);
        assertThat(PlannedDayTotal.shareOf(3, 2)).isEqualTo(1.5);
        assertThat(PlannedDayTotal.shareOf(2, 3)).isEqualTo(0.7);
        assertThat(PlannedDayTotal.shareOf(1.5, 2)).isEqualTo(0.8);
        assertThat(PlannedDayTotal.shareOf(10, 3)).isEqualTo(3.3);
        assertThat(PlannedDayTotal.shareOf(2.5, 2)).isEqualTo(1.3);
        assertThat(PlannedDayTotal.shareOf(0.5, 6)).isEqualTo(0.1);
        assertThat(PlannedDayTotal.shareOf(0.5, 100)).isEqualTo(0.1);
        assertThat(monday(entry(MONDAY, STEW, 1, ME, PARTNER, UUID.randomUUID()))
                        .total()
                        .calories())
                .isEqualTo(120.0);
    }

    @Test
    void aWeekIsAlwaysSevenDaysFromMonday() {
        List<PlannedDayTotal> week =
                PlannedDayTotal.forWeek(MONDAY, ME, List.of(entry(MONDAY.plusDays(6), STEW, 1, ME)), PER_SERVING);

        assertThat(week)
                .extracting(PlannedDayTotal::date)
                .containsExactly(
                        MONDAY,
                        MONDAY.plusDays(1),
                        MONDAY.plusDays(2),
                        MONDAY.plusDays(3),
                        MONDAY.plusDays(4),
                        MONDAY.plusDays(5),
                        MONDAY.plusDays(6));
        assertThat(week.getFirst()).isEqualTo(new PlannedDayTotal(MONDAY, Nutrition.UNKNOWN, 0, 0));
        assertThat(week.getLast().total().calories()).isEqualTo(400.0);
    }

    private static PlannedDayTotal monday(MealPlanRepository.PlannedMealInput... entries) {
        return PlannedDayTotal.forWeek(MONDAY, ME, List.of(entries), PER_SERVING)
                .getFirst();
    }

    private static MealPlanRepository.PlannedMealInput entry(
            LocalDate date, UUID recipeId, double servings, UUID... diners) {
        return new MealPlanRepository.PlannedMealInput(
                UUID.randomUUID(), date, MealType.DINNER, recipeId, servings, Set.of(diners));
    }
}
