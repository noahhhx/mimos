import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { plannedTotalParts, showsPlannedTotals, withoutNutritionNote } from "../src/lib/planned-totals.ts";

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

describe("plannedTotalParts", () => {
  it("names calories and each macro, rounded to whole numbers", () => {
    assert.deepEqual(plannedTotalParts({ calories: 1199.6, proteinG: 60.4, carbsG: 120, fatG: 29.5 }), [
      "1200 kcal",
      "60 g protein",
      "120 g carbs",
      "30 g fat",
    ]);
  });

  it("shows known zeros as zeros", () => {
    assert.deepEqual(plannedTotalParts({ calories: 0, proteinG: 0, carbsG: 0, fatG: 0 }), [
      "0 kcal",
      "0 g protein",
      "0 g carbs",
      "0 g fat",
    ]);
  });

  it("leaves out a value no meal that day knows, never showing it as zero", () => {
    assert.deepEqual(plannedTotalParts({ proteinG: 15, carbsG: 0, fatG: 6 }), ["15 g protein", "0 g carbs", "6 g fat"]);
  });

  it("shows nothing when no value is known", () => {
    assert.deepEqual(plannedTotalParts({}), []);
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
