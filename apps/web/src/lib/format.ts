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

/** Whether a unit means the whole of a linked recipe, shown as "2x" with no unit (ADR-0018). */
function isWholeRecipeUnit(unit: string | null | undefined): boolean {
  const normalized = unit?.trim().toLowerCase();
  return normalized === "recipe" || normalized === "recipes";
}

/**
 * "2 cups flour" style quantity formatting (best effort, no smarts). An
 * unmeasured ingredient ("salt, to taste") has no quantity: it formats as
 * its unit alone, or as nothing.
 */
export function formatQuantity(quantity: number | null | undefined, unit: string | null | undefined): string {
  const wholeRecipe = isWholeRecipeUnit(unit);
  if (quantity == null) {
    return wholeRecipe ? "" : (unit ?? "");
  }
  const rounded = roundQuantity(quantity);
  const number = Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(2).replace(/0$/, "");
  if (wholeRecipe) {
    return `${number}x`;
  }
  if (!unit) {
    return number;
  }
  // A line that is one of your recipes counts in servings of it (ADR-0018): "1 serving", "2 servings".
  return `${number} ${rounded === 1 && unit.trim().toLowerCase() === "servings" ? "serving" : unit}`;
}

function roundQuantity(quantity: number): number {
  return Math.round(quantity * 100) / 100;
}

/** A parenthetical that names what a piece is, so it takes the plural: "Rye bread (slices)". */
const COUNTED_IN = new Set(["slice", "rasher", "piece", "sheet", "strip", "wedge"]);

const IRREGULAR_PLURALS: Record<string, string> = {
  half: "halves",
  knife: "knives",
  leaf: "leaves",
  loaf: "loaves",
  potato: "potatoes",
  tomato: "tomatoes",
};

function pluralWord(word: string): string {
  const irregular = IRREGULAR_PLURALS[word.toLowerCase()];
  if (irregular) {
    return word[0] + irregular.slice(1);
  }
  if (/s$/i.test(word)) {
    return word;
  }
  if (/(ch|sh|x|z)$/i.test(word)) {
    return `${word}es`;
  }
  if (/[^aeiou]y$/i.test(word)) {
    return `${word.slice(0, -1)}ies`;
  }
  return `${word}s`;
}

/**
 * An ingredient's name as it reads after its amount: a count of pieces (no
 * unit) other than one takes the plural, "2 Eggs" but "1 Egg". The plural
 * goes on the head noun, before any qualifier: "Chicken thighs, bone-in",
 * "Corns on the cob", "Rye bread (slices)". A name that already ends in s is
 * left as it is.
 */
export function countedName(name: string, quantity: number | null | undefined, unit: string | null | undefined): string {
  if (unit?.trim() || quantity == null || roundQuantity(quantity) === 1) {
    return name;
  }
  const counted = /\((\w+)\)/.exec(name);
  if (counted && COUNTED_IN.has(counted[1].toLowerCase())) {
    return name.replace(counted[0], `(${pluralWord(counted[1])})`);
  }
  const qualifier = /,|\s\(|\s(?:of|on|in|with)\s/i.exec(name);
  const end = qualifier ? qualifier.index : name.length;
  const head = name.slice(0, end).replace(/(\S+)(\s*)$/, (_, word: string, space: string) => pluralWord(word) + space);
  return head + name.slice(end);
}

/** "1 serving", "2 servings", "1.5 servings". */
export function formatServings(servings: number): string {
  return `${servings} ${servings === 1 ? "serving" : "servings"}`;
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

/** The Log table's calories, protein, carbs, and fat cells; an unknown value is a dash. */
export function nutritionCells(
  nutrition: { calories?: number; proteinG?: number; carbsG?: number; fatG?: number } | undefined,
): string[] {
  const grams = (value: number | undefined) => (value != null ? `${Math.round(value)} g` : "–");
  return [
    formatKcal(nutrition?.calories),
    grams(nutrition?.proteinG),
    grams(nutrition?.carbsG),
    grams(nutrition?.fatG),
  ];
}
