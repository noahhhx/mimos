import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  dayHead,
  ledgerFuel,
  ledgerFuelLabel,
  sessionShort,
  standingSelection,
  toggleSelection,
} from "../src/lib/plan-ledger.ts";

describe("toggleSelection", () => {
  it("selects a meal or a day", () => {
    assert.deepEqual(toggleSelection(null, { kind: "entry", id: "a" }), { kind: "entry", id: "a" });
    assert.deepEqual(toggleSelection({ kind: "entry", id: "a" }, { kind: "day", date: "2026-11-09" }), {
      kind: "day",
      date: "2026-11-09",
    });
    assert.deepEqual(toggleSelection({ kind: "entry", id: "a" }, { kind: "entry", id: "b" }), { kind: "entry", id: "b" });
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
    assert.equal(head.weekday, new Date("2026-11-09T00:00:00Z").toLocaleDateString(undefined, { weekday: "short", timeZone: "UTC" }));
  });
});

describe("sessionShort", () => {
  it("names the sport and its minutes", () => {
    assert.equal(sessionShort({ sport: "RIDE", durationMinutes: 90 }), "Ride 90 min");
  });
});

describe("ledgerFuel", () => {
  const planned = (calories: number | undefined, meals = 2) => ({ meals, total: calories == null ? {} : { calories } });

  it("puts planned calories against the target while targets are on", () => {
    assert.deepEqual(ledgerFuel({ calories: 3120.4 }, planned(1696.6)), {
      value: "1697",
      detail: "of 3120 kcal",
      said: "1697",
    });
  });

  it("shows a dash for a day with nothing planned, still against its target", () => {
    const nothing = { value: "–", detail: "of 2650 kcal", said: "nothing yet" };
    assert.deepEqual(ledgerFuel({ calories: 2650 }, undefined), nothing);
    assert.deepEqual(ledgerFuel({ calories: 2650 }, planned(0, 0)), nothing);
  });

  it("shows calories alone while targets are off", () => {
    assert.deepEqual(ledgerFuel(undefined, planned(800)), { value: "800", detail: "kcal", said: "800" });
    assert.deepEqual(ledgerFuel(undefined, undefined), { value: "–", detail: null, said: "nothing yet" });
  });

  it("shows unknown calories of planned meals as a dash, not zero, and says they are unknown", () => {
    assert.deepEqual(ledgerFuel(undefined, planned(undefined)), { value: "–", detail: null, said: "calories unknown" });
    assert.deepEqual(ledgerFuel({ calories: 2650 }, planned(undefined)), {
      value: "–",
      detail: "of 2650 kcal",
      said: "calories unknown",
    });
  });
});

describe("ledgerFuelLabel", () => {
  it("names the day and the figures", () => {
    assert.match(ledgerFuelLabel("2026-11-09", ledgerFuel({ calories: 3120 }, { meals: 1, total: { calories: 1697 } })), /^Planned for .+: 1697 of 3120 kcal$/);
    assert.match(ledgerFuelLabel("2026-11-09", ledgerFuel({ calories: 3120 }, undefined)), /: nothing yet of 3120 kcal$/);
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
