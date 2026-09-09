import assert from "node:assert/strict";
import { test } from "node:test";

import type { LibraryRecipe, PlannedSlot } from "@mimos/plugin-sdk";

import { COUNTRIES } from "./countries.ts";
import { buildCard, dayNumber, matchesFor, pickCountry, suggest } from "./country-week.ts";

const WEEK = "2026-09-07"; // a Monday

function recipe(id: string, title: string, tags: string[]): LibraryRecipe {
  return { id, title, tags, servings: 4 };
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
  const italy = COUNTRIES.find((country) => country.id === "italy");
  assert.ok(italy);
  // r-1 by tag ("italian"), r-2 by title ("minestrone").
  assert.deepEqual(
    matchesFor(italy, CATALOG).map((recipe) => recipe.id),
    ["r-1", "r-2"],
  );
  const india = COUNTRIES.find((country) => country.id === "india");
  assert.ok(india);
  // r-3 by tag ("curry"), r-4 by title ("dahl").
  assert.deepEqual(
    matchesFor(india, CATALOG).map((recipe) => recipe.id),
    ["r-3", "r-4"],
  );
  const greece = COUNTRIES.find((country) => country.id === "greece");
  assert.ok(greece);
  assert.deepEqual(
    matchesFor(greece, CATALOG).map((recipe) => recipe.id),
    ["r-5"],
  );
});

test("pickCountry is deterministic in the week and null without matches", () => {
  const first = pickCountry(WEEK, CATALOG);
  assert.ok(first);
  assert.equal(pickCountry(WEEK, CATALOG)?.id, first.id);
  // Another week may pick another country, but it is stable too.
  const other = pickCountry("2026-09-14", CATALOG);
  assert.equal(pickCountry("2026-09-14", CATALOG)?.id, other?.id);
  // A catalog with no cuisine signal gets no country.
  assert.equal(pickCountry(WEEK, [recipe("r-x", "Plain Porridge", ["breakfast"])]), null);
  // Every catalog-matched country can be picked for some week.
  const picked = new Set<string>();
  for (let day = 0; day < 700; day++) {
    const week = new Date(Date.parse(`${WEEK}T00:00:00Z`) + day * 7 * 86_400_000)
      .toISOString()
      .slice(0, 10);
    const country = pickCountry(week, CATALOG);
    if (country) {
      picked.add(country.id);
    }
  }
  assert.equal(picked.size, 6);
});

test("dayNumber is stable arithmetic", () => {
  assert.equal(dayNumber("2026-09-07"), dayNumber("2026-09-07"));
  assert.equal(dayNumber("2026-09-14") - dayNumber("2026-09-07"), 7);
});

test("buildCard fills unplanned dinner slots and never a taken one", () => {
  const italy = COUNTRIES.find((country) => country.id === "italy");
  assert.ok(italy);
  const taken: PlannedSlot[] = [
    { date: WEEK, mealType: "DINNER", servings: 2 },
    { date: "2026-09-08", mealType: "LUNCH", servings: 1 },
  ];
  const card = buildCard(italy, CATALOG, taken, WEEK);
  assert.ok(card);
  assert.equal(card.title, "Italy week");
  assert.equal(card.icon, "IT");
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
  const italy = COUNTRIES.find((country) => country.id === "italy");
  assert.ok(italy);
  const allDinnersTaken: PlannedSlot[] = Array.from({ length: 7 }, (_, i) => ({
    date: new Date(Date.parse(`${WEEK}T00:00:00Z`) + i * 86_400_000).toISOString().slice(0, 10),
    mealType: "DINNER",
    servings: 2,
  }));
  assert.equal(buildCard(italy, CATALOG, allDinnersTaken, WEEK), null);
  assert.equal(buildCard(italy, [recipe("r-x", "Plain Porridge", ["breakfast"])], [], WEEK), null);
});

test("suggest returns one card for a matchable catalog and none otherwise", () => {
  const cards = suggest({
    weekStartDate: WEEK,
    plannedSlots: [],
    libraryRecipes: CATALOG,
  });
  assert.equal(cards.length, 1);
  assert.ok(cards[0]);
  assert.ok(cards[0].entries.length > 0);
  assert.deepEqual(suggest({ weekStartDate: WEEK, plannedSlots: [], libraryRecipes: [] }), []);
});
