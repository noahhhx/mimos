import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import {
  countedName,
  formatQuantity,
  formatServings,
  hasNutrition,
  nutritionCells,
  todayIso,
} from "../src/lib/format.ts";

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const text = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/recipe-text.json", import.meta.url), "utf8"),
) as {
  quantityCases: { quantity: number | null; unit: string | null; expected: string }[];
  nameCases: { name: string; quantity: number | null; unit: string | null; expected: string }[];
  servingsCases: { servings: number; expected: string }[];
};

describe("formatQuantity", () => {
  for (const { quantity, unit, expected } of text.quantityCases) {
    it(`formats ${quantity} ${JSON.stringify(unit)} as ${JSON.stringify(expected)}`, () => {
      assert.equal(formatQuantity(quantity, unit), expected);
    });
  }

  it("formats an unmeasured ingredient as its unit or nothing", () => {
    assert.equal(formatQuantity(undefined, "pinch"), "pinch");
    assert.equal(formatQuantity(undefined, undefined), "");
  });
});

describe("countedName", () => {
  for (const { name, quantity, unit, expected } of text.nameCases) {
    it(`reads ${JSON.stringify(name)}, ${quantity} ${JSON.stringify(unit)}, as ${JSON.stringify(expected)}`, () => {
      assert.equal(countedName(name, quantity, unit), expected);
    });
  }
});

describe("hasNutrition", () => {
  it("is true when any value is known, carbs or fat alone included", () => {
    assert.equal(hasNutrition({ carbsG: 40 }), true);
    assert.equal(hasNutrition({ fatG: 0 }), true);
    assert.equal(hasNutrition({}), false);
    assert.equal(hasNutrition(undefined), false);
  });
});

describe("todayIso", () => {
  const original = process.env.TZ;
  const inZone = (zone: string, run: () => void) => {
    process.env.TZ = zone;
    try {
      run();
    } finally {
      if (original === undefined) {
        delete process.env.TZ;
      } else {
        process.env.TZ = original;
      }
    }
  };

  it("is the local date in the evening west of UTC, where the UTC date is already tomorrow", () => {
    inZone("America/Los_Angeles", () => {
      assert.equal(todayIso(new Date("2026-10-03T06:30:00Z")), "2026-10-02"); // 23:30 local
    });
  });

  it("is the local date in the early morning east of UTC, where the UTC date is still yesterday", () => {
    inZone("Asia/Tokyo", () => {
      assert.equal(todayIso(new Date("2026-10-01T15:30:00Z")), "2026-10-02"); // 00:30 local
    });
  });
});

describe("formatServings", () => {
  for (const { servings, expected } of text.servingsCases) {
    it(`reads ${servings} as ${JSON.stringify(expected)}`, () => {
      assert.equal(formatServings(servings), expected);
    });
  }

  it("is plural for a fraction of a serving", () => {
    assert.equal(formatServings(0.5), "0.5 servings");
    assert.equal(formatServings(1.5), "1.5 servings");
  });
});

describe("nutritionCells", () => {
  it("formats calories and each macro in the Log table's column order", () => {
    assert.deepEqual(nutritionCells({ calories: 2678.4, proteinG: 112, carbsG: 349.6, fatG: 83 }), [
      "2678 kcal",
      "112 g",
      "350 g",
      "83 g",
    ]);
  });

  it("shows a dash for each unknown value and a zero as a zero", () => {
    assert.deepEqual(nutritionCells({ calories: 0, carbsG: 40 }), ["0 kcal", "–", "40 g", "–"]);
  });

  it("shows four dashes for a day with nothing logged", () => {
    assert.deepEqual(nutritionCells(undefined), ["–", "–", "–", "–"]);
  });
});
