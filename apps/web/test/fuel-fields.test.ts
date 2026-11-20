import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import { FIELD_ERRORS, GOALS, SEXES, TRAINING_FIELD_ERRORS, namedFields } from "../src/lib/fuel-fields.ts";

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(readFileSync(new URL("../../../libraries/test-cases/fuel.json", import.meta.url), "utf8")) as {
  goals: typeof GOALS;
  sexes: typeof SEXES;
  fieldErrors: typeof FIELD_ERRORS;
  trainingFieldErrors: typeof TRAINING_FIELD_ERRORS;
  namedFieldsCases: { about: string; detail?: string; profile: string[]; training: string[] }[];
};

describe("namedFields", () => {
  for (const each of cases.namedFieldsCases) {
    it(each.about, () => {
      assert.deepEqual(namedFields(each.detail, FIELD_ERRORS), each.profile);
      assert.deepEqual(namedFields(each.detail, TRAINING_FIELD_ERRORS), each.training);
    });
  }
});

describe("the profile form", () => {
  it("offers the goals and sexes the app does, with each goal's protein", () => {
    assert.deepEqual(GOALS, cases.goals);
    assert.deepEqual(SEXES, cases.sexes);
  });

  it("says what to fix as the app does", () => {
    assert.deepEqual(FIELD_ERRORS, cases.fieldErrors);
    assert.deepEqual(TRAINING_FIELD_ERRORS, cases.trainingFieldErrors);
  });

  it("has no em dash and no judging words", () => {
    for (const text of [...Object.values(FIELD_ERRORS), ...Object.values(TRAINING_FIELD_ERRORS)]) {
      assert.doesNotMatch(text, /—/, text);
      assert.doesNotMatch(text, /\b(over|under|missed|exceeded)\b/i, text);
    }
  });
});
