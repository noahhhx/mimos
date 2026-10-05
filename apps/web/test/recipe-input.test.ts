import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { formValuesOf, toRecipeInput, type RecipeFormValues } from "../src/lib/recipe-input.ts";

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
      ingredients: [{ name: "salt" }],
      steps: [{ instruction: "Simmer." }],
    });
    assert.deepEqual(form.ingredients, [{ quantity: "", unit: "", name: "salt" }]);
  });
});
