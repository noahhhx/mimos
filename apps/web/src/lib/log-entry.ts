import type {
  CatalogIngredient,
  MealLogInput,
  MealType,
  Nutrition,
  NutritionBasis,
  RecipeSummary,
} from "@mimos/api-client";

import { describeEntry, searchByName } from "./catalog-match.ts";
import { hasNutrition } from "./format.ts";

/**
 * What the log page's "Log something else" form logs: a recipe picked from
 * the search, counted in servings of it; an ingredient picked from it,
 * counted by amount; or anything else, described and counted by hand.
 */
export type LogWhat =
  | { kind: "custom"; description: string }
  | { kind: "recipe"; recipe: RecipeSummary }
  | { kind: "ingredient"; entry: CatalogIngredient };

/** What the What search offers: everything that is picked rather than typed. */
export type WhatOption = Exclude<LogWhat, { kind: "custom" }>;

/** Food whose nutrition is known per basis (per 100 g, per 100 ml, or each), so any amount of it can be counted. */
export type Measured = { name: string; basis: NutritionBasis; nutrition: Nutrition };

/** The units an amount is counted in (ADR-0015); "" counts pieces. */
export type UnitSymbol = "g" | "kg" | "ml" | "l" | "tsp" | "tbsp" | "";

/** The nutrition fields of a custom log, as typed. */
export type NutritionFields = { calories: string; proteinG: string; carbsG: string; fatG: string };

/** The amount of measured food, as typed, and the unit chosen for it. */
export type AmountFields = { quantity: string; unit: UnitSymbol };

export const EMPTY_WHAT: LogWhat = { kind: "custom", description: "" };

export const EMPTY_NUTRITION: NutritionFields = { calories: "", proteinG: "", carbsG: "", fatG: "" };

/**
 * Each unit's basis and its size in that basis's base unit (grams,
 * millilitres, or pieces), as `MetricUnit` sizes them for recipes. A
 * basis's first unit is the one its amount starts in.
 */
const UNITS: Record<UnitSymbol, { basis: NutritionBasis; size: number }> = {
  g: { basis: "PER_100_G", size: 1 },
  kg: { basis: "PER_100_G", size: 1000 },
  ml: { basis: "PER_100_ML", size: 1 },
  l: { basis: "PER_100_ML", size: 1000 },
  tsp: { basis: "PER_100_ML", size: 5 },
  tbsp: { basis: "PER_100_ML", size: 15 },
  "": { basis: "PER_PIECE", size: 1 },
};

/** How many base units each basis gives nutrition for. */
const BASIS_AMOUNT: Record<NutritionBasis, number> = { PER_100_G: 100, PER_100_ML: 100, PER_PIECE: 1 };

const MAX_SERVINGS = 100;

const MAX_DESCRIPTION_LENGTH = 200;

/** The What field's text: a picked recipe shows its title, a picked ingredient its name. */
export function whatText(what: LogWhat): string {
  switch (what.kind) {
    case "recipe":
      return what.recipe.title;
    case "ingredient":
      return what.entry.name;
    case "custom":
      return what.description;
  }
}

/** Typing into What: the text becomes a custom log, so changing a picked recipe's title unpicks it. */
export function typeWhat(text: string): LogWhat {
  return { kind: "custom", description: text };
}

/**
 * Recipes and ingredients with a word in their name that starts with what
 * was typed, ranked together, so "banana" offers the banana before banana
 * bread.
 */
export function searchWhat(
  query: string,
  recipes: RecipeSummary[],
  ingredients: CatalogIngredient[],
  limit = 8,
): WhatOption[] {
  const options: WhatOption[] = [
    ...recipes.map((recipe) => ({ kind: "recipe" as const, recipe })),
    ...ingredients.map((entry) => ({ kind: "ingredient" as const, entry })),
  ];
  return searchByName(query, options, whatText, limit);
}

/** An ingredient as a search option's second line, like "Ingredient · 387 kcal per 100 g". */
export function describeIngredient(entry: CatalogIngredient): string {
  return `${entry.isShared ? "Ingredient" : "Your ingredient"} · ${describeEntry(entry)}`;
}

