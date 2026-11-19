import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { MealType, NutritionBasis, PlannedFood } from "@mimos/api-client";

import { chipLabels, defaultDinerCount, defaultServings, toggleDiner } from "../src/lib/diners.ts";
import { amountText, newFoodEntry, parseFoodAmount } from "../src/lib/planned-item.ts";
import { withoutNutritionNote } from "../src/lib/planned-totals.ts";

type Sized = { mealType: MealType; members: number; expected: number };

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/plan.json", import.meta.url), "utf8"),
) as {
  defaultServingsCases: Sized[];
  defaultDinerCountCases: Sized[];
  toggleDinerCases: { diners: string[]; member: string; expected: string[] | null }[];
  chipLabelCases: { names: string[]; expected: string[] }[];
  amountTextCases: { recipeTitle?: string; food?: PlannedFood; servings: number; expected: string }[];
  newFoodEntryCases: {
    basis: NutritionBasis;
    servingSize?: number;
    mealType: MealType;
    members: number;
    expected: { food: { catalogSlug: string; unit?: string }; servings: number };
  }[];
  parseFoodAmountCases: { text: string; expected: number | null }[];
  withoutNutritionNoteCases: { mealsWithoutNutrition: number; expected: string | null }[];
};

describe("the shared plan cases", () => {
  it("start a new meal at the web's servings and diners", () => {
    for (const each of cases.defaultServingsCases) {
      assert.equal(defaultServings(each.mealType, each.members), each.expected, JSON.stringify(each));
    }
    for (const each of cases.defaultDinerCountCases) {
      assert.equal(defaultDinerCount(each.mealType, each.members), each.expected, JSON.stringify(each));
    }
  });

  it("toggle diners, never leaving a meal with nobody", () => {
    for (const each of cases.toggleDinerCases) {
      assert.deepEqual(toggleDiner(each.diners, each.member), each.expected, JSON.stringify(each));
    }
  });

  it("label each member's chip", () => {
    for (const each of cases.chipLabelCases) {
      assert.deepEqual(chipLabels(each.names), each.expected);
    }
  });

  it("say how much is planned", () => {
    for (const each of cases.amountTextCases) {
      const entry = each.food
        ? { food: each.food, servings: each.servings }
        : { recipeId: "r1", recipeTitle: each.recipeTitle, servings: each.servings };
      assert.equal(amountText(entry), each.expected);
    }
  });

  it("plan a new food at one portion per default diner", () => {
    for (const each of cases.newFoodEntryCases) {
      assert.deepEqual(
        newFoodEntry({ slug: "x", basis: each.basis, servingSize: each.servingSize }, each.mealType, each.members),
        each.expected,
        JSON.stringify(each),
      );
    }
  });

  it("take a typed food amount the API accepts", () => {
    for (const each of cases.parseFoodAmountCases) {
      assert.equal(parseFoodAmount(each.text), each.expected ?? undefined, JSON.stringify(each.text));
    }
  });

  it("note meals whose nutrition is unknown", () => {
    for (const each of cases.withoutNutritionNoteCases) {
      assert.equal(withoutNutritionNote(each), each.expected);
    }
  });
});
