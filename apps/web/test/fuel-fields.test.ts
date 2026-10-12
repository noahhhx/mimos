import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { FIELD_ERRORS, TRAINING_FIELD_ERRORS, namedFields } from "../src/lib/fuel-fields.ts";

describe("namedFields", () => {
  it("reads every field the API names in a 400 detail", () => {
    const detail =
      "weightKg must be between 30 and 250; bodyFatPercent must be between 3 and 60;" +
      " ftpWatts must be between 50 and 600; proteinGPerKg must be between 1.2 and 3.0";
    assert.deepEqual(namedFields(detail, FIELD_ERRORS), ["weightKg", "bodyFatPercent", "ftpWatts", "proteinGPerKg"]);
  });

  it("names nothing for a detail about something else", () => {
    assert.deepEqual(namedFields("weightKg is required", FIELD_ERRORS), ["weightKg"]);
    assert.deepEqual(namedFields("constructor must be between 1 and 2; toString is odd", FIELD_ERRORS), []);
    assert.deepEqual(namedFields(undefined, FIELD_ERRORS), []);
  });

  it("reads only the fields of the form it is given", () => {
    const detail = "durationMinutes must be between 1 and 1440";
    assert.deepEqual(namedFields(detail, TRAINING_FIELD_ERRORS), ["durationMinutes"]);
    assert.deepEqual(namedFields(detail, FIELD_ERRORS), []);
    assert.deepEqual(namedFields("weightKg must be between 30 and 250", TRAINING_FIELD_ERRORS), []);
  });
});

describe("field errors", () => {
  it("have no em dash and no judging words", () => {
    for (const text of [...Object.values(FIELD_ERRORS), ...Object.values(TRAINING_FIELD_ERRORS)]) {
      assert.doesNotMatch(text, /—/, text);
      assert.doesNotMatch(text, /\b(over|under|missed|exceeded)\b/i, text);
    }
  });
});
