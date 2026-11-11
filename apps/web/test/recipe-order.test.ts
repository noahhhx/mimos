import assert from "node:assert/strict";
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

function recipe(overrides: Partial<RecipeSummary> & Pick<RecipeSummary, "id" | "title">): RecipeSummary {
  return {
    description: "",
    servings: 1,
    isLibrary: false,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    tags: [],
    nutrition: {},
    ...overrides,
  };
}

describe("parseRecipeSort", () => {
  it("returns the value when it is a known sort", () => {
    assert.equal(parseRecipeSort("newest"), "newest");
    assert.equal(parseRecipeSort("oldest"), "oldest");
    assert.equal(parseRecipeSort("edited"), "edited");
    assert.equal(parseRecipeSort("title"), "title");
  });

  it("falls back to the default for unknown or missing values", () => {
    assert.equal(parseRecipeSort(null), DEFAULT_RECIPE_SORT);
    assert.equal(parseRecipeSort(""), DEFAULT_RECIPE_SORT);
    assert.equal(parseRecipeSort("recent"), DEFAULT_RECIPE_SORT);
  });
});

describe("orderRecipes", () => {
  const apple = recipe({ id: "1", title: "Apple tart", tags: ["dessert", "fruit"], createdAt: "2025-01-02T00:00:00Z" });
  const banana = recipe({ id: "2", title: "banana bread", tags: ["dessert"], createdAt: "2025-01-03T00:00:00Z" });
  const carrot = recipe({ id: "3", title: "Carrot soup", tags: ["savoury"], createdAt: "2025-01-01T00:00:00Z" });

  it("sorts alphabetically by title regardless of input order and case", () => {
    assert.deepEqual(
      orderRecipes([banana, carrot, apple], "title", new Set()).map((r) => r.title),
      ["Apple tart", "banana bread", "Carrot soup"],
    );
  });

  it("sorts newest first, then alphabetically", () => {
    assert.deepEqual(
      orderRecipes([apple, carrot, banana], "newest", new Set()).map((r) => r.title),
      ["banana bread", "Apple tart", "Carrot soup"],
    );
  });

  it("sorts oldest first, then alphabetically", () => {
    assert.deepEqual(
      orderRecipes([banana, apple, carrot], "oldest", new Set()).map((r) => r.title),
      ["Carrot soup", "Apple tart", "banana bread"],
    );
  });

  it("sorts by recently edited, then alphabetically", () => {
    const a = recipe({ id: "a", title: "A", updatedAt: "2025-01-01T00:00:00Z", createdAt: "2025-01-01T00:00:00Z" });
    const b = recipe({ id: "b", title: "B", updatedAt: "2025-01-03T00:00:00Z", createdAt: "2025-01-02T00:00:00Z" });
    const c = recipe({ id: "c", title: "C", updatedAt: "2025-01-02T00:00:00Z", createdAt: "2025-01-03T00:00:00Z" });
    assert.deepEqual(
      orderRecipes([a, b, c], "edited", new Set()).map((r) => r.title),
      ["B", "C", "A"],
    );
  });

  it("filters recipes that have every selected tag", () => {
    assert.deepEqual(
      orderRecipes([apple, banana, carrot], "title", new Set(["dessert"])).map((r) => r.title),
      ["Apple tart", "banana bread"],
    );
    assert.deepEqual(
      orderRecipes([apple, banana, carrot], "title", new Set(["dessert", "fruit"])).map((r) => r.title),
      ["Apple tart"],
    );
    assert.deepEqual(orderRecipes([apple, banana, carrot], "title", new Set(["missing"])), []);
  });

  it("passes all recipes when no tags are selected", () => {
    assert.deepEqual(
      orderRecipes([banana, apple, carrot], "title", new Set()).map((r) => r.title),
      ["Apple tart", "banana bread", "Carrot soup"],
    );
  });

  it("returns a new array and does not mutate the input", () => {
    const input = [apple, banana];
    const output = orderRecipes(input, "title", new Set());
    assert.notEqual(output, input);
    assert.deepEqual(input.map((r) => r.title), ["Apple tart", "banana bread"]);
  });
});

describe("recipeTags", () => {
  it("returns distinct tags sorted case-insensitively", () => {
    const recipes = [
      recipe({ id: "1", title: "A", tags: ["Dinner", "quick"] }),
      recipe({ id: "2", title: "B", tags: ["dinner", "vegetarian"] }),
    ];
    assert.deepEqual(recipeTags(recipes), ["Dinner", "dinner", "quick", "vegetarian"]);
  });

  it("returns an empty array when there are no tags", () => {
    assert.deepEqual(recipeTags([recipe({ id: "1", title: "A" })]), []);
  });
});

describe("RECIPE_SORTS", () => {
  it("has a label for every sort", () => {
    const sorts = Object.keys(RECIPE_SORTS) as RecipeSort[];
    for (const sort of sorts) {
      assert.equal(typeof RECIPE_SORTS[sort].label, "string");
    }
  });
});
