import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { TrainingSession } from "@mimos/api-client";

import {
  FUEL_LABEL,
  INTENSITIES,
  SPORTS,
  TRAINING_FAILURES,
  TRAINING_LABEL,
  addTrainingLabelFor,
  fuelLabelFor,
  removeSessionLabel,
  sessionParts,
  trainingLabelFor,
} from "../src/lib/fuel.ts";

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
  it("names the sport, minutes, intensity, kind, and source", () => {
    assert.deepEqual(sessionParts(ride), ["Ride", "90 min", "Endurance", "Planned", "By hand"]);
    assert.deepEqual(sessionParts({ ...ride, sport: "STRENGTH", durationMinutes: 45, intensity: "HARD", kind: "COMPLETED" }), [
      "Strength",
      "45 min",
      "Hard",
      "Completed",
      "By hand",
    ]);
  });

  it("leaves out an intensity the session does not have, and names intervals.icu", () => {
    assert.deepEqual(sessionParts({ ...ride, source: "INTERVALS_ICU", intensity: undefined, workKj: 900 }), [
      "Ride",
      "90 min",
      "Planned",
      "intervals.icu",
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
