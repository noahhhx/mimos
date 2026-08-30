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

export function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/** "2 cups flour" style quantity formatting (best effort, no smarts). */
export function formatQuantity(quantity: number, unit: string | null | undefined): string {
  const rounded = Math.round(quantity * 100) / 100;
  const number = Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(2).replace(/0$/, "");
  return unit ? `${number} ${unit}` : number;
}

export function formatKcal(value: number | null | undefined): string {
  return value != null ? `${Math.round(value)} kcal` : "—";
}
