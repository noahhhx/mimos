import type { ShoppingListItem } from "@mimos/api-client";

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
  return aisles.sort(
    (a, b) => Number(done(a)) - Number(done(b)) || rank(a.category) - rank(b.category),
  );
}
