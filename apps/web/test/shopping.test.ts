import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { ShoppingListItem, ShoppingListItemInput } from "@mimos/api-client";

import { ADD_ITEM_UNITS, addedItem, aislesToShop, type AddItemFields } from "../src/lib/shopping.ts";

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/shopping.json", import.meta.url), "utf8"),
) as {
  aisleCases: {
    about: string;
    items: { name: string; category: string; checked?: boolean }[];
    expected: { category: string; names: string[] }[];
  }[];
  units: string[];
  addedItemCases: { fields: AddItemFields; input?: ShoppingListItemInput; error?: string }[];
  longName: { length: number; error: string };
};

const item = (sketch: { name: string; category: string; checked?: boolean }): ShoppingListItem => ({
  id: sketch.name,
  name: sketch.name,
  category: sketch.category,
  checked: sketch.checked ?? false,
  added: false,
});

describe("aislesToShop", () => {
  for (const each of cases.aisleCases) {
    it(each.about, () => {
      assert.deepEqual(
        aislesToShop(each.items.map(item)).map((aisle) => ({
          category: aisle.category,
          names: aisle.items.map((i) => i.name),
        })),
        each.expected,
      );
    });
  }
});

describe("addedItem", () => {
  it("offers the recipe form's metric units, pieces first", () => {
    assert.deepEqual(ADD_ITEM_UNITS, cases.units);
  });

  it("makes the item to add, or says what to fix", () => {
    for (const each of cases.addedItemCases) {
      const expected = each.input ? { input: each.input } : { error: each.error };
      assert.deepEqual(addedItem(each.fields), expected, JSON.stringify(each.fields));
    }
  });

  it("refuses a name over 200 characters", () => {
    assert.deepEqual(addedItem({ name: "a".repeat(cases.longName.length), quantity: "", unit: "" }), {
      error: cases.longName.error,
    });
  });
});
