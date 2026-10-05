import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { CatalogIngredient } from "@mimos/api-client";

import { lineHint, suggestCatalogSlug } from "../src/lib/catalog-match.ts";

const nutrition = { calories: 1, proteinG: 0, carbsG: 0, fatG: 0 };
const CATALOG: CatalogIngredient[] = [
  { slug: "garlic-clove", name: "Garlic clove", basis: "PER_PIECE", nutrition },
  { slug: "olive-oil", name: "Olive oil", basis: "PER_100_ML", nutrition },
  { slug: "vegetable-oil", name: "Vegetable oil", basis: "PER_100_ML", nutrition },
  { slug: "olives", name: "Olives", basis: "PER_100_G", nutrition },
  { slug: "parsley", name: "Fresh parsley", basis: "PER_100_ML", nutrition },
  { slug: "egg", name: "Egg", basis: "PER_PIECE", nutrition },
  { slug: "onion", name: "Onion", basis: "PER_PIECE", nutrition },
  { slug: "white-rice", name: "White rice, uncooked", basis: "PER_100_G", nutrition },
];

describe("suggestCatalogSlug", () => {
  it("finds the entry a line names, prep notes and plurals aside", () => {
    assert.equal(suggestCatalogSlug("garlic cloves, minced", CATALOG), "garlic-clove");
    assert.equal(suggestCatalogSlug("Eggs", CATALOG), "egg");
    assert.equal(suggestCatalogSlug("parsley, chopped", CATALOG), "parsley");
    assert.equal(suggestCatalogSlug("white rice", CATALOG), "white-rice");
  });

  it("prefers the longest match", () => {
    assert.equal(suggestCatalogSlug("olive oil", CATALOG), "olive-oil");
    assert.equal(suggestCatalogSlug("kalamata olives", CATALOG), "olives");
  });

  it("matches only at a word start, and suggests nothing for an unknown name", () => {
    assert.equal(suggestCatalogSlug("scallion", CATALOG), undefined);
    assert.equal(suggestCatalogSlug("oil", CATALOG), undefined);
    assert.equal(suggestCatalogSlug("", CATALOG), undefined);
  });
});

describe("lineHint", () => {
  it("names the units a linked line must use", () => {
    assert.equal(lineHint("UNIT_NOT_SUPPORTED", CATALOG[1]), "Not counted. Olive oil counts in ml, l, tsp or tbsp.");
    assert.equal(lineHint("UNMEASURED", CATALOG[0]), "No amount, so it adds nothing.");
    assert.equal(lineHint("COUNTED", CATALOG[0]), undefined);
    assert.equal(lineHint("NOT_LINKED", undefined), undefined);
  });
});
