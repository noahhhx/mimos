import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { CatalogIngredient, IngredientLineStatus, NutritionBasis, RecipeSummary } from "@mimos/api-client";

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

type LinkSketch = { entry: string } | { recipe: string } | null;

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/ingredient-links.json", import.meta.url), "utf8"),
) as {
  catalog: { slug: string; name: string; basis: NutritionBasis; calories: number }[];
  recipes: { id: string; title: string; servings: number; nutrition: RecipeSummary["nutrition"] }[];
  searchRecipesCases: { query: string; expected: string[] }[];
  describeRecipeCases: { recipe: string; isLibrary: boolean; expected: string }[];
  pickEntryCases: { name: string; row: IngredientRow; entry: string; expected: IngredientRow }[];
  pickRecipeCases: { name: string; row: IngredientRow; recipe: string; expected: IngredientRow }[];
  dontCountCases: { row: IngredientRow; expected: IngredientRow }[];
  retypeCases: { name: string; row: IngredientRow; typed: string; recipesLoaded?: boolean; expected: IngredientRow }[];
  linkOfCases: { row: IngredientRow; expected: LinkSketch }[];
  describeLinkCases: { link: LinkSketch; expected: string }[];
  lineHintCases: { status: IngredientLineStatus | null; link: LinkSketch; expected: string | null }[];
};

const CATALOG: CatalogIngredient[] = cases.catalog.map(({ slug, name, basis, calories }) => ({
  slug,
  name,
  basis,
  isShared: true,
  nutrition: { calories, proteinG: 0, carbsG: 0, fatG: 0 },
}));
const RECIPES: RecipeSummary[] = cases.recipes.map((sketch) => ({
  ...sketch,
  description: "",
  isLibrary: false,
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
  tags: [],
}));
const BY_SLUG = new Map(CATALOG.map((e) => [e.slug, e]));
const BY_ID = new Map(RECIPES.map((r) => [r.id, r]));

function link(sketch: LinkSketch): LineLink | undefined {
  if (sketch === null) {
    return undefined;
  }
  return "entry" in sketch
    ? { kind: "entry", entry: BY_SLUG.get(sketch.entry)! }
    : { kind: "recipe", recipe: BY_ID.get(sketch.recipe)! };
}

describe("searchRecipes and describeRecipe", () => {
  for (const { query, expected } of cases.searchRecipesCases) {
    it(`finds ${JSON.stringify(expected)} for ${JSON.stringify(query)}`, () => {
      assert.deepEqual(
        searchRecipes(query, RECIPES).map((r) => r.id),
        expected,
      );
    });
  }
  for (const { recipe, isLibrary, expected } of cases.describeRecipeCases) {
    it(`describes ${recipe}${isLibrary ? " in the library" : ""}`, () => {
      assert.equal(describeRecipe({ ...BY_ID.get(recipe)!, isLibrary }), expected);
    });
  }
});

describe("picking and not counting", () => {
  for (const { name, row, entry, expected } of cases.pickEntryCases) {
    it(name, () => assert.deepEqual(pickEntry(row, BY_SLUG.get(entry)!), expected));
  }
  for (const { name, row, recipe, expected } of cases.pickRecipeCases) {
    it(name, () => assert.deepEqual(pickRecipe(row, BY_ID.get(recipe)!), expected));
  }
  for (const { row, expected } of cases.dontCountCases) {
    it(`stops counting ${JSON.stringify(row.name)} for good`, () => assert.deepEqual(dontCount(row), expected));
  }
});

describe("retype", () => {
  for (const { name, row, typed, recipesLoaded = true, expected } of cases.retypeCases) {
    it(name, () => {
      assert.deepEqual(retype(row, typed, CATALOG, BY_SLUG, recipesLoaded ? BY_ID : new Map()), expected);
    });
  }
});

describe("describing a link and why it did not count", () => {
  for (const { row, expected } of cases.linkOfCases) {
    it(`finds ${JSON.stringify(expected)} for ${JSON.stringify(row)}`, () => {
      assert.deepEqual(linkOf(row, BY_SLUG, BY_ID), link(expected));
    });
  }
  for (const { link: sketch, expected } of cases.describeLinkCases) {
    it(`says ${JSON.stringify(expected)}`, () => assert.equal(describeLink(link(sketch)!), expected));
  }
  for (const { status, link: sketch, expected } of cases.lineHintCases) {
    it(`hints ${JSON.stringify(expected)} for ${status} on ${JSON.stringify(sketch)}`, () => {
      assert.equal(lineHint(status ?? undefined, link(sketch)) ?? null, expected);
    });
  }
});
