import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { CatalogIngredient, NutritionBasis, RecipeSummary } from "@mimos/api-client";

import {
  describeLink,
  describeRecipe,
  dontCount,
  lineHint,
  linkOf,
  pickEntry,
  pickRecipe,
  retype,
  searchRecipes,
  type LineLink,
} from "../src/lib/ingredient-links.ts";
import type { IngredientRow } from "../src/lib/recipe-input.ts";

function entry(slug: string, name: string, basis: NutritionBasis, calories = 1): CatalogIngredient {
  return { slug, name, basis, isShared: true, nutrition: { calories, proteinG: 0, carbsG: 0, fatG: 0 } };
}

function recipe(id: string, title: string, servings: number, nutrition: RecipeSummary["nutrition"]): RecipeSummary {
  return { id, title, description: "", servings, isLibrary: false, tags: [], nutrition };
}

const CATALOG = [
  entry("olive-oil", "Olive oil", "PER_100_ML", 813),
  entry("garlic-clove", "Garlic clove", "PER_PIECE", 4),
  entry("all-purpose-flour", "All-purpose flour", "PER_100_G", 364),
];
const FOCACCIA = recipe("r-focaccia", "Focaccia", 8, { calories: 278.4, proteinG: 6.4, carbsG: 47.7, fatG: 6.4 });
const PESTO = recipe("r-pesto", "Basil pesto", 1, { calories: 120 });
const RECIPES = [FOCACCIA, PESTO, recipe("r-sandwich", "Focaccia sandwich", 2, {})];
const BY_SLUG = new Map(CATALOG.map((e) => [e.slug, e]));
const BY_ID = new Map(RECIPES.map((r) => [r.id, r]));

const row = (patch: Partial<IngredientRow> = {}): IngredientRow => ({ quantity: "2", unit: "", name: "", ...patch });

describe("searchRecipes and describeRecipe", () => {
  it("finds the user's recipes by a word start of their title, best first", () => {
    assert.deepEqual(
      searchRecipes("foc", RECIPES).map((r) => r.id),
      ["r-focaccia", "r-sandwich"],
    );
    assert.deepEqual(
      searchRecipes("pesto", RECIPES).map((r) => r.id),
      ["r-pesto"],
    );
    assert.deepEqual(searchRecipes("cacc", RECIPES), []);
  });

  it("says what a serving is, or that it is not known", () => {
    assert.equal(describeRecipe(FOCACCIA), "Your recipe · 278 kcal per serving");
    assert.equal(describeRecipe(PESTO), "Your recipe · nutrition unknown");
  });

  it("names a library recipe as one", () => {
    assert.equal(describeRecipe({ ...FOCACCIA, isLibrary: true }), "Library · 278 kcal per serving");
  });
});

describe("picking", () => {
  it("links a picked recipe in servings, named by its title unless the typed name already names it", () => {
    assert.deepEqual(pickRecipe(row({ name: "foc", unit: "g", catalogSlug: "all-purpose-flour" }), FOCACCIA), {
      quantity: "2",
      unit: "servings",
      name: "focaccia",
      recipeId: "r-focaccia",
    });
    assert.equal(pickRecipe(row({ name: "rosemary focaccia, halved" }), FOCACCIA).name, "rosemary focaccia, halved");
  });

  it("drops a recipe link when a catalog entry is picked", () => {
    assert.deepEqual(pickEntry(row({ name: "oli", unit: "tbsp", recipeId: "r-focaccia" }), CATALOG[0]), {
      quantity: "2",
      unit: "tbsp",
      name: "olive oil",
      catalogSlug: "olive-oil",
    });
  });

  it("stops counting a row of either kind, for good", () => {
    assert.deepEqual(dontCount(row({ name: "focaccia", recipeId: "r-focaccia" })), {
      quantity: "2",
      unit: "",
      name: "focaccia",
      catalogSlug: "",
    });
  });
});

describe("retype", () => {
  const typed = (from: IngredientRow, name: string) => retype(from, name, CATALOG, BY_SLUG, BY_ID);

  it("keeps a recipe link while the name still names the recipe, and drops it otherwise", () => {
    const linked = row({ name: "focaccia", unit: "servings", recipeId: "r-focaccia" });
    assert.equal(typed(linked, "focaccia, toasted").recipeId, "r-focaccia");
    assert.deepEqual(typed(linked, "ciabatta"), { quantity: "2", unit: "servings", name: "ciabatta" });
  });

  it("never links a recipe by its name, but still links a catalog entry by name", () => {
    assert.deepEqual(typed(row(), "focaccia"), { quantity: "2", unit: "", name: "focaccia" });
    assert.equal(typed(row({ recipeId: "r-focaccia", name: "focaccia" }), "garlic cloves").catalogSlug, "garlic-clove");
  });

  it("keeps a recipe link whose recipe is still loading", () => {
    assert.equal(retype(row({ recipeId: "r-focaccia" }), "bread", CATALOG, BY_SLUG, new Map()).recipeId, "r-focaccia");
  });

  it("keeps a catalog link while the name names its entry, and leaves a row its author chose not to count", () => {
    assert.equal(typed(row({ name: "garlic clove", catalogSlug: "garlic-clove" }), "garlic cloves").catalogSlug, "garlic-clove");
    assert.equal(typed(row({ name: "olive oil", catalogSlug: "" }), "olive oil").catalogSlug, "");
  });
});

describe("describing a link and why it did not count", () => {
  const asEntry = (index: number): LineLink => ({ kind: "entry", entry: CATALOG[index] });
  const asRecipe = (r: RecipeSummary): LineLink => ({ kind: "recipe", recipe: r });

  it("finds the row's link among what is loaded", () => {
    assert.deepEqual(linkOf(row({ recipeId: "r-pesto" }), BY_SLUG, BY_ID), asRecipe(PESTO));
    assert.deepEqual(linkOf(row({ catalogSlug: "olive-oil" }), BY_SLUG, BY_ID), asEntry(0));
    assert.equal(linkOf(row({ catalogSlug: "" }), BY_SLUG, BY_ID), undefined);
  });

  it("says what the row counts as", () => {
    assert.equal(describeLink(asEntry(0)), "Matched to Olive oil (813 kcal per 100 ml).");
    assert.equal(describeLink(asRecipe(FOCACCIA)), "Uses your recipe Focaccia (makes 8 servings).");
    assert.equal(describeLink(asRecipe(PESTO)), "Uses your recipe Basil pesto (makes 1 serving).");
  });

  it("names the units a linked row must use, and a recipe whose nutrition is unknown", () => {
    assert.equal(lineHint("UNIT_NOT_SUPPORTED", asEntry(0)), "Not counted. Olive oil counts in ml, l, tsp or tbsp.");
    assert.equal(lineHint("UNIT_NOT_SUPPORTED", asEntry(1)), "Not counted. Garlic clove counts in pieces.");
    assert.equal(lineHint("UNIT_NOT_SUPPORTED", asRecipe(FOCACCIA)), "Not counted. A recipe counts in servings.");
    assert.equal(lineHint("NUTRITION_UNKNOWN", asRecipe(PESTO)), "Not counted. Basil pesto's nutrition isn't known yet.");
    assert.equal(lineHint("UNMEASURED", asRecipe(FOCACCIA)), "No amount, so it adds nothing.");
    assert.equal(lineHint("COUNTED", asEntry(1)), undefined);
    assert.equal(lineHint("NOT_LINKED", undefined), undefined);
  });
});
