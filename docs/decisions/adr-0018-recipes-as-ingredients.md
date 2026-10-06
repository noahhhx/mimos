# ADR-0018: Recipes as ingredients

- Status: Accepted
- Date: 2026-10-06
- Extends: [ADR-0015](adr-0015-ingredient-catalog.md),
  [ADR-0016](adr-0016-personal-ingredients.md),
  [ADR-0011](adr-0011-account-export-import.md) (export format version 3)

## Context

Cooks build recipes out of other recipes: a chicken focaccia sandwich
uses the focaccia baked from another recipe, a curry uses a homemade
paste. An ingredient line can link a catalog entry (ADR-0015, ADR-0016),
so the sandwich's focaccia either counts as nothing or as an ingredient
the user re-enters by hand, with nutrition that stops matching the
focaccia recipe the next time it changes.

## Decision

### A line links one thing: a catalog entry or one of your recipes

An ingredient line may name one of its owner's own recipes (`recipeId`)
instead of a catalog entry (`catalogSlug`), never both. The domain
refuses both (a 400), and the schema backs it with a check on
`recipe_ingredient` (`linked_recipe_id`, migration V12).

Only the owner's personal recipes can be linked: not a library recipe,
not another user's recipe, and not the recipe itself. A recipe that is
not the owner's is refused with the same "you have no recipe with id"
400 whether it exists or not, so the API does not reveal other users'
recipes. Library recipes link no recipes, so plugins, which see library
recipes only, are unaffected.

Deleting a recipe leaves the lines that used it, unlinked
(`ON DELETE SET NULL`), as deleting a personal ingredient does.

### Counted in servings

A recipe-linked line counts in servings of the linked recipe: the unit
`servings` (or `serving`, any case). Calculation treats the link like a
catalog entry whose basis is one serving and whose nutrition is the
linked recipe's per-serving nutrition, through the same unit and
dimension rule as catalog entries (`MetricUnit.SERVING`). A recipe link
in any other unit, or no unit, is `UNIT_NOT_SUPPORTED`; a catalog entry
never counts in servings.

When any of the linked recipe's four per-serving values is unknown (a
typed recipe with blanks, or a calculated one where nothing counted),
the line is a new status, `NUTRITION_UNKNOWN`, and adds nothing.

### Computed when read

As with catalog entries, a recipe's nutrition is calculated when it is
read: the sandwich reflects the focaccia as it is now. Reading a page of
recipes loads the recipes they link to a level of links at a time (rows
and lines only), then every catalog entry the graph uses in one query,
then walks the graph depth first, memoised. Recipes with no recipe links
cost no extra queries.

### No cycles

Saving refuses a link to the recipe itself and a link to a recipe that
already uses it, directly or through other recipes, with a 400 that
names the recipe in the way ("Focaccia" uses this recipe, so this recipe
can't use it). A recursive query over `linked_recipe_id` finds it.

Two saves at once could still make a cycle. Reading therefore never
recurses forever: a link back to a recipe still being worked out counts
as unknown nutrition.

### Export format version 3

Exported lines carry `recipeId`, the exported `id` of another recipe in
the same document. Import restores each recipe after the recipes it
links to and points the link at the re-created recipe. A link to an id
that is not in the document, or a cycle, is a 400 naming where, and the
import writes nothing. The upgrade from version 2 changes no data, since
version 2 had no recipe links. Frozen fixture: `export/v3.json`.

## Consequences

- Contract 0.10.0: `IngredientQuantity.recipeId` and the
  `NUTRITION_UNKNOWN` line status. The web is the only client and ships
  with it; agents read the rules from the operation descriptions.
- The recipe form's ingredient search also lists the user's own recipes
  (never the one being edited). Picking one is the only way to link a
  recipe; a name alone never links one, since a recipe title is far
  more likely than a catalog name to appear in an unrelated line.
- The shopping list does not expand a linked recipe into its
  ingredients: "2 servings focaccia" is its own line. Expanding it
  (scaled by servings, recursively, cycle-safe) is a possible follow-up
  once someone wants to shop for the parts rather than the whole.
- A deep chain of linked recipes costs one query per level when read.
  Chains are short in practice; if that changes, a recursive query can
  load the graph in one round trip.
