import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { hasNutrition } from "../src/lib/format.ts";

describe("hasNutrition", () => {
  it("is true when any value is known, carbs or fat alone included", () => {
    assert.equal(hasNutrition({ carbsG: 40 }), true);
    assert.equal(hasNutrition({ fatG: 0 }), true);
    assert.equal(hasNutrition({}), false);
    assert.equal(hasNutrition(undefined), false);
  });
});
