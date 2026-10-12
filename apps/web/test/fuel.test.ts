import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { FuelTarget } from "@mimos/api-client";

import { FUEL_LABEL, fuelLabelFor, fuelTargetParts } from "../src/lib/fuel.ts";

const golden: FuelTarget = {
  calories: 2678,
  proteinG: 112,
  carbsG: 350,
  fatG: 83,
  basis: {
    energyAvailabilityKcalPerKgFfm: 45,
    bodyFatPercent: 15,
    bodyFatSource: "PROFILE",
    fatFreeMassKg: 59.5,
    exerciseKcal: 0,
    sessions: [],
    loadBand: "LIGHT",
    carbsLeftBand: false,
  },
};

describe("fuelTargetParts", () => {
  it("names calories and each macro in the Planned line's order", () => {
    assert.deepEqual(fuelTargetParts(golden), ["2678 kcal", "112 g protein", "350 g carbs", "83 g fat"]);
  });

  it("shows a zero as a zero", () => {
    assert.deepEqual(fuelTargetParts({ ...golden, fatG: 0 }), ["2678 kcal", "112 g protein", "350 g carbs", "0 g fat"]);
  });

  it("shows nothing while targets are off", () => {
    assert.deepEqual(fuelTargetParts(undefined), []);
  });
});

describe("fuel copy", () => {
  const copy = [FUEL_LABEL, fuelLabelFor("2026-10-12"), ...fuelTargetParts(golden)];

  it("calls the target Fuel for the day", () => {
    assert.equal(FUEL_LABEL, "Fuel for the day");
    assert.match(fuelLabelFor("2026-10-12"), /^Fuel for .*12/);
  });

  it("has no em dash and no judging words", () => {
    for (const text of copy) {
      assert.doesNotMatch(text, /—/, text);
      assert.doesNotMatch(text, /\b(over|under|missed|exceeded)\b/i, text);
    }
  });
});
