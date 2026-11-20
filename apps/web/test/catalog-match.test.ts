import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { CatalogIngredient, NutritionBasis } from "@mimos/api-client";

import {
  basisForUnit,
  containsAtWordStart,
  describeEntry,
  lineNameOf,
  mentions,
  searchCatalog,
  suggestCatalogSlug,
} from "../src/lib/catalog-match.ts";

type EntrySketch = { slug: string; name: string; basis: NutritionBasis; calories: number; isShared?: boolean };

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/catalog-match.json", import.meta.url), "utf8"),
) as {
  catalog: EntrySketch[];
  searchCases: { name: string; query: string; expected: string[] }[];
  suggestCases: { name: string; expected: string | null }[];
  mentionsCases: { name: string; entry: string; expected: boolean }[];
  containsCases: { text: string; phrase: string; expected: boolean }[];
  lineNameCases: { entry: string; expected: string }[];
  describeEntryCases: { entry: string; expected: string }[];
  basisCases: { unit: string; expected: NutritionBasis }[];
};

const CATALOG: CatalogIngredient[] = cases.catalog.map(({ slug, name, basis, calories, isShared = true }) => ({
  slug,
  name,
  basis,
  isShared,
  nutrition: { calories, proteinG: 0, carbsG: 0, fatG: 0 },
}));
const entry = (slug: string) => CATALOG.find((e) => e.slug === slug)!;

describe("searchCatalog", () => {
  for (const { name, query, expected } of cases.searchCases) {
    it(name, () => {
      assert.deepEqual(
        searchCatalog(query, CATALOG).map((e) => e.slug),
        expected,
      );
    });
  }

  it("finds at most eight", () => {
    const many = Array.from({ length: 10 }, (_, i) => ({ ...CATALOG[0], slug: `s${i}`, name: `Salt ${i}` }));
    assert.equal(searchCatalog("salt", many).length, 8);
  });
});

describe("suggestCatalogSlug", () => {
  for (const { name, expected } of cases.suggestCases) {
    it(`reads ${JSON.stringify(name)} as ${expected ?? "nothing"}`, () => {
      assert.equal(suggestCatalogSlug(name, CATALOG) ?? null, expected);
    });
  }
});

describe("mentions and containsAtWordStart", () => {
  for (const { name, entry: slug, expected } of cases.mentionsCases) {
    it(`${JSON.stringify(name)} ${expected ? "names" : "does not name"} ${slug}`, () => {
      assert.equal(mentions(name, entry(slug)), expected);
    });
  }
  for (const { text, phrase, expected } of cases.containsCases) {
    it(`${JSON.stringify(text)} ${expected ? "has" : "does not have"} ${JSON.stringify(phrase)} at a word start`, () => {
      assert.equal(containsAtWordStart(text, phrase), expected);
    });
  }
});

describe("lineNameOf, describeEntry, and basisForUnit", () => {
  for (const { entry: slug, expected } of cases.lineNameCases) {
    it(`writes ${slug} as ${JSON.stringify(expected)}`, () => assert.equal(lineNameOf(entry(slug)), expected));
  }
  for (const { entry: slug, expected } of cases.describeEntryCases) {
    it(`describes ${slug} as ${JSON.stringify(expected)}`, () => assert.equal(describeEntry(entry(slug)), expected));
  }
  for (const { unit, expected } of cases.basisCases) {
    it(`guesses ${expected} from ${JSON.stringify(unit)}`, () => assert.equal(basisForUnit(unit), expected));
  }
});
