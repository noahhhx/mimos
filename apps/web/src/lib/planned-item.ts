import type {
  CatalogIngredient,
  FoodProduct,
  IngredientInput,
  MealLogInput,
  MealPlanEntry,
  MealPlanEntryInput,
  MealType,
  PlannedFood,
} from "@mimos/api-client";

import { defaultDinerCount } from "./diners.ts";
import { formatServings } from "./format.ts";
import {
  EMPTY_NUTRITION,
  defaultAmount,
  logBody,
  productName,
  unitLabel,
  type Measured,
  type UnitSymbol,
} from "./log-entry.ts";

/**
 * What a plan entry plans (ADR-0026): a recipe, counted in servings, or a
 * food (an ingredient), counted by amount in its unit. The API sends exactly
 * one of `recipeId` and `food`; everything on the plan page that reads an
 * entry goes through this module so it handles both.
 */
export type PlannedWhat =
  | { kind: "recipe"; recipeId: string; title: string }
  | { kind: "food"; food: PlannedFood };

type EntryItem = Pick<MealPlanEntry, "recipeId" | "recipeTitle" | "food">;

/** The most the API takes as a food's amount (`MAX_FOOD_QUANTITY`). */
export const MAX_FOOD_AMOUNT = 10_000;

const MAX_NAME_LENGTH = 100;

export function plannedWhat(entry: EntryItem): PlannedWhat {
  if (entry.food) {
    return { kind: "food", food: entry.food };
  }
  if (entry.recipeId === undefined || entry.recipeTitle === undefined) {
    throw new Error("A planned meal names a recipe or a food");
  }
  return { kind: "recipe", recipeId: entry.recipeId, title: entry.recipeTitle };
}

/** A planned meal's name: the recipe's title or the food's name. */
export function entryName(entry: EntryItem): string {
  const what = plannedWhat(entry);
  return what.kind === "food" ? what.food.name : what.title;
}

/**
 * A planned meal in a few words: a recipe's title, or a food with its
 * amount, like "Banana, 1" (a count of pieces has no unit) or "Bagel, 85 g".
 */
export function entryLabel(entry: EntryItem & Pick<MealPlanEntry, "servings">): string {
  const what = plannedWhat(entry);
  if (what.kind === "recipe") {
    return what.title;
  }
  const unit = unitOf(what.food);
  return `${what.food.name}, ${entry.servings}${unit === "" ? "" : ` ${unit}`}`;
}

/** An amount planned, in words, like "2 servings", "85 g", or "1 piece". */
export function amountText(entry: EntryItem & Pick<MealPlanEntry, "servings">): string {
  const what = plannedWhat(entry);
  return what.kind === "recipe"
    ? formatServings(entry.servings)
    : `${entry.servings} ${unitLabel(unitOf(what.food), entry.servings)}`;
}

/** The unit a food's amount is counted in; "" counts pieces. */
export function unitOf(food: Pick<PlannedFood, "unit">): UnitSymbol {
  return (food.unit ?? "") as UnitSymbol;
}

/** A typed amount of a food, if the API takes it: more than 0, at most 10,000. */
export function parseFoodAmount(text: string): number | undefined {
  if (text.trim() === "") {
    return undefined;
  }
  const amount = Number(text);
  return Number.isFinite(amount) && amount > 0 && amount <= MAX_FOOD_AMOUNT ? amount : undefined;
}

/**
 * The food a new entry plans and how much of it: what the Log page would
 * start at (one serving, else 100 g, 100 ml, or 1 piece) for each of the
 * meal's default diners. A household of one plans one portion, not the two
 * servings a recipe starts at.
 */
export function newFoodEntry(
  food: Pick<Measured, "basis" | "servingSize"> & { slug: string },
  mealType: MealType,
  memberCount: number,
): Pick<MealPlanEntryInput, "food" | "servings"> {
  const { quantity, unit } = defaultAmount(food);
  const servings = Math.round(Number(quantity) * defaultDinerCount(mealType, memberCount) * 1000) / 1000;
  return { food: { catalogSlug: food.slug, ...(unit === "" ? {} : { unit }) }, servings };
}

/**
 * The ingredient a scanned product becomes, when Open Food Facts gave all
 * four values; a product missing one needs the person to fill it in first.
 */
export function productIngredient(product: FoodProduct): IngredientInput | undefined {
  const { calories, proteinG, carbsG, fatG } = product.nutrition;
  if (calories == null || proteinG == null || carbsG == null || fatG == null) {
    return undefined;
  }
  return {
    name: productName(product).slice(0, MAX_NAME_LENGTH),
    basis: product.basis,
    nutrition: { calories, proteinG, carbsG, fatG },
  };
}

/** The household's own ingredient that already is `input`, so scanning one product twice adds it once. */
export function existingIngredient(input: IngredientInput, ingredients: CatalogIngredient[]): CatalogIngredient | undefined {
  const same = (a: IngredientInput["nutrition"], b: IngredientInput["nutrition"]) =>
    a.calories === b.calories && a.proteinG === b.proteinG && a.carbsG === b.carbsG && a.fatG === b.fatG;
  return ingredients.find(
    (entry) =>
      !entry.isShared && entry.name === input.name && entry.basis === input.basis && same(entry.nutrition, input.nutrition),
  );
}

/**
 * The ad-hoc log of `amount` of a planned food, with the nutrition the
 * ingredient's entry in the loaded catalog gives it; nothing when the
 * ingredient is not in the catalog or the unit does not fit it.
 */
export function foodLogBody(
  entry: Pick<MealPlanEntry, "date" | "mealType" | "food">,
  ingredients: CatalogIngredient[],
  amount: number,
): MealLogInput | undefined {
  const found = ingredients.find((each) => each.slug === entry.food?.catalogSlug);
  if (!found || !entry.food) {
    return undefined;
  }
  return logBody(
    { kind: "ingredient", entry: found },
    entry.date,
    entry.mealType,
    "1",
    EMPTY_NUTRITION,
    { quantity: String(amount), unit: unitOf(entry.food) },
  );
}
