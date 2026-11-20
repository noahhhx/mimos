import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { FuelSex, IntervalsIcuConnection, IntervalsIcuHints } from "@mimos/api-client";

import {
  INTERVALS_ICU_FAILURES,
  bodyFatNote,
  connectionSummary,
  hintSuggestions,
  syncNote,
  type Suggestion,
} from "../src/lib/intervals-icu.ts";

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(readFileSync(new URL("../../../libraries/test-cases/fuel.json", import.meta.url), "utf8")) as {
  intervalsIcuFailures: typeof INTERVALS_ICU_FAILURES;
  connectionCases: { about: string; connection: IntervalsIcuConnection; summary: string; note: string | null }[];
  hintCases: {
    about: string;
    form: { weightKg: string; sex: FuelSex; ftpWatts: string };
    hints?: IntervalsIcuHints;
    suggestions: Suggestion[];
  }[];
  bodyFatCases: { about: string; field: string; hints?: IntervalsIcuHints; note: string | null }[];
};

const time = (iso: string) => `<${iso}>`;

describe("connectionSummary and syncNote", () => {
  for (const each of cases.connectionCases) {
    it(each.about, () => {
      assert.equal(connectionSummary(each.connection, time), each.summary);
      assert.equal(syncNote(each.connection, time), each.note);
    });
  }
});

describe("hintSuggestions", () => {
  for (const each of cases.hintCases) {
    it(each.about, () => {
      assert.deepEqual(hintSuggestions(each.form, each.hints), each.suggestions);
    });
  }
});

describe("bodyFatNote", () => {
  for (const each of cases.bodyFatCases) {
    it(each.about, () => {
      assert.equal(bodyFatNote(each.field, each.hints), each.note);
    });
  }
});

describe("intervals.icu failures", () => {
  it("read as the app's", () => {
    assert.deepEqual(INTERVALS_ICU_FAILURES, cases.intervalsIcuFailures);
  });
});
