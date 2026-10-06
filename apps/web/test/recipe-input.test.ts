import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { formValuesOf, toEstimateInput, toRecipeInput, unitChoices, type RecipeFormValues } from "../src/lib/recipe-input.ts";

function values(patch: Partial<RecipeFormValues> = {}): RecipeFormValues {
  return {
    ...formValuesOf(),
    title: "Soup",
    description: "Warm.",
    servings: "2",
    ingredients: [{ quantity: "1", unit: "l", name: "stock" }],
    steps: ["Simmer."],
    ...patch,
  };
}

function errorsOf(patch: Partial<RecipeFormValues>): string[] {
  const result = toRecipeInput(values(patch));
  assert.ok("errors" in result, "expected errors");
  return result.errors;
}

describe("toRecipeInput", () => {
  it("leaves a blank amount out: an unmeasured ingredient", () => {
    const result = toRecipeInput(
      values({ ingredients: [{ quantity: "", unit: "", name: " salt, to taste " }, { quantity: "1", unit: "l", name: "stock" }] }),
    );
    assert.ok("input" in result);
    assert.deepEqual(result.input.ingredients, [
      { quantity: undefined, unit: undefined, name: "salt, to taste" },
      { quantity: 1, unit: "l", name: "stock" },
    ]);
    assert.equal(JSON.stringify(result.input.ingredients[0]), '{"name":"salt, to taste"}');
  });

  it("skips untouched rows but names a row with no name", () => {
    const result = toRecipeInput(
      values({
        ingredients: [
          { quantity: "1", unit: "l", name: "stock" },
          { quantity: "", unit: "", name: "" },
        ],
        steps: ["Simmer.", "  "],
      }),
    );
    assert.ok("input" in result);
    assert.equal(result.input.ingredients.length, 1);
    assert.deepEqual(result.input.steps, [{ instruction: "Simmer." }]);

    assert.deepEqual(errorsOf({ ingredients: [{ quantity: "2", unit: "cups", name: "" }] }), [
      "Ingredient 1 needs a name.",
    ]);
  });

  it("rejects a zero or negative amount, naming the row", () => {
    assert.deepEqual(
      errorsOf({
        ingredients: [
          { quantity: "1", unit: "l", name: "stock" },
          { quantity: "0", unit: "", name: "salt" },
        ],
      }),
      ['Ingredient 2\'s amount must be more than 0, or leave it blank for "to taste".'],
    );
  });

  it("asks for a title, a description, an ingredient, and a step", () => {
    assert.deepEqual(errorsOf({ title: "  ", description: "", ingredients: [{ quantity: "", unit: "", name: "" }], steps: [""] }), [
      "Give the recipe a title.",
      "Give the recipe a description.",
      "Add at least one ingredient.",
      "Add at least one step.",
    ]);
  });

  it("lowercases tags and keeps each once", () => {
    const result = toRecipeInput(values({ tags: "Dinner, dinner , soup,," }));
    assert.ok("input" in result);
    assert.deepEqual(result.input.tags, ["dinner", "soup"]);
  });

  it("round-trips a saved recipe's unmeasured ingredient as a blank amount", () => {
    const form = formValuesOf({
      id: "00000000-0000-0000-0000-000000000000",
      title: "Soup",
      description: "Warm.",
      servings: 2,
      isLibrary: false,
      tags: [],
      nutrition: {},
      nutritionSource: "MANUAL",
      ingredients: [{ name: "salt" }],
      steps: [{ instruction: "Simmer." }],
    });
    assert.deepEqual(form.ingredients, [{ quantity: "", unit: "", name: "salt" }]);
  });

  it("round-trips calculated nutrition and each line's note and catalog link", () => {
    const form = formValuesOf({
      id: "00000000-0000-0000-0000-000000000000",
      title: "Soup",
      description: "Warm.",
      servings: 2,
      isLibrary: false,
      tags: [],
      nutrition: { calories: 240 },
      nutritionSource: "INGREDIENTS",
      ingredients: [{ quantity: 250, unit: "g", name: "red lentils", note: "rinsed", catalogSlug: "red-lentils" }, { name: "salt" }],
      steps: [{ instruction: "Simmer." }],
    });
    const result = toRecipeInput(form);
    assert.ok("input" in result);
    assert.equal(result.input.nutritionSource, "INGREDIENTS");
    assert.equal(
      JSON.stringify(result.input.ingredients),
      '[{"quantity":250,"unit":"g","name":"red lentils","note":"rinsed","catalogSlug":"red-lentils"},{"name":"salt"}]',
    );
  });

  it("round-trips a line that is one of the user's recipes, in servings", () => {
    const form = formValuesOf({
      id: "00000000-0000-0000-0000-000000000000",
      title: "Focaccia sandwich",
      description: "Lunch.",
      servings: 2,
      isLibrary: false,
      tags: [],
      nutrition: { calories: 300 },
      nutritionSource: "INGREDIENTS",
      ingredients: [{ quantity: 2, unit: "Servings", name: "focaccia", recipeId: "11111111-1111-1111-1111-111111111111" }],
      steps: [{ instruction: "Fill it." }],
    });
    assert.deepEqual(form.ingredients, [
      { quantity: "2", unit: "servings", name: "focaccia", recipeId: "11111111-1111-1111-1111-111111111111" },
    ]);
    const result = toRecipeInput(form);
    assert.ok("input" in result);
    assert.equal(
      JSON.stringify(result.input.ingredients),
      '[{"quantity":2,"unit":"servings","name":"focaccia","recipeId":"11111111-1111-1111-1111-111111111111"}]',
    );
  });

  it("never sends both links for a line", () => {
    const result = toRecipeInput(
      values({ ingredients: [{ quantity: "1", unit: "servings", name: "focaccia", catalogSlug: "bread", recipeId: "r-1" }] }),
    );
    assert.ok("input" in result);
    assert.deepEqual(result.input.ingredients, [{ quantity: 1, unit: "servings", name: "focaccia", recipeId: "r-1" }]);
  });
});

