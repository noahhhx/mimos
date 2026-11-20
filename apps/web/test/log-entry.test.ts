import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type {
  CatalogIngredient,
  FoodProduct,
  MealLogInput,
  MealType,
  Nutrition,
  NutritionBasis,
  RecipeSummary,
} from "@mimos/api-client";

import {
  EMPTY_NUTRITION,
  defaultAmount,
  describeIngredient,
  describeTotal,
  logBody,
  measuredOf,
  measuredTotal,
  parseServings,
  recipeTotal,
  searchWhat,
  servingHint,
  typeWhat,
  unitsFor,
  whatText,
  type AmountFields,
  type LogWhat,
  type NutritionFields,
  type UnitSymbol,
} from "../src/lib/log-entry.ts";
import { nutritionCells } from "../src/lib/format.ts";

type WhatSketch = { custom?: string; recipe?: string; ingredient?: string; product?: string };

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/log-entry.json", import.meta.url), "utf8"),
) as {
  date: string;
  recipes: Record<string, { id: string; title: string; nutrition: Nutrition }>;
  ingredients: Record<string, CatalogIngredient>;
  products: Record<string, FoodProduct>;
  logBodyCases: {
    about: string;
    what: WhatSketch;
    mealType?: MealType;
    servings?: string;
    nutrition?: NutritionFields;
    amount?: AmountFields;
    expected?: MealLogInput;
    logsNothing?: boolean;
    description?: string;
  }[];
  servingsCases: { text: string; expected: number | null }[];
  recipeTotalCases: { about: string; recipe: string; servings: number; total: Nutrition | null; text: string }[];
  measuredTotalCases: { ingredient: string; quantity: number; unit: UnitSymbol; total: Nutrition | null; text?: string }[];
  unitsFor: Record<NutritionBasis, UnitSymbol[]>;
  measuredCases: { about: string; what: WhatSketch; name: string; basis: NutritionBasis; amount: AmountFields; hint?: string }[];
  whatTextCases: { about: string; what: WhatSketch; text: string }[];
  searchCases: { about: string; query: string; recipes: string[]; ingredients: string[]; expected: string[] }[];
  searchLimit: { query: string; count: number; limit: number };
  describeIngredientCases: { ingredient: string; text: string }[];
  nutritionCellsCases: { nutrition: Nutrition | null; cells: string[] }[];
};

const recipe = (key: string): RecipeSummary => ({
  description: "",
  servings: 4,
  isLibrary: true,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
  tags: [],
  ...cases.recipes[key],
});

const what = (sketch: WhatSketch): LogWhat => {
  if (sketch.recipe !== undefined) return { kind: "recipe", recipe: recipe(sketch.recipe) };
  if (sketch.ingredient !== undefined) return { kind: "ingredient", entry: cases.ingredients[sketch.ingredient] };
  if (sketch.product !== undefined) return { kind: "product", product: cases.products[sketch.product] };
  return typeWhat(sketch.custom ?? "");
};

/** As the API is sent it: absent values left out. */
const wire = (body: MealLogInput | undefined) => (body === undefined ? null : JSON.parse(JSON.stringify(body)));

const optionKey = (option: LogWhat) =>
  option.kind === "recipe" ? `recipe:${option.recipe.id}` : option.kind === "ingredient" ? `ingredient:${option.entry.slug}` : "";

describe("logBody", () => {
  for (const each of cases.logBodyCases) {
    it(each.about, () => {
      const body = logBody(
        what(each.what),
        cases.date,
        each.mealType ?? "SNACK",
        each.servings ?? "1",
        each.nutrition ?? EMPTY_NUTRITION,
        each.amount ?? { quantity: "1", unit: "g" },
      );
      if (each.logsNothing) {
        assert.equal(body, undefined);
      }
      if (each.expected !== undefined) {
        assert.deepEqual(wire(body), each.expected);
      }
      if (each.description !== undefined) {
        assert.equal(body?.description, each.description);
      }
    });
  }
});

describe("parseServings", () => {
  it("reads more than 0, up to 100", () => {
    for (const each of cases.servingsCases) {
      assert.equal(parseServings(each.text), each.expected ?? undefined, each.text);
    }
  });
});

describe("typing and picking", () => {
  for (const each of cases.whatTextCases) {
    it(each.about, () => assert.equal(whatText(what(each.what)), each.text));
  }

  it("unpicks a recipe or a product when its name is typed over", () => {
    assert.deepEqual(typeWhat("Red Lentil Dah"), { kind: "custom", description: "Red Lentil Dah" });
  });
});

describe("recipeTotal and describeTotal", () => {
  for (const each of cases.recipeTotalCases) {
    it(each.about, () => {
      const total = recipeTotal(recipe(each.recipe), each.servings);
      assert.deepEqual(wire(total as MealLogInput | undefined), each.total);
      assert.equal(describeTotal(total), each.text);
    });
  }
});

describe("measuredTotal", () => {
  it("counts each unit by its size against the basis, to 0.1, leaving unknown values absent", () => {
    for (const each of cases.measuredTotalCases) {
      const total = measuredTotal(cases.ingredients[each.ingredient], each.quantity, each.unit);
      const label = `${each.quantity} ${each.unit} of ${each.ingredient}`;
      assert.deepEqual(wire(total as MealLogInput | undefined), each.total, label);
      if (each.text !== undefined) {
        assert.equal(describeTotal(total), each.text, label);
      }
    }
  });
});

describe("units and amounts", () => {
  it("offers only the units of the food's basis", () => {
    for (const [basis, units] of Object.entries(cases.unitsFor)) {
      assert.deepEqual(unitsFor(basis as NutritionBasis), units, basis);
    }
  });

  for (const each of cases.measuredCases) {
    it(each.about, () => {
      const food = measuredOf(what(each.what))!;
      assert.equal(food.name, each.name);
      assert.equal(whatText(what(each.what)), each.name);
      assert.equal(food.basis, each.basis);
      assert.deepEqual(defaultAmount(food), each.amount);
      assert.equal(servingHint(food), each.hint);
    });
  }
});

describe("searchWhat", () => {
  for (const each of cases.searchCases) {
    it(each.about, () => {
      const options = searchWhat(
        each.query,
        each.recipes.map(recipe),
        each.ingredients.map((key) => cases.ingredients[key]),
      );
      assert.deepEqual(options.map(optionKey), each.expected);
    });
  }

  it("offers at most the limit", () => {
    const { query, count, limit } = cases.searchLimit;
    const many = Array.from({ length: count }, (_, index) => ({
      ...cases.ingredients.sugar,
      slug: `s${index}`,
      name: `${query} ${index}`,
    }));
    assert.equal(searchWhat(query, [], many).length, limit);
  });

  it("says what an ingredient option is, and whose", () => {
    for (const each of cases.describeIngredientCases) {
      assert.equal(describeIngredient(cases.ingredients[each.ingredient]), each.text);
    }
  });
});

describe("nutritionCells", () => {
  it("rounds the week table's values, a dash for each unknown", () => {
    for (const each of cases.nutritionCellsCases) {
      assert.deepEqual(nutritionCells(each.nutrition ?? undefined), each.cells);
    }
  });
});
