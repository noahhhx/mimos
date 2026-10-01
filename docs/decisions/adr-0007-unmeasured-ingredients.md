# ADR-0007: Unmeasured ingredients

- Status: Accepted
- Date: 2026-10-01
- Supersedes: none

## Context

Recipes are full of ingredients nobody measures: "salt, to taste",
"parsley, to serve", "oil for frying". Until now an ingredient line
required a positive quantity, so the form sent a blank amount as `0` and
the API rejected it — authors had to invent an amount. The quantity also
feeds the shopping list (ADR-0005), which sums it per (normalized name,
unit) across the week, so making it optional needs a rule for those
sums.

## Decision

- An ingredient's `quantity` is optional in the contract
  (`IngredientQuantity`), the domain (`Ingredient`), and the schema
  (`recipe_ingredient.quantity` is nullable). Absent means unmeasured; when
  present it must still be positive. The library seed may use it too.
- Shopping-list aggregation keeps its key, (normalized name, unit). An
  unmeasured ingredient adds its line but nothing to its total: the
  total is the sum of the measured contributions, scaled by planned
  servings. A line with no measured contributions has no quantity
  (`ShoppingListItem.quantity` is optional; `shopping_list_item.quantity`
  is nullable).
- The web shows an unmeasured ingredient by its name alone, both on the
  recipe and on the shopping list.

## Consequences

- "2 tsp salt" and "salt" (no unit) stay separate shopping-list lines, as
  differently-united lines always have; "1 lemon" and "lemon" (both
  unit-less) merge into one line totalling 1. Both are honest about what
  was measured, and neither invents an amount.
- Contract 0.4.0: a response quantity may now be absent. The web is the
  only client and ships with the contract; the plugin extension API does
  not carry ingredients, so plugins are unaffected.
- The migration only relaxes `NOT NULL`, so it is safe on existing data,
  and every existing row keeps its quantity.
