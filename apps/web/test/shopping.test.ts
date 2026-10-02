import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { ShoppingListItem } from "@mimos/api-client";

import { aislesToShop } from "../src/lib/shopping.ts";

const item = (name: string, category: string, checked = false): ShoppingListItem => ({
  id: name,
  name,
  category,
  checked,
});

const shape = (items: ShoppingListItem[]) =>
  aislesToShop(items).map((aisle) => [aisle.category, aisle.items.map((i) => i.name)]);

describe("aislesToShop", () => {
  it("groups by aisle in store order, unknown categories last", () => {
    assert.deepEqual(
      shape([item("salt", "Pantry"), item("mystery", "Garden"), item("onion", "Produce"), item("milk", "Dairy & Eggs")]),
      [
        ["Produce", ["onion"]],
        ["Dairy & Eggs", ["milk"]],
        ["Pantry", ["salt"]],
        ["Garden", ["mystery"]],
      ],
    );
  });

  it("puts what's still to buy above what's checked off, keeping the list's order within each", () => {
    assert.deepEqual(
      shape([
        item("carrots", "Produce", true),
        item("garlic", "Produce"),
        item("leeks", "Produce", true),
        item("onion", "Produce"),
      ]),
      [["Produce", ["garlic", "onion", "carrots", "leeks"]]],
    );
  });

  it("moves aisles with nothing left to buy below the rest, still in store order", () => {
    assert.deepEqual(
      shape([
        item("onion", "Produce", true),
        item("milk", "Dairy & Eggs", true),
        item("salt", "Pantry"),
        item("buns", "Bakery"),
      ]),
      [
        ["Bakery", ["buns"]],
        ["Pantry", ["salt"]],
        ["Produce", ["onion"]],
        ["Dairy & Eggs", ["milk"]],
      ],
    );
  });

  it("returns nothing for an empty list", () => {
    assert.deepEqual(aislesToShop([]), []);
  });
});
