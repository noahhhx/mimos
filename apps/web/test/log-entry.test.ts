import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { RecipeSummary } from "@mimos/api-client";

import {
  EMPTY_NUTRITION,
  describeTotal,
  logBody,
  parseServings,
  recipeTotal,
  typeWhat,
  whatText,
  type LogWhat,
} from "../src/lib/log-entry.ts";

const recipe = (patch: Partial<RecipeSummary> = {}): RecipeSummary => ({
  id: "r-dahl",
  title: "Red Lentil Dahl",
  description: "",
  servings: 4,
  isLibrary: true,
  tags: [],
  nutrition: { calories: 450, proteinG: 20, carbsG: 61, fatG: 12 },
  ...patch,
});

const DAHL: LogWhat = { kind: "recipe", recipe: recipe() };

describe("logBody", () => {
  it("logs a recipe by its id and servings, leaving nutrition to the API", () => {
    assert.deepEqual(
      logBody(DAHL, "2026-10-07", "LUNCH", "1.5", { calories: "999", proteinG: "1", carbsG: "", fatG: "" }),
      { date: "2026-10-07", mealType: "LUNCH", recipeId: "r-dahl", servings: 1.5 },
    );
  });

  it("logs anything else as one serving of what was typed, with the nutrition given", () => {
    assert.deepEqual(
      logBody(typeWhat("  Toast with peanut butter "), "2026-10-07", "SNACK", "3", {
        calories: "310",
        proteinG: "11.5",
        carbsG: "",
        fatG: " ",
      }),
      {
        date: "2026-10-07",
        mealType: "SNACK",
        description: "Toast with peanut butter",
        servings: 1,
        nutrition: { calories: 310, proteinG: 11.5, carbsG: undefined, fatG: undefined },
      },
    );
  });

  it("logs nothing without a description", () => {
    assert.equal(logBody(typeWhat(""), "2026-10-07", "SNACK", "1", EMPTY_NUTRITION), undefined);
    assert.equal(logBody(typeWhat("   "), "2026-10-07", "SNACK", "1", { ...EMPTY_NUTRITION, calories: "100" }), undefined);
  });

  it("logs no recipe without servings the API accepts", () => {
    for (const servings of ["0", "-1", "100.1", "abc", "", " ", "Infinity", "NaN"]) {
      assert.equal(logBody(DAHL, "2026-10-07", "DINNER", servings, EMPTY_NUTRITION), undefined, servings);
    }
    assert.equal(logBody(DAHL, "2026-10-07", "DINNER", "100", EMPTY_NUTRITION)?.servings, 100);
    assert.equal(logBody(DAHL, "2026-10-07", "DINNER", "0.1", EMPTY_NUTRITION)?.servings, 0.1);
  });
});

describe("parseServings", () => {
  it("reads more than 0, up to 100", () => {
    assert.equal(parseServings("2"), 2);
    assert.equal(parseServings("0.5"), 0.5);
    assert.equal(parseServings("0"), undefined);
    assert.equal(parseServings("101"), undefined);
  });
});

describe("typing and picking", () => {
  it("shows a picked recipe's title", () => {
    assert.equal(whatText(DAHL), "Red Lentil Dahl");
  });

  it("unpicks the recipe when its title is typed over, keeping the text as typed", () => {
    assert.deepEqual(typeWhat("Red Lentil Dah"), { kind: "custom", description: "Red Lentil Dah" });
    // A trailing space must survive, or no second word can be typed.
    assert.equal(whatText(typeWhat("Red Lentil ")), "Red Lentil ");
  });
});

describe("recipeTotal and describeTotal", () => {
  it("scales a serving by the servings logged, to 0.1 as the API stores it", () => {
    assert.deepEqual(recipeTotal(recipe(), 1.5), { calories: 675, proteinG: 30, carbsG: 91.5, fatG: 18 });
    assert.deepEqual(recipeTotal(recipe(), 0.3), { calories: 135, proteinG: 6, carbsG: 18.3, fatG: 3.6 });
  });

  it("says what the servings come to, in whole numbers", () => {
    assert.equal(describeTotal(recipeTotal(recipe(), 1.5)), "Comes to 675 kcal, 30 g protein, 92 g carbs, 18 g fat.");
    assert.equal(
      describeTotal(recipeTotal(recipe({ nutrition: { calories: 333.33 } }), 1)),
      "Comes to 333 kcal.",
    );
  });

  it("knows nothing about a recipe whose nutrition is unknown", () => {
    assert.equal(recipeTotal(recipe({ nutrition: {} }), 2), undefined);
    assert.equal(describeTotal(undefined), "Nutrition unknown, so it adds nothing.");
  });
});
