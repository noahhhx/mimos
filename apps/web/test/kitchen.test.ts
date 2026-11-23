import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { MealPlanEntry, MealType, PlanSuggestion, ShoppingListItem } from "@mimos/api-client";

import { greeting, pickThought, pickTonight, stillToBuy, totalMinutes, weekDinners } from "../src/lib/kitchen.ts";

type SketchedEntry = { date: string; mealType: MealType; title: string; diners?: "mine" | "theirs" | "shared" };

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/kitchen.json", import.meta.url), "utf8"),
) as {
  greetingCases: { hour: number; minute: number; greeting: string }[];
  tonightCases: {
    about: string;
    today: string;
    entries: SketchedEntry[];
    expected: { title: string; label: string } | null;
  }[];
  weekCases: {
    about: string;
    monday: string;
    entries: SketchedEntry[];
    expected: { date: string; title: string | null }[];
  }[];
  thoughtCases: {
    about: string;
    today: string;
    suggestions: { title: string; dates: string[] }[];
    entries: SketchedEntry[];
    expected: { title: string; date: string } | null;
  }[];
  stillToBuyCases: {
    about: string;
    limit?: number;
    items: { name: string; checked?: boolean }[];
    expected: { shown: string[]; remaining: number; total: number };
  }[];
  totalMinutesCases: { prepMinutes?: number; cookMinutes?: number; expected: number | null }[];
};

const me = { id: "me", displayName: "Sam", you: true };
const them = { id: "them", displayName: "Alex", you: false };
const dinersOf = { mine: [me], theirs: [them], shared: [me, them] };

const entry = (sketch: SketchedEntry): MealPlanEntry => ({
  id: `${sketch.date}:${sketch.mealType}:${sketch.title}`,
  date: sketch.date,
  mealType: sketch.mealType,
  recipeId: sketch.title.toLowerCase(),
  recipeTitle: sketch.title,
  servings: 2,
  diners: dinersOf[sketch.diners ?? "mine"],
});

const card = (sketch: { title: string; dates: string[] }): PlanSuggestion => ({
  pluginId: "country-week",
  pluginName: "Country of the Week",
  title: sketch.title,
  entries: sketch.dates.map((date) => ({
    date,
    mealType: "DINNER",
    recipeId: "mujadara",
    recipeTitle: "Mujadara",
    servings: 2,
  })),
});

describe("greeting", () => {
  it("follows the local hour, with morning from 05:00, afternoon from 12:00, evening from 18:00", () => {
    for (const each of cases.greetingCases) {
      assert.equal(
        greeting(new Date(2026, 9, 2, each.hour, each.minute)),
        each.greeting,
        `${each.hour}:${each.minute}`,
      );
    }
  });
});

describe("pickTonight", () => {
  for (const each of cases.tonightCases) {
    it(each.about, () => {
      const picked = pickTonight(each.entries.map(entry), each.today);
      assert.deepEqual(picked && { title: picked.entry.recipeTitle, label: picked.label }, each.expected);
    });
  }
});

describe("weekDinners", () => {
  for (const each of cases.weekCases) {
    it(each.about, () => {
      assert.deepEqual(
        weekDinners(each.entries.map(entry), each.monday).map(({ date, dinner }) => ({
          date,
          title: dinner?.recipeTitle ?? null,
        })),
        each.expected,
      );
    });
  }
});

describe("pickThought", () => {
  for (const each of cases.thoughtCases) {
    it(each.about, () => {
      const thought = pickThought(each.suggestions.map(card), each.entries.map(entry), each.today);
      assert.deepEqual(thought && { title: thought.suggestion.title, date: thought.entry.date }, each.expected);
    });
  }
});

describe("stillToBuy", () => {
  const item = (sketch: { name: string; checked?: boolean }): ShoppingListItem => ({
    id: sketch.name,
    name: sketch.name,
    category: "Pantry",
    checked: sketch.checked ?? false,
    added: false,
  });

  for (const each of cases.stillToBuyCases) {
    it(each.about, () => {
      const result = stillToBuy(each.items.map(item), each.limit);
      assert.deepEqual(
        { shown: result.shown.map((shown) => shown.name), remaining: result.remaining, total: result.total },
        each.expected,
      );
    });
  }
});

describe("totalMinutes", () => {
  it("adds prep and cook, using whichever is known", () => {
    for (const each of cases.totalMinutesCases) {
      assert.equal(totalMinutes(each), each.expected, JSON.stringify(each));
    }
  });
});
