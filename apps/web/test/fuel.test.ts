import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { FuelTarget, TrainingSession } from "@mimos/api-client";

import {
  FUEL_LABEL,
  INTENSITIES,
  SPORTS,
  TRAINING_FAILURES,
  TRAINING_LABEL,
  addTrainingLabelFor,
  fuelLabelFor,
  fuelTargetParts,
  removeSessionLabel,
  sessionParts,
  trainingLabelFor,
} from "../src/lib/fuel.ts";

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

const ride: TrainingSession = {
  id: "7f1c2d9e-5b3a-4c8e-9d21-6a0b4e3f8c17",
  date: "2026-10-13",
  source: "MANUAL",
  kind: "PLANNED",
  sport: "RIDE",
  durationMinutes: 90,
  intensity: "ENDURANCE",
};

describe("sessionParts", () => {
  it("names the sport, minutes, intensity, and kind", () => {
    assert.deepEqual(sessionParts(ride), ["Ride", "90 min", "Endurance", "Planned"]);
    assert.deepEqual(sessionParts({ ...ride, sport: "STRENGTH", durationMinutes: 45, intensity: "HARD", kind: "COMPLETED" }), [
      "Strength",
      "45 min",
      "Hard",
      "Completed",
    ]);
  });

  it("leaves out an intensity the session does not have", () => {
    assert.deepEqual(sessionParts({ ...ride, source: "INTERVALS_ICU", intensity: undefined, workKj: 900 }), [
      "Ride",
      "90 min",
      "Planned",
    ]);
  });
});

describe("removeSessionLabel", () => {
  it("names the session it removes", () => {
    assert.equal(removeSessionLabel(ride), "Remove Ride, 90 min");
  });
});

describe("fuel copy", () => {
  const copy = [
    FUEL_LABEL,
    fuelLabelFor("2026-10-12"),
    ...fuelTargetParts(golden),
    TRAINING_LABEL,
    trainingLabelFor("2026-10-13"),
    addTrainingLabelFor("2026-10-13"),
    ...Object.values(TRAINING_FAILURES),
    ...Object.values(SPORTS),
    ...Object.values(INTENSITIES),
    ...sessionParts(ride),
    ...sessionParts({ ...ride, kind: "COMPLETED" }),
    removeSessionLabel(ride),
  ];

  it("calls the target Fuel for the day", () => {
    assert.equal(FUEL_LABEL, "Fuel for the day");
    assert.match(fuelLabelFor("2026-10-12"), /^Fuel for .*12/);
    assert.match(trainingLabelFor("2026-10-13"), /^Training for .*13/);
  });

  it("has no em dash and no judging words", () => {
    for (const text of copy) {
      assert.doesNotMatch(text, /—/, text);
      assert.doesNotMatch(text, /\b(over|under|missed|exceeded)\b/i, text);
    }
  });
});