/** The food `what` counts by amount, if it counts one. */
export function measuredOf(what: LogWhat): Measured | undefined {
  return what.kind === "ingredient" ? what.entry : undefined;
}

/** The units an amount of food on `basis` can be counted in. */
export function unitsFor(basis: NutritionBasis): UnitSymbol[] {
  return (Object.keys(UNITS) as UnitSymbol[]).filter((unit) => UNITS[unit].basis === basis);
}

/** Where a picked food's amount starts: 100 g, 100 ml, or 1 piece. */
export function defaultAmount(basis: NutritionBasis): AmountFields {
  return { quantity: String(BASIS_AMOUNT[basis]), unit: unitsFor(basis)[0] };
}

/** A unit as words, like "tbsp" or "pieces"; pieces are plural unless `quantity` is 1. */
export function unitLabel(unit: UnitSymbol, quantity = 2): string {
  if (unit !== "") {
    return unit;
  }
  return quantity === 1 ? "piece" : "pieces";
}

/** A typed number of servings, if it is one the API accepts: more than 0, at most 100. */
export function parseServings(text: string): number | undefined {
  const servings = parseAmount(text);
  return servings !== undefined && servings <= MAX_SERVINGS ? servings : undefined;
}

/** A typed amount, if it is more than 0. */
export function parseAmount(text: string): number | undefined {
  if (text.trim() === "") {
    return undefined;
  }
  const amount = Number(text);
  return Number.isFinite(amount) && amount > 0 ? amount : undefined;
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
  amount: AmountFields,
): MealLogInput | undefined {
  switch (what.kind) {
    case "recipe": {
      const parsed = parseServings(servings);
      return parsed === undefined ? undefined : { date, mealType, recipeId: what.recipe.id, servings: parsed };
    }
    case "ingredient":
      return measuredBody(what.entry, date, mealType, amount);
    case "custom":
      return customBody(what.description, date, mealType, nutrition);
  }
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
  return scaled(recipe.nutrition, servings);
}

/**
 * What `quantity` of `unit` of a food adds to the day, rounded to 0.1 like
 * a recipe's total; a value the food lacks stays absent. Nothing when the
 * food's nutrition is unknown or the unit does not fit its basis.
 */
export function measuredTotal(food: Measured, quantity: number, unit: UnitSymbol): Nutrition | undefined {
  const { basis, size } = UNITS[unit];
  if (basis !== food.basis || !hasNutrition(food.nutrition)) {
    return undefined;
  }
  return scaled(food.nutrition, (quantity * size) / BASIS_AMOUNT[basis]);
}

/** A log's total in words for the form, like "Comes to 675 kcal, 30 g protein, 80 g carbs, 20 g fat." */
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

/**
 * Measured food is logged as one serving of the amount, named with it, and
 * with the nutrition the form previewed, so the entry is what the person saw.
 */
function measuredBody(
  food: Measured,
  date: string,
  mealType: MealType,
  amount: AmountFields,
): MealLogInput | undefined {
  const quantity = parseAmount(amount.quantity);
  if (quantity === undefined || UNITS[amount.unit].basis !== food.basis) {
    return undefined;
  }
  const measure = `, ${quantity} ${unitLabel(amount.unit, quantity)}`;
  return {
    date,
    mealType,
    description: food.name.trim().slice(0, MAX_DESCRIPTION_LENGTH - measure.length) + measure,
    servings: 1,
    nutrition: measuredTotal(food, quantity, amount.unit) ?? {},
  };
}

function customBody(
  text: string,
  date: string,
  mealType: MealType,
  nutrition: NutritionFields,
): MealLogInput | undefined {
  const description = text.trim();
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

function scaled(nutrition: Nutrition, factor: number): Nutrition {
  const scale = (value: number | undefined) => (value == null ? undefined : Math.round(value * factor * 10) / 10);
  const { calories, proteinG, carbsG, fatG } = nutrition;
  return { calories: scale(calories), proteinG: scale(proteinG), carbsG: scale(carbsG), fatG: scale(fatG) };
}
