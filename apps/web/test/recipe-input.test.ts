import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { RecipeDetail } from "@mimos/api-client";

import { formValuesOf, toEstimateInput, toRecipeInput, unitChoices, type RecipeFormValues } from "../src/lib/recipe-input.ts";

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/recipe-input.json", import.meta.url), "utf8"),
) as {
  base: RecipeFormValues;
  inputCases: { name: string; patch: Partial<RecipeFormValues>; expected: { input: Record<string, unknown> } | { errors: string[] } }[];
  draftCases: { name: string; recipe: RecipeDetail | null; expected: RecipeFormValues; input?: Record<string, unknown> }[];
  estimateCases: { name: string; patch: Partial<RecipeFormValues>; expected: { input: unknown; rows: number[] } | null }[];
  unitChoiceCases: { current: string; isRecipe: boolean; expected: string[] }[];
};

/** What the API is sent: undefined fields are left out, as `JSON.stringify` leaves them. */
const wire = (value: unknown) => JSON.parse(JSON.stringify(value)) as Record<string, unknown>;

/** Each field the case names, as sent; fields it does not name are another case's business. */
function assertFields(actual: unknown, expected: Record<string, unknown>) {
  const sent = wire(actual);
  for (const [field, value] of Object.entries(expected)) {
    assert.deepEqual(sent[field], value, field);
  }
}

describe("toRecipeInput", () => {
  for (const { name, patch, expected } of cases.inputCases) {
    it(name, () => {
      const result = toRecipeInput({ ...cases.base, ...patch });
      if ("errors" in expected) {
        assert.deepEqual("errors" in result ? result.errors : [], expected.errors);
      } else {
        assert.ok("input" in result, `unexpected errors: ${"errors" in result ? result.errors.join(" ") : ""}`);
        assertFields(result.input, expected.input);
      }
    });
  }
});

describe("formValuesOf", () => {
  for (const { name, recipe, expected, input } of cases.draftCases) {
    it(name, () => {
      const form = formValuesOf(recipe ?? undefined);
      assert.deepEqual(form, expected);
      if (input) {
        const result = toRecipeInput(form);
        assert.ok("input" in result);
        assertFields(result.input, input);
      }
    });
  }
});

describe("toEstimateInput", () => {
  for (const { name, patch, expected } of cases.estimateCases) {
    it(name, () => {
      const estimate = toEstimateInput({ ...cases.base, ...patch });
      assert.deepEqual(estimate === undefined ? null : wire(estimate), expected);
    });
  }
});

describe("unitChoices", () => {
  for (const { current, isRecipe, expected } of cases.unitChoiceCases) {
    it(`offers ${JSON.stringify(expected)} for ${JSON.stringify(current)}${isRecipe ? " on a recipe row" : ""}`, () => {
      assert.deepEqual(unitChoices(current, isRecipe), expected);
    });
  }
});
