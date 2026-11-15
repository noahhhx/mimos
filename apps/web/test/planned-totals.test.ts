import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { showsPlannedTotals, withoutNutritionNote } from "../src/lib/planned-totals.ts";

describe("showsPlannedTotals", () => {
  it("always shows them to a household of one", () => {
    assert.equal(showsPlannedTotals(false, "mine"), true);
    assert.equal(showsPlannedTotals(false, "everyone"), true);
  });

  it("shows them in a shared household's Mine view only", () => {
    assert.equal(showsPlannedTotals(true, "mine"), true);
    assert.equal(showsPlannedTotals(true, "everyone"), false);
  });
});

describe("withoutNutritionNote", () => {
  it("says nothing when every meal is fully known", () => {
    assert.equal(withoutNutritionNote({ mealsWithoutNutrition: 0 }), null);
  });

  it("says how many meals have unknown values, without claiming the whole meal is left out", () => {
    assert.equal(withoutNutritionNote({ mealsWithoutNutrition: 1 }), "Unknown nutrition in 1 meal is not counted.");
    assert.equal(withoutNutritionNote({ mealsWithoutNutrition: 3 }), "Unknown nutrition in 3 meals is not counted.");
  });
});
