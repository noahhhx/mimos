import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { FuelTarget, PlannedDayTotal, TrainingSession } from "@mimos/api-client";

import {
  dayHead,
  ledgerFuel,
  ledgerFuelLabel,
  sessionShort,
  standingSelection,
  toggleSelection,
} from "../src/lib/plan-ledger.ts";

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const fuelCases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/fuel.json", import.meta.url), "utf8"),
) as {
  sessionCases: { about: string; session: Pick<TrainingSession, "sport" | "durationMinutes">; short: string }[];
  ledgerFuelCases: {
    about: string;
    target?: Pick<FuelTarget, "calories">;
    planned?: Pick<PlannedDayTotal, "meals" | "total">;
    expected: ReturnType<typeof ledgerFuel>;
  }[];
};

describe("toggleSelection", () => {
  it("selects a meal or a day", () => {
    assert.deepEqual(toggleSelection(null, { kind: "entry", id: "a" }), { kind: "entry", id: "a" });
    assert.deepEqual(toggleSelection({ kind: "entry", id: "a" }, { kind: "day", date: "2026-11-09" }), {
      kind: "day",
      date: "2026-11-09",
    });
    assert.deepEqual(toggleSelection({ kind: "entry", id: "a" }, { kind: "entry", id: "b" }), {
      kind: "entry",
      id: "b",
    });
  });

  it("closes the inspector when the selected one is chosen again", () => {
    assert.equal(toggleSelection({ kind: "entry", id: "a" }, { kind: "entry", id: "a" }), null);
    assert.equal(toggleSelection({ kind: "day", date: "2026-11-09" }, { kind: "day", date: "2026-11-09" }), null);
  });
});

describe("standingSelection", () => {
  const days = ["2026-11-09", "2026-11-10"];

  it("keeps a meal that is still shown and a day in the week", () => {
    assert.deepEqual(standingSelection({ kind: "entry", id: "a" }, ["a", "b"], days), { kind: "entry", id: "a" });
    assert.deepEqual(standingSelection({ kind: "day", date: "2026-11-10" }, [], days), {
      kind: "day",
      date: "2026-11-10",
    });
  });

  it("drops a meal that was removed or is hidden, and a day from another week", () => {
    assert.equal(standingSelection({ kind: "entry", id: "gone" }, ["a"], days), null);
    assert.equal(standingSelection({ kind: "day", date: "2026-11-16" }, ["a"], days), null);
    assert.equal(standingSelection(null, ["a"], days), null);
  });
});

describe("dayHead", () => {
  it("splits a date into its weekday and day of the month", () => {
    const head = dayHead("2026-11-09");
    assert.equal(head.date, 9);
    assert.equal(
      head.weekday,
      new Date("2026-11-09T00:00:00Z").toLocaleDateString(undefined, { weekday: "short", timeZone: "UTC" }),
    );
  });
});

describe("sessionShort", () => {
  for (const each of fuelCases.sessionCases) {
    it(each.about, () => {
      assert.equal(sessionShort(each.session), each.short);
    });
  }
});

describe("ledgerFuel", () => {
  for (const each of fuelCases.ledgerFuelCases) {
    it(each.about, () => {
      assert.deepEqual(ledgerFuel(each.target, each.planned), each.expected);
    });
  }
});

describe("ledgerFuelLabel", () => {
  it("names the day and the figures", () => {
    assert.match(
      ledgerFuelLabel("2026-11-09", ledgerFuel({ calories: 3120 }, { meals: 1, total: { calories: 1697 } })),
      /^Planned for .+: 1697 of 3120 kcal$/,
    );
    assert.match(
      ledgerFuelLabel("2026-11-09", ledgerFuel({ calories: 3120 }, undefined)),
      /: nothing yet of 3120 kcal$/,
    );
    assert.match(ledgerFuelLabel("2026-11-09", ledgerFuel(undefined, undefined)), /: nothing yet$/);
  });
});

describe("ledger copy", () => {
  it("has no em dash and no judging words", () => {
    const copy = [
      ledgerFuel({ calories: 3120 }, { meals: 2, total: { calories: 4000 } }).detail ?? "",
      ledgerFuel(undefined, { meals: 1, total: { calories: 500 } }).detail ?? "",
      ledgerFuelLabel("2026-11-09", ledgerFuel({ calories: 3120 }, { meals: 2, total: { calories: 4000 } })),
      ledgerFuelLabel("2026-11-09", ledgerFuel(undefined, { meals: 1, total: {} })),
      sessionShort({ sport: "STRENGTH", durationMinutes: 30 }),
    ];
    for (const text of copy) {
      assert.doesNotMatch(text, /—/, text);
      assert.doesNotMatch(text, /\b(over|under|missed|exceeded)\b/i, text);
    }
  });
});
