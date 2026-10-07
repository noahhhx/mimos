import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { namedFields } from "../src/lib/fuel-fields.ts";

describe("namedFields", () => {
  it("reads every field the API names in a 400 detail", () => {
    const detail =
      "weightKg must be between 30 and 250; bodyFatPercent must be between 3 and 60;" +
      " ftpWatts must be between 50 and 600; proteinGPerKg must be between 1.2 and 3.0";
    assert.deepEqual(namedFields(detail), ["weightKg", "bodyFatPercent", "ftpWatts", "proteinGPerKg"]);
  });

  it("names nothing for a detail about something else", () => {
    assert.deepEqual(namedFields("weightKg is required"), ["weightKg"]);
    assert.deepEqual(namedFields("constructor must be between 1 and 2; toString is odd"), []);
    assert.deepEqual(namedFields(undefined), []);
  });
});
