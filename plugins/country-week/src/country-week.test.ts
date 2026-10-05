import assert from "node:assert/strict";
import { test } from "node:test";

import type { LibraryRecipe, PlannedSlot } from "@mimos/plugin-sdk";

import { type Country, countryByCode } from "./countries.ts";
import { buildCard, matchesFor, suggest } from "./country-week.ts";

const WEEK = "2026-09-07"; // a Monday

function recipe(id: string, title: string, tags: string[]): LibraryRecipe {
  return { id, title, tags, servings: 4 };
}

function country(code: string): Country {
  const found = countryByCode(code);
  assert.ok(found);
  return found;
}

const CATALOG: LibraryRecipe[] = [
  recipe("r-1", "Spaghetti Aglio e Olio", ["dinner", "vegetarian", "italian"]),
  recipe("r-2", "Minestrone", ["dinner", "vegetarian", "soup"]),
  recipe("r-3", "Coconut Chicken Curry", ["dinner", "curry"]),
  recipe("r-4", "Red Lentil Dahl", ["dinner", "vegetarian", "vegan"]),
  recipe("r-5", "Greek Salad", ["lunch", "vegetarian", "salad"]),
  recipe("r-6", "Miso Salmon Rice Bowl", ["dinner", "fish"]),
  recipe("r-7", "Mujadara", ["dinner", "vegetarian"]),
  recipe("r-8", "Beef Chili", ["dinner", "one-pot"]),
];

test("matchesFor finds cuisine by tag and by title keyword, case-insensitively", () => {
  const italy = country("IT");
  // r-1 by tag ("italian"), r-2 by title ("minestrone").
  assert.deepEqual(
    matchesFor(italy, CATALOG).map((recipe) => recipe.id),
    ["r-1", "r-2"],
  );
  const india = country("IN");
  // r-3 by tag ("curry"), r-4 by title ("dahl").
  assert.deepEqual(
    matchesFor(india, CATALOG).map((recipe) => recipe.id),
    ["r-3", "r-4"],
  );
  const greece = country("GR");
  assert.deepEqual(
    matchesFor(greece, CATALOG).map((recipe) => recipe.id),
    ["r-5"],
  );
});

test("buildCard fills unplanned dinner slots and never a taken one", () => {
  const italy = country("IT");
  const taken: PlannedSlot[] = [
    { date: WEEK, mealType: "DINNER", servings: 2 },
    { date: "2026-09-08", mealType: "LUNCH", servings: 1 },
  ];
  const card = buildCard(italy, CATALOG, taken, WEEK);
  assert.ok(card);
  assert.equal(card.title, "Italy week");
  assert.equal(card.icon, "\u{1F1EE}\u{1F1F9}");
  assert.ok(card.blurb?.startsWith("Lean into Italian cooking"));
  assert.deepEqual(
    card.entries.map((entry) => entry.date),
    ["2026-09-08", "2026-09-09"], // Monday's dinner is taken; Tuesday's lunch does not block dinner
  );
  for (const entry of card.entries) {
    assert.equal(entry.mealType, "DINNER");
    assert.equal(entry.servings, 2);
    assert.ok(["r-1", "r-2"].includes(entry.recipeId));
  }
});

test("buildCard returns null when every dinner is planned or nothing matches", () => {
  const italy = country("IT");
  const allDinnersTaken: PlannedSlot[] = Array.from({ length: 7 }, (_, i) => ({
    date: new Date(Date.parse(`${WEEK}T00:00:00Z`) + i * 86_400_000).toISOString().slice(0, 10),
    mealType: "DINNER",
    servings: 2,
  }));
  assert.equal(buildCard(italy, CATALOG, allDinnersTaken, WEEK), null);
  assert.equal(buildCard(italy, [recipe("r-x", "Plain Porridge", ["breakfast"])], [], WEEK), null);
});

test("suggest builds a card for the chosen country and none without one", () => {
  const context = { weekStartDate: WEEK, plannedSlots: [], libraryRecipes: CATALOG };
  const cards = suggest(country("IN"), context);
  assert.equal(cards.length, 1);
  assert.equal(cards[0]?.title, "India week");
  assert.deepEqual(
    cards[0]?.entries.map((entry) => entry.recipeId),
    ["r-3", "r-4"],
  );
  assert.deepEqual(suggest(null, context), []);
  assert.deepEqual(suggest(country("PE"), context), [], "Peru has nothing in this library");
});
