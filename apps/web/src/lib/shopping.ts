import type { ShoppingListItem, ShoppingListItemInput } from "@mimos/api-client";

import { UNITS } from "./recipe-input.ts";

/** Store order: the aisles a shopper walks, in the order they meet them. Unknown categories go last. */
const AISLE_ORDER = ["Produce", "Meat & Seafood", "Dairy & Eggs", "Bakery", "Frozen", "Pantry", "Other"];

export interface Aisle {
  category: string;
  items: ShoppingListItem[];
}

/**
 * The shopping list as the store sees it: grouped by aisle, what's still to
 * buy above what's in the basket. Aisles with something left come first, in
 * store order; aisles already done follow, in the same order. Within each
 * part the list's own order is kept.
 */
export function aislesToShop(items: ShoppingListItem[]): Aisle[] {
  const byCategory = new Map<string, ShoppingListItem[]>();
  for (const item of items) {
    byCategory.set(item.category, [...(byCategory.get(item.category) ?? []), item]);
  }
  const rank = (category: string) => {
    const index = AISLE_ORDER.indexOf(category);
    return index === -1 ? AISLE_ORDER.length : index;
  };
  const aisles = [...byCategory].map(([category, aisleItems]) => ({
    category,
    items: [...aisleItems.filter((item) => !item.checked), ...aisleItems.filter((item) => item.checked)],
  }));
  const done = (aisle: Aisle) => aisle.items.every((item) => item.checked);
  return aisles.sort((a, b) => Number(done(a)) - Number(done(b)) || rank(a.category) - rank(b.category));
}

/** The units the add-an-item form offers: a recipe's metric units, with "" for pieces. */
export const ADD_ITEM_UNITS = UNITS;

/** The add-an-item form as typed: every field a string, as inputs give them. */
export interface AddItemFields {
  name: string;
  quantity: string;
  unit: string;
}

/** The most a hand-added item can be, as the API allows. */
const MAX_QUANTITY = 10_000;

/**
 * The form's fields as an item to add, or what to fix first. Only a name
 * is needed; an amount is optional, and a unit needs an amount.
 */
export function addedItem(fields: AddItemFields): { input: ShoppingListItemInput } | { error: string } {
  const name = fields.name.trim();
  if (!name) {
    return { error: "Say what to buy." };
  }
  if (name.length > 200) {
    return { error: "Keep the name to 200 characters." };
  }
  const unit = fields.unit.trim();
  if (!ADD_ITEM_UNITS.includes(unit)) {
    return { error: "Choose a unit from the list." };
  }
  const typed = fields.quantity.trim();
  if (!typed) {
    return unit ? { error: "Give an amount for the unit, or choose pieces." } : { input: { name } };
  }
  const quantity = Number(typed);
  if (!Number.isFinite(quantity) || quantity <= 0 || quantity > MAX_QUANTITY) {
    return { error: `The amount must be more than 0 and at most ${MAX_QUANTITY}.` };
  }
  return { input: unit ? { name, quantity, unit } : { name, quantity } };
}
