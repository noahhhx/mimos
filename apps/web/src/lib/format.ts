/** Shared formatting and date helpers for the app UI. */

export const MEAL_TYPES = ["BREAKFAST", "LUNCH", "DINNER", "SNACK"] as const;

export type MealTypeValue = (typeof MEAL_TYPES)[number];

export function mealLabel(mealType: string): string {
  const labels: Record<string, string> = {
    BREAKFAST: "Breakfast",
    LUNCH: "Lunch",
    DINNER: "Dinner",
    SNACK: "Snack",
  };
  return labels[mealType] ?? mealType;
}

/** The Monday of the week containing `date`. */
export function mondayOf(date: Date): string {
  const day = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const weekday = day.getUTCDay(); // 0 = Sunday
  const diff = weekday === 0 ? -6 : 1 - weekday;
  day.setUTCDate(day.getUTCDate() + diff);
  return day.toISOString().slice(0, 10);
}

export function addDays(isoDate: string, days: number): string {
  const day = new Date(`${isoDate}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + days);
  return day.toISOString().slice(0, 10);
}

export function weekDays(startIso: string): string[] {
  return Array.from({ length: 7 }, (_, i) => addDays(startIso, i));
}

export function dayLabel(isoDate: string): string {
  return new Date(`${isoDate}T00:00:00Z`).toLocaleDateString(undefined, {
    weekday: "short",
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  });
}

/** "Sunday": an ISO date's weekday, in the user's locale. */
export function weekdayName(isoDate: string): string {
  return new Date(`${isoDate}T00:00:00Z`).toLocaleDateString(undefined, { weekday: "long", timeZone: "UTC" });
}

/** "Mon 28": an ISO date's short weekday and day of the month. */
export function shortDayLabel(isoDate: string): string {
  const day = new Date(`${isoDate}T00:00:00Z`);
  return `${day.toLocaleDateString(undefined, { weekday: "short", timeZone: "UTC" })} ${day.getUTCDate()}`;
}

/**
 * The local calendar date of `now` as ISO (not `toISOString()`, which is
 * the UTC date: wrong in the evening west of UTC and the early morning east
 * of it).
 */
export function todayIso(now: Date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * "2 cups flour" style quantity formatting (best effort, no smarts). An
 * unmeasured ingredient ("salt, to taste") has no quantity: it formats as
 * its unit alone, or as nothing.
 */
export function formatQuantity(quantity: number | null | undefined, unit: string | null | undefined): string {
  if (quantity == null) {
    return unit ?? "";
  }
  const rounded = Math.round(quantity * 100) / 100;
  const number = Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(2).replace(/0$/, "");
  return unit ? `${number} ${unit}` : number;
}

/** Whether any per-serving value is known (absent values are unknown, not zero). */
export function hasNutrition<T extends { calories?: number; proteinG?: number; carbsG?: number; fatG?: number }>(
  nutrition: T | null | undefined,
): nutrition is T {
  return (
    nutrition != null &&
    [nutrition.calories, nutrition.proteinG, nutrition.carbsG, nutrition.fatG].some((value) => value != null)
  );
}

export function formatKcal(value: number | null | undefined): string {
  return value != null ? `${Math.round(value)} kcal` : "–";
}
