import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { formatQuantity, hasNutrition } from "../src/lib/format.ts";

describe("formatQuantity", () => {
  it("formats an amount with its unit", () => {
    assert.equal(formatQuantity(2, "cups"), "2 cups");
    assert.equal(formatQuantity(1.333, "tbsp"), "1.33 tbsp");
    assert.equal(formatQuantity(3, undefined), "3");
  });

  it("formats an unmeasured ingredient as its unit or nothing", () => {
    assert.equal(formatQuantity(undefined, "pinch"), "pinch");
    assert.equal(formatQuantity(undefined, undefined), "");
  });
});

describe("hasNutrition", () => {
  it("is true when any value is known, carbs or fat alone included", () => {
    assert.equal(hasNutrition({ carbsG: 40 }), true);
    assert.equal(hasNutrition({ fatG: 0 }), true);
    assert.equal(hasNutrition({}), false);
    assert.equal(hasNutrition(undefined), false);
  });
});
