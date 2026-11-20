import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { RecipeSummary } from "@mimos/api-client";

import {
  DEFAULT_RECIPE_SORT,
  RECIPE_SORTS,
  orderRecipes,
  parseRecipeSort,
  recipeTags,
  type RecipeSort,
} from "../src/lib/recipe-order.ts";

type Sketch = Partial<RecipeSummary> & Pick<RecipeSummary, "id" | "title">;

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/recipe-order.json", import.meta.url), "utf8"),
) as {
  orderCases: { name: string; recipes: Sketch[]; sort: RecipeSort; tags: string[]; expected: string[] }[];
  tagCases: { name: string; recipes: Sketch[]; expected: string[] }[];
  sortKeys: { key: string | null; expected: RecipeSort }[];
};

function recipe(sketch: Sketch): RecipeSummary {
  return {
    description: "",
    servings: 1,
    isLibrary: false,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    tags: [],
    nutrition: {},
    ...sketch,
  };
}

describe("parseRecipeSort", () => {
  for (const { key, expected } of cases.sortKeys) {
    it(`reads ${JSON.stringify(key)} as ${expected}`, () => {
      assert.equal(parseRecipeSort(key), expected);
    });
  }

  it("falls back to the default", () => {
    assert.equal(DEFAULT_RECIPE_SORT, "title");
  });
});

describe("orderRecipes", () => {
  for (const { name, recipes, sort, tags, expected } of cases.orderCases) {
    it(name, () => {
      const ordered = orderRecipes(recipes.map(recipe), sort, new Set(tags));
      assert.deepEqual(
        ordered.map((r) => r.id),
        expected,
      );
    });
  }

  it("returns a new array and does not mutate the input", () => {
    const input = [recipe({ id: "2", title: "banana bread" }), recipe({ id: "1", title: "Apple tart" })];
    const output = orderRecipes(input, "title", new Set());
    assert.notEqual(output, input);
    assert.deepEqual(
      input.map((r) => r.id),
      ["2", "1"],
    );
  });
});

describe("recipeTags", () => {
  for (const { name, recipes, expected } of cases.tagCases) {
    it(name, () => {
      assert.deepEqual(recipeTags(recipes.map(recipe)), expected);
    });
  }
});

describe("RECIPE_SORTS", () => {
  it("has a label for every sort", () => {
    const sorts = Object.keys(RECIPE_SORTS) as RecipeSort[];
    for (const sort of sorts) {
      assert.equal(typeof RECIPE_SORTS[sort].label, "string");
    }
  });
});