describe("toEstimateInput", () => {
  it("sends the named, validly measured rows and remembers where each came from", () => {
    const estimate = toEstimateInput(
      values({
        servings: "4",
        ingredients: [
          { quantity: "", unit: "", name: "" },
          { quantity: "250", unit: " g ", name: "red lentils", catalogSlug: "red-lentils" },
          { quantity: "-1", unit: "", name: "carrots" },
          { quantity: "", unit: "", name: "salt", catalogSlug: "" },
          { quantity: "2", unit: "servings", name: "focaccia", recipeId: "r-1" },
        ],
      }),
    );
    assert.ok(estimate);
    assert.deepEqual(estimate.rows, [1, 3, 4]);
    assert.equal(
      JSON.stringify(estimate.input),
      '{"servings":4,"ingredients":[{"quantity":250,"unit":"g","name":"red lentils","catalogSlug":"red-lentils"},' +
        '{"name":"salt"},{"quantity":2,"unit":"servings","name":"focaccia","recipeId":"r-1"}]}',
    );
  });

  it("waits for a valid number of servings", () => {
    assert.equal(toEstimateInput(values({ servings: "" })), undefined);
    assert.equal(toEstimateInput(values({ servings: "2.5" })), undefined);
  });
});

describe("unit choices", () => {
  it("offers the metric units and a count, and keeps an older recipe's own unit", () => {
    assert.deepEqual(unitChoices("g"), ["", "g", "kg", "ml", "l", "tsp", "tbsp"]);
    assert.deepEqual(unitChoices("cups"), ["", "g", "kg", "ml", "l", "tsp", "tbsp", "cups"]);
  });

  it("offers servings only for a row that is one of the user's recipes, or is already in servings", () => {
    assert.deepEqual(unitChoices("", true), ["", "g", "kg", "ml", "l", "tsp", "tbsp", "servings"]);
    assert.deepEqual(unitChoices("servings"), ["", "g", "kg", "ml", "l", "tsp", "tbsp", "servings"]);
    assert.equal(unitChoices("g").includes("servings"), false);
  });

  it("reads a loosely written metric unit as that unit", () => {
    const form = formValuesOf({
      id: "00000000-0000-0000-0000-000000000000",
      title: "Soup",
      description: "Warm.",
      servings: 2,
      isLibrary: false,
      tags: [],
      nutrition: {},
      nutritionSource: "MANUAL",
      ingredients: [{ quantity: 2, unit: " TBSP", name: "oil" }, { quantity: 2, unit: "cups", name: "stock" }],
      steps: [{ instruction: "Simmer." }],
    });
    assert.deepEqual(
      form.ingredients.map((row) => row.unit),
      ["tbsp", "cups"],
    );
  });
});
