import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { CatalogIngredient, NutritionBasis } from "@mimos/api-client";

import {
  basisForUnit,
  describeEntry,
  lineNameOf,
  mentions,
  searchCatalog,
  suggestCatalogSlug,
} from "../src/lib/catalog-match.ts";

function entry(slug: string, name: string, basis: NutritionBasis, calories = 1, isShared = true): CatalogIngredient {
  return { slug, name, basis, isShared, nutrition: { calories, proteinG: 0, carbsG: 0, fatG: 0 } };
}

const CATALOG: CatalogIngredient[] = [
  entry("garlic-clove", "Garlic clove", "PER_PIECE", 4),
  entry("olive-oil", "Olive oil", "PER_100_ML", 813),
  entry("vegetable-oil", "Vegetable oil", "PER_100_ML"),
  entry("olives", "Olives", "PER_100_G"),
  entry("parsley", "Fresh parsley", "PER_100_ML"),
  entry("egg", "Egg", "PER_PIECE"),
  entry("onion", "Onion", "PER_PIECE"),
  entry("red-lentils", "Red lentils, dry", "PER_100_G"),
  entry("beef-mince", "Beef mince (20% fat)", "PER_100_G"),
  entry("dragon-fruit-k3f9q2", "Dragon fruit", "PER_PIECE", 60, false),
];

describe("searchCatalog", () => {
  it("puts names that start with the query first, and skips matches inside a word", () => {
    assert.deepEqual(
      searchCatalog("oil", CATALOG).map((e) => e.slug),
      ["olive-oil", "vegetable-oil"],
    );
    assert.deepEqual(
      searchCatalog("ol", CATALOG).map((e) => e.slug),
      ["olive-oil", "olives"],
    );
    assert.deepEqual(
      searchCatalog("on", CATALOG).map((e) => e.slug),
      ["onion"],
    );
  });

  it("finds the user's own ingredients too, and nothing for an empty query", () => {
    assert.deepEqual(
      searchCatalog("dragon", CATALOG).map((e) => e.slug),
      ["dragon-fruit-k3f9q2"],
    );
    assert.deepEqual(searchCatalog("  ", CATALOG), []);
  });
});

describe("suggestCatalogSlug", () => {
  it("finds the entry a line names, plurals aside", () => {
    assert.equal(suggestCatalogSlug("garlic cloves", CATALOG), "garlic-clove");
    assert.equal(suggestCatalogSlug("Eggs", CATALOG), "egg");
    assert.equal(suggestCatalogSlug("parsley", CATALOG), "parsley");
    assert.equal(suggestCatalogSlug("red lentils", CATALOG), "red-lentils");
  });

  it("prefers the longest match, and matches only at a word start", () => {
    assert.equal(suggestCatalogSlug("olive oil", CATALOG), "olive-oil");
    assert.equal(suggestCatalogSlug("kalamata olives", CATALOG), "olives");
    assert.equal(suggestCatalogSlug("scallion", CATALOG), undefined);
    assert.equal(suggestCatalogSlug("oil", CATALOG), undefined);
  });
});

describe("mentions and lineNameOf", () => {
  it("keeps a link while the name still names the entry", () => {
    assert.equal(mentions("garlic cloves", CATALOG[0]), true);
    assert.equal(mentions("ginger", CATALOG[0]), false);
  });

  it("writes a picked entry as a line name without its qualifiers", () => {
    assert.equal(lineNameOf(CATALOG[7]), "red lentils");
    assert.equal(lineNameOf(CATALOG[8]), "beef mince");
  });
});

describe("describeEntry and basisForUnit", () => {
  it("says what an entry's calories are for", () => {
    assert.equal(describeEntry(CATALOG[1]), "813 kcal per 100 ml");
    assert.equal(describeEntry(CATALOG[0]), "4 kcal each");
  });

  it("guesses a new ingredient's basis from its line's unit", () => {
    assert.equal(basisForUnit(""), "PER_PIECE");
    assert.equal(basisForUnit("tbsp"), "PER_100_ML");
    assert.equal(basisForUnit("kg"), "PER_100_G");
  });
});
