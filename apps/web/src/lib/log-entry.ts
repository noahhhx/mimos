import type { MealLogInput, MealType, Nutrition, RecipeSummary } from "@mimos/api-client";

import { hasNutrition } from "./format.ts";

/**
 * What the log page's "Log something else" form logs: a recipe picked from
 * the search, counted in servings of it, or anything else, described and
 * counted by hand.
 */
export type LogWhat = { kind: "custom"; description: string } | { kind: "recipe"; recipe: RecipeSummary };

/** The nutrition fields of a custom log, as typed. */
export type NutritionFields = { calories: string; proteinG: string; carbsG: string; fatG: string };

export const EMPTY_WHAT: LogWhat = { kind: "custom", description: "" };

export const EMPTY_NUTRITION: NutritionFields = { calories: "", proteinG: "", carbsG: "", fatG: "" };

const MAX_SERVINGS = 100;

/** The What field's text: a picked recipe shows its title. */
export function whatText(what: LogWhat): string {
  return what.kind === "recipe" ? what.recipe.title : what.description;
}

/** Typing into What: the text becomes a custom log, so changing a picked recipe's title unpicks it. */
export function typeWhat(text: string): LogWhat {
  return { kind: "custom", description: text };
}

/** A typed number of servings, if it is one the API accepts: more than 0, at most 100. */
export function parseServings(text: string): number | undefined {
  if (text.trim() === "") {
    return undefined;
  }
  const servings = Number(text);
  return Number.isFinite(servings) && servings > 0 && servings <= MAX_SERVINGS ? servings : undefined;
}

/**
 * The request that logs `what`, or nothing while it cannot be logged. A
 * recipe sends only itself and servings: the API counts its nutrition.
 */
export function logBody(
  what: LogWhat,
  date: string,
  mealType: MealType,
  servings: string,
  nutrition: NutritionFields,
): MealLogInput | undefined {
  if (what.kind === "recipe") {
    const parsed = parseServings(servings);
    return parsed === undefined ? undefined : { date, mealType, recipeId: what.recipe.id, servings: parsed };
  }
  const description = what.description.trim();
  if (description === "") {
    return undefined;
  }
  const numberOrUndefined = (value: string) => (value.trim() === "" ? undefined : Number(value));
  return {
    date,
    mealType,
    description,
    servings: 1,
    nutrition: {
      calories: numberOrUndefined(nutrition.calories),
      proteinG: numberOrUndefined(nutrition.proteinG),
      carbsG: numberOrUndefined(nutrition.carbsG),
      fatG: numberOrUndefined(nutrition.fatG),
    },
  };
}

/**
 * What `servings` of a recipe add to the day, rounded to 0.1 as the API
 * rounds a logged recipe, so the preview matches the entry it becomes;
 * nothing when the recipe's nutrition is unknown.
 */
export function recipeTotal(recipe: RecipeSummary, servings: number): Nutrition | undefined {
  if (!hasNutrition(recipe.nutrition)) {
    return undefined;
  }
  const scale = (perServing: number | undefined) =>
    perServing == null ? undefined : Math.round(perServing * servings * 10) / 10;
  const { calories, proteinG, carbsG, fatG } = recipe.nutrition;
  return { calories: scale(calories), proteinG: scale(proteinG), carbsG: scale(carbsG), fatG: scale(fatG) };
}

/** A recipe log's total in words for the form, like "Comes to 675 kcal, 30 g protein, 80 g carbs, 20 g fat." */
export function describeTotal(total: Nutrition | undefined): string {
  if (total === undefined) {
    return "Nutrition unknown, so it adds nothing.";
  }
  const parts = [
    total.calories != null ? `${Math.round(total.calories)} kcal` : undefined,
    total.proteinG != null ? `${Math.round(total.proteinG)} g protein` : undefined,
    total.carbsG != null ? `${Math.round(total.carbsG)} g carbs` : undefined,
    total.fatG != null ? `${Math.round(total.fatG)} g fat` : undefined,
  ].filter((part) => part !== undefined);
  return `Comes to ${parts.join(", ")}.`;
}
