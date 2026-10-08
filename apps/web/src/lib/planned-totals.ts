import type { PlannedDayTotal } from "@mimos/api-client";

import type { PlanView } from "./diners.ts";
import { formatKcal } from "./format.ts";

export function showsPlannedTotals(shared: boolean, view: PlanView): boolean {
  return !shared || view === "mine";
}

export function plannedTotalParts(day: Pick<PlannedDayTotal, "total" | "meals" | "mealsWithoutNutrition">): string[] {
  const { total } = day;
  const nothingKnown =
    day.mealsWithoutNutrition === day.meals &&
    total.calories === 0 &&
    total.proteinG === 0 &&
    total.carbsG === 0 &&
    total.fatG === 0;
  if (nothingKnown) {
    return [];
  }
  const grams = (value: number, name: string) => `${Math.round(value)} g ${name}`;
  return [formatKcal(total.calories), grams(total.proteinG, "protein"), grams(total.carbsG, "carbs"), grams(total.fatG, "fat")];
}

export function withoutNutritionNote(day: Pick<PlannedDayTotal, "mealsWithoutNutrition">): string | null {
  const count = day.mealsWithoutNutrition;
  if (count === 0) {
    return null;
  }
  return `Unknown nutrition in ${count} ${count === 1 ? "meal" : "meals"} is not counted.`;
}
