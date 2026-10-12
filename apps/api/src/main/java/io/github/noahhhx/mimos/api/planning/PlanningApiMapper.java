package io.github.noahhhx.mimos.api.planning;

import io.github.noahhhx.mimos.api.recipes.RecipeApiMapper;
import io.github.noahhhx.mimos.planning.plan.PlannedDayTotal;

/** Maps planning domain records to the contract models (generated). */
public final class PlanningApiMapper {

    private PlanningApiMapper() {}

    public static org.openapitools.model.PlannedDayTotal toApiPlannedDay(PlannedDayTotal day) {
        return new org.openapitools.model.PlannedDayTotal()
                .date(day.date())
                .total(RecipeApiMapper.toApiNutrition(day.total()))
                .meals(day.meals())
                .mealsWithoutNutrition(day.mealsWithoutNutrition());
    }
}
