import type { PlannedDayTotal } from "@mimos/api-client";

import type { PlanView } from "./diners.ts";

export function showsPlannedTotals(shared: boolean, view: PlanView): boolean {
  return !shared || view === "mine";
}

export function withoutNutritionNote(day: Pick<PlannedDayTotal, "mealsWithoutNutrition">): string | null {
  const count = day.mealsWithoutNutrition;
  if (count === 0) {
    return null;
  }
  return `Unknown nutrition in ${count} ${count === 1 ? "meal" : "meals"} is not counted.`;
}
