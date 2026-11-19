import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
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

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(readFileSync(new URL("../../../libraries/test-cases/fuel.json", import.meta.url), "utf8")) as {
  sports: typeof SPORTS;
  intensities: typeof INTENSITIES;
  trainingFailures: typeof TRAINING_FAILURES;
  sessionCases: { about: string; session: Omit<TrainingSession, "id" | "date">; parts: string[]; remove: string }[];
};

const session = (sketch: Omit<TrainingSession, "id" | "date">): TrainingSession => ({
  id: "7f1c2d9e-5b3a-4c8e-9d21-6a0b4e3f8c17",
  date: "2026-10-13",
  ...sketch,
});

describe("sessionParts and removeSessionLabel", () => {
  for (const each of cases.sessionCases) {
    it(each.about, () => {
      assert.deepEqual(sessionParts(session(each.session)), each.parts);
      assert.equal(removeSessionLabel(session(each.session)), each.remove);
    });
  }
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
    ...cases.sessionCases.flatMap((each) => [...each.parts, each.remove]),
  ];

  it("names sports, intensities, and failures as the app does", () => {
    assert.deepEqual(SPORTS, cases.sports);
    assert.deepEqual(INTENSITIES, cases.intensities);
    assert.deepEqual(TRAINING_FAILURES, cases.trainingFailures);
  });

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
