# ADR-0027: Items added to the shopping list by hand

- Status: Accepted
- Date: 2026-11-14

## Context

A week's shopping list (roadmap step 9) is generated from its plan: every
planned recipe's ingredients, scaled and summed, plus each planned food
(ADR-0026). Nothing else can be on it. People also need things no meal
plans for (bin bags, coffee, milk for the week) and had to keep a second
list for them. Regenerating replaces the list's items, keeping only their
ticks, so anything else put on the list has to survive that.

## Decision

- **An item is generated or added.** `shopping_list_item.added`
  (`V22__shopping_list_added_items.sql`, default false, so every existing
  item stays generated) marks an item put on the list by hand. The API
  shows it as `ShoppingListItem.added`.
- **Adding.** `POST /api/v1/plans/{startDate}/shopping-list/items`
  (`addShoppingListItem`, an MCP tool) takes a name (up to 200 characters)
  and an optional quantity (more than 0, at most 10,000) with an optional
  free-text unit (up to 30 characters, only with a quantity). Units are
  free text, unlike a recipe line's metric units: a shopping list buys
  packs and bottles, and nothing calculates with an added item. The aisle
  comes from the name, as for generated items (`Aisles.categorize`). The
  item is appended to the list, at most 1,000 items as for a restored list.
- **A week with no list gets one from its plan first.** The item joins the
  week's list rather than standing in for it, and `generatedAt` stays true.
- **Regeneration keeps added items** as they are, with their ticks, sorted
  among the generated items by aisle and name. An added item is never
  merged with a generated line of the same name and unit, and never lends
  its tick to one: they are separate things to buy.
- **Only added items can be removed** (`removeShoppingListItem`, `DELETE
  …/items/{itemId}`, an MCP tool). A generated item would come back on the
  next regeneration, so removing one is a 400; the plan is where it changes.
- **Locking.** Generating, restoring, and adding all start by locking the
  week's list row, creating it if needed (`ShoppingListRepository.lockOrCreateList`:
  insert on conflict do nothing, then select for update). The list row is
  now updated in place instead of deleted and inserted, so an add and a
  regeneration at the same time never lose each other's items.
- **Export version 8.** `ExportedShoppingListItem.added` is true for an
  added item and absent for a generated one. Version 7 had no added items,
  so its upgrade step changes nothing; `export/v8.json` is the frozen
  fixture.

## Consequences

- The shopping list is no longer purely derived from the plan; the plan
  still decides every generated item.
- Item ids still change on regeneration, added items' included.
- Plugins never see shopping lists, so their contract is unchanged.
