import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { ShoppingListItem } from "@mimos/api-client";

import { addedItem, aislesToShop } from "../src/lib/shopping.ts";

const item = (name: string, category: string, checked = false): ShoppingListItem => ({
  id: name,
  name,
  category,
  checked,
  added: false,
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

describe("addedItem", () => {
  it("needs only a name, trimmed", () => {
    assert.deepEqual(addedItem({ name: "  bin bags ", quantity: "", unit: "" }), { input: { name: "bin bags" } });
  });

  it("takes an amount, with or without a unit", () => {
    assert.deepEqual(addedItem({ name: "milk", quantity: "2", unit: " l " }), {
      input: { name: "milk", quantity: 2, unit: "l" },
    });
    assert.deepEqual(addedItem({ name: "lemons", quantity: "3", unit: "" }), {
      input: { name: "lemons", quantity: 3 },
    });
  });

  it("refuses a blank name, a unit without an amount, an amount out of range, and a unit not offered", () => {
    assert.ok("error" in addedItem({ name: " ", quantity: "", unit: "" }));
    assert.ok("error" in addedItem({ name: "milk", quantity: "", unit: "l" }));
    assert.ok("error" in addedItem({ name: "milk", quantity: "0", unit: "" }));
    assert.ok("error" in addedItem({ name: "milk", quantity: "10001", unit: "" }));
    assert.ok("error" in addedItem({ name: "milk", quantity: "lots", unit: "" }));
    assert.ok("error" in addedItem({ name: "bin bags", quantity: "1", unit: "pack" }));
  });
});
