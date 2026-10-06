import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { MealPlanEntry, PlanSuggestion, ShoppingListItem } from "@mimos/api-client";

import { greeting, pickThought, pickTonight, stillToBuy, totalMinutes, weekDinners } from "../src/lib/kitchen.ts";

const entry = (date: string, mealType: MealPlanEntry["mealType"], recipeTitle: string): MealPlanEntry => ({
  id: `${date}:${mealType}:${recipeTitle}`,
  date,
  mealType,
  recipeId: recipeTitle.toLowerCase(),
  recipeTitle,
  servings: 2,
  diners: [{ id: "me", displayName: "Sam", you: true }],
});

const at = (hour: number, minute = 0) => new Date(2026, 9, 2, hour, minute);

describe("greeting", () => {
  it("follows the local hour, with morning from 05:00, afternoon from 12:00, evening from 18:00", () => {
    assert.equal(greeting(at(4, 59)), "Good evening.");
    assert.equal(greeting(at(5)), "Good morning.");
    assert.equal(greeting(at(11, 59)), "Good morning.");
    assert.equal(greeting(at(12)), "Good afternoon.");
    assert.equal(greeting(at(17, 59)), "Good afternoon.");
    assert.equal(greeting(at(18)), "Good evening.");
    assert.equal(greeting(at(0)), "Good evening.");
  });
});

describe("pickTonight", () => {
  const today = "2026-10-02";

  it("takes today's first dinner", () => {
    const entries = [
      entry("2026-10-01", "DINNER", "Yesterday"),
      entry(today, "LUNCH", "Soup"),
      entry(today, "DINNER", "Dahl"),
      entry(today, "DINNER", "Curry"),
    ];
    assert.deepEqual(pickTonight(entries, today), { entry: entries[2], label: "Tonight" });
  });

  it("falls back to today's lunch, then breakfast, then a snack, under Today", () => {
    const breakfast = entry(today, "BREAKFAST", "Oats");
    const lunch = entry(today, "LUNCH", "Soup");
    const snack = entry(today, "SNACK", "Apple");
    assert.deepEqual(pickTonight([breakfast, snack, lunch], today), { entry: lunch, label: "Today" });
    assert.deepEqual(pickTonight([snack, breakfast], today), { entry: breakfast, label: "Today" });
    assert.deepEqual(pickTonight([snack], today), { entry: snack, label: "Today" });
  });

  it("is null when nothing is planned today", () => {
    assert.equal(pickTonight([entry("2026-10-03", "DINNER", "Chili")], today), null);
  });
});

describe("weekDinners", () => {
  it("lists Monday to Sunday with each day's first dinner", () => {
    const entries = [entry("2026-10-04", "DINNER", "Chili"), entry("2026-09-28", "LUNCH", "Soup"), entry("2026-09-28", "DINNER", "Minestrone")];
    const week = weekDinners(entries, "2026-09-28");
    assert.deepEqual(
      week.map(({ date, dinner }) => [date, dinner?.recipeTitle ?? null]),
      [
        ["2026-09-28", "Minestrone"],
        ["2026-09-29", null],
        ["2026-09-30", null],
        ["2026-10-01", null],
        ["2026-10-02", null],
        ["2026-10-03", null],
        ["2026-10-04", "Chili"],
      ],
    );
  });
});

describe("pickThought", () => {
  const card = (title: string, dates: string[]): PlanSuggestion => ({
    pluginId: "country-week",
    pluginName: "Country of the Week",
    title,
    entries: dates.map((date) => ({ date, mealType: "DINNER", recipeId: "mujadara", recipeTitle: "Mujadara", servings: 2 })),
  });

  it("offers the first open dinner from today on", () => {
    const suggestion = card("Lebanon week", ["2026-09-30", "2026-10-02", "2026-10-04"]);
    const planned = [entry("2026-10-02", "DINNER", "Dahl")];
    const thought = pickThought([suggestion], planned, "2026-10-02");
    assert.equal(thought?.entry.date, "2026-10-04");
    assert.equal(thought?.suggestion, suggestion);
  });

  it("skips cards with nothing open and past days", () => {
    const past = card("Past", ["2026-09-29"]);
    const taken = card("Taken", ["2026-10-03"]);
    const open = card("Open", ["2026-10-02"]);
    const planned = [entry("2026-10-03", "DINNER", "Chili")];
    assert.equal(pickThought([past, taken, open], planned, "2026-10-02")?.suggestion.title, "Open");
    assert.equal(pickThought([past, taken], planned, "2026-10-02"), null);
    assert.equal(pickThought([], planned, "2026-10-02"), null);
  });
});

describe("stillToBuy", () => {
  const item = (name: string, checked: boolean): ShoppingListItem => ({ id: name, name, category: "Pantry", checked });

  it("shows unchecked items first, fills up with checked ones, and counts what's left", () => {
    const items = [item("a", true), item("b", false), item("c", false), item("d", true)];
    const result = stillToBuy(items, 3);
    assert.deepEqual(result.shown.map((shown) => shown.name), ["b", "c", "a"]);
    assert.equal(result.remaining, 2);
    assert.equal(result.total, 4);
  });

  it("caps the rows at five by default", () => {
    const items = Array.from({ length: 8 }, (_, i) => item(`i${i}`, false));
    assert.equal(stillToBuy(items).shown.length, 5);
  });
});

describe("totalMinutes", () => {
  it("adds prep and cook, using whichever is known", () => {
    assert.equal(totalMinutes({ prepMinutes: 10, cookMinutes: 30 }), 40);
    assert.equal(totalMinutes({ cookMinutes: 30 }), 30);
    assert.equal(totalMinutes({ prepMinutes: 5 }), 5);
    assert.equal(totalMinutes({}), null);
  });
});
