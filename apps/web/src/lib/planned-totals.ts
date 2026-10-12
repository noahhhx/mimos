import type { Nutrition, PlannedDayTotal } from "@mimos/api-client";

import type { PlanView } from "./diners.ts";
import { formatKcal } from "./format.ts";

export function showsPlannedTotals(shared: boolean, view: PlanView): boolean {
  return !shared || view === "mine";
}

export function plannedTotalParts(total: Nutrition): string[] {
  const grams = (name: string) => (value: number) => `${Math.round(value)} g ${name}`;
  const parts: [number | undefined, (value: number) => string][] = [
    [total.calories, formatKcal],
    [total.proteinG, grams("protein")],
    [total.carbsG, grams("carbs")],
    [total.fatG, grams("fat")],
  ];
  return parts.flatMap(([value, format]) => (value == null ? [] : [format(value)]));
}

export function withoutNutritionNote(day: Pick<PlannedDayTotal, "mealsWithoutNutrition">): string | null {
  const count = day.mealsWithoutNutrition;
  if (count === 0) {
    return null;
  }
  return `Unknown nutrition in ${count} ${count === 1 ? "meal" : "meals"} is not counted.`;
}
