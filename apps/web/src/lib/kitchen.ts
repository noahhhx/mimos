import type {
  MealPlanEntry,
  MealType,
  PlanSuggestion,
  PlanSuggestionEntry,
  RecipeDetail,
  ShoppingListItem,
} from "@mimos/api-client";

import { eatsIt } from "./diners.ts";
import { weekDays } from "./format.ts";

/**
 * The Kitchen home's choices (docs/design/index.md, "Kitchen home"), as pure
 * functions of the week's data and an injected time, so they can be tested
 * without a browser. Its meals are the caller's: in a shared household,
 * a meal planned only for someone else is never "tonight" (ADR-0019).
 */

/** "Good morning." 05:00–11:59, "Good afternoon." 12:00–17:59, "Good evening." otherwise, by local hour. */
export function greeting(now: Date): string {
  const hour = now.getHours();
  if (hour >= 5 && hour < 12) {
    return "Good morning.";
  }
  if (hour >= 12 && hour < 18) {
    return "Good afternoon.";
  }
  return "Good evening.";
}

/** Today's meal for the "Tonight" panel, and the label it goes under. */
export interface TonightPick {
  entry: MealPlanEntry;
  label: "Tonight" | "Today";
}

/** When today has no dinner, the meal shown under "Today": the latest main meal first, a snack last. */
const TODAY_FALLBACK: MealType[] = ["LUNCH", "BREAKFAST", "SNACK"];

/**
 * Today's first dinner the caller eats; failing that, today's other meal
 * they eat (lunch, then breakfast, then a snack) under "Today"; `null`
 * when they have nothing planned.
 */
export function pickTonight(entries: MealPlanEntry[], today: string): TonightPick | null {
  const todays = entries.filter((entry) => entry.date === today && eatsIt(entry));
  const dinner = todays.find((entry) => entry.mealType === "DINNER");
  if (dinner) {
    return { entry: dinner, label: "Tonight" };
  }
  for (const mealType of TODAY_FALLBACK) {
    const meal = todays.find((entry) => entry.mealType === mealType);
    if (meal) {
      return { entry: meal, label: "Today" };
    }
  }
  return null;
}

/** Monday to Sunday, each day with its first dinner the caller eats, or `null`. */
export function weekDinners(
  entries: MealPlanEntry[],
  weekStart: string,
): { date: string; dinner: MealPlanEntry | null }[] {
  return weekDays(weekStart).map((date) => ({
    date,
    dinner: entries.find((entry) => entry.date === date && entry.mealType === "DINNER" && eatsIt(entry)) ?? null,
  }));
}

/** A plugin's card and the one proposed dinner the home page offers from it. */
export interface Thought {
  suggestion: PlanSuggestion;
  entry: PlanSuggestionEntry;
}

/**
 * The first card that fills an open dinner from today on: its first dinner
 * entry on a day with no dinner planned. Past days are not offered. Open
 * means open for the household, since a suggested dinner is for everyone.
 */
export function pickThought(suggestions: PlanSuggestion[], entries: MealPlanEntry[], today: string): Thought | null {
  const planned = new Set(entries.filter((entry) => entry.mealType === "DINNER").map((entry) => entry.date));
  for (const suggestion of suggestions) {
    const entry = suggestion.entries.find(
      (candidate) => candidate.mealType === "DINNER" && candidate.date >= today && !planned.has(candidate.date),
    );
    if (entry) {
      return { suggestion, entry };
    }
  }
  return null;
}

/** "Still to buy": up to `limit` rows, unchecked items first, checked ones filling what's left. */
export function stillToBuy(
  items: ShoppingListItem[],
  limit = 5,
): { shown: ShoppingListItem[]; remaining: number; total: number } {
  const unchecked = items.filter((item) => !item.checked);
  const checked = items.filter((item) => item.checked);
  return { shown: [...unchecked, ...checked].slice(0, limit), remaining: unchecked.length, total: items.length };
}

/** Prep plus cook time, whichever is known; `null` when neither is. */
export function totalMinutes(recipe: Pick<RecipeDetail, "prepMinutes" | "cookMinutes">): number | null {
  if (recipe.prepMinutes == null && recipe.cookMinutes == null) {
    return null;
  }
  return (recipe.prepMinutes ?? 0) + (recipe.cookMinutes ?? 0);
}
