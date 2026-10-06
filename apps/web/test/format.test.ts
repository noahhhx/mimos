import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { formatQuantity, formatServings, hasNutrition, todayIso } from "../src/lib/format.ts";

describe("formatQuantity", () => {
  it("formats an amount with its unit", () => {
    assert.equal(formatQuantity(2, "cups"), "2 cups");
    assert.equal(formatQuantity(1.333, "tbsp"), "1.33 tbsp");
    assert.equal(formatQuantity(3, undefined), "3");
  });

  it("counts servings of a recipe in words that agree with the amount", () => {
    assert.equal(formatQuantity(2, "servings"), "2 servings");
    assert.equal(formatQuantity(1, "servings"), "1 serving");
    assert.equal(formatQuantity(0.5, "servings"), "0.5 servings");
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
  it("is singular for exactly one serving", () => {
    assert.equal(formatServings(1), "1 serving");
  });

  it("is plural otherwise", () => {
    assert.equal(formatServings(2), "2 servings");
    assert.equal(formatServings(0.5), "0.5 servings");
    assert.equal(formatServings(1.5), "1.5 servings");
  });
});
