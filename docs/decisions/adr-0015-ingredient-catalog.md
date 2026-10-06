# ADR-0015: Ingredient catalog and calculated nutrition

- Status: Accepted
- Date: 2026-10-05
- Supersedes: none

## Context

A recipe's per-serving nutrition is typed in by hand (ADR-0005). Authors
have to work it out somewhere else, and nothing keeps it in step with
the ingredients. We want an ingredient to carry its nutrition once, and
a recipe's nutrition to follow from the ingredients it uses.

Ingredient lines are free text: "2 cups flour", "garlic cloves, minced",
"salt". Names carry prep notes and units vary, so nutrition cannot be
read off a line by matching its name.

## Decision

### A shared, seeded catalog

The instance has one ingredient catalog, shared by every user and seeded
read-only from `core-recipes/src/main/resources/library/ingredient-seed.json`
at startup, the same way as the recipe library (ADR-0005): insert by slug,
refresh existing entries, never delete. Content fixes ship with a restart.
The seeder runs whether or not the library seed is enabled, since personal
recipes link to the catalog too. It starts small: the ingredients the
library uses. Personal saved ingredients may come later.

*Update 2026-10-06:* the seed now covers the standard kitchen
ingredients, about 470 of them, measured the way a cook measures each:
weighed foods per 100 g, liquids and anything spooned per 100 ml
(converted with a realistic density), and counted foods per piece of a
stated or typical size. Values follow USDA FoodData Central, raw unless
the name says otherwise. Personal ingredients arrived with ADR-0016.
`IngredientSeedTests` (core-recipes) keeps the original slugs, checks
slugs and names, and flags calories that disagree with the macros,
except for a listed few that honestly do (fibre-heavy spices, alcohol).

Each entry has a stable `slug`, a display `name`, a **basis**, and its
calories, protein, carbs, and fat for that basis. All four are required.

| Basis        | Nutrition is given for | Lines it counts         |
| ------------ | ---------------------- | ----------------------- |
| `PER_100_G`  | 100 g                  | `g`, `kg`               |
| `PER_100_ML` | 100 ml                 | `ml`, `l`, `tsp`, `tbsp` |
| `PER_PIECE`  | one piece              | lines with no unit ("2 eggs") |

### Metric units only

A line counts when its unit matches its ingredient's basis: `g`, `kg`,
`ml`, `l`, and the metric spoons `tsp` (5 ml) and `tbsp` (15 ml), case
insensitive. There is no conversion between mass and volume, and no
imperial units. A line in cups or ounces still belongs to the recipe; it
just does not count.

### Lines link to the catalog by slug

An ingredient line may name a catalog entry (`catalogSlug`). The slug, not
a row id, is the reference everywhere: in the API, the library seed, and
the account export. Catalog ids would differ per instance, and slugs do
not.

### A recipe's nutrition is manual or calculated

Each recipe has a `nutritionSource`:

- `MANUAL`: the per-serving values its author typed, as before. Existing
  recipes are all `MANUAL` after the migration.
- `INGREDIENTS`: calculated. Each line contributes when it is linked,
  measured, and in a unit its basis counts; the sum is divided by
  servings and rounded (calories to whole numbers, grams to one decimal).
  With no contributing line, nutrition is unknown.

Calculated nutrition is computed when a recipe is read, not stored, so a
catalog fix reaches every recipe on the next restart with no recompute
step. Everything that reads a recipe (plans, logging, suggestions, the
export) sees the same per-serving `Nutrition` as before. Meal logs still
copy it at logging time, so history does not move when the catalog does.

Library recipes are calculated.

`POST /api/v1/recipes/nutrition-estimate` runs the same calculation on an
unsaved recipe and reports each line's status (`COUNTED`, `UNMEASURED`,
`NOT_LINKED`, `UNIT_NOT_SUPPORTED`), so the recipe form can show what
counts while the author types.

### Export format version 2

Exported recipes gain `nutritionSource` and their lines' `catalogSlug`
(ADR-0011). The upgrade from version 1 marks every recipe `MANUAL`. On
import, a line whose slug is not in this instance's catalog loses the
link; a calculated recipe that loses a link is imported as `MANUAL` with
the nutrition it was exported with, and the import report says so.

## Consequences

- Recipe authors pick, per line, which catalog entry it counts as. The
  form shows the calculated values and which lines did not count.
- A partial recipe (some lines unlinked) shows the sum of what counted.
  The form names the gaps; the number itself does not.
- The library seed is metric and every measured line links to the
  catalog, so library nutrition is calculated from the same data users
  see.
- `RecipeInput.nutritionSource` is required: a client must say whether
  the nutrition it sends is meant. Contract 0.7.0.
- Plugins are unaffected: their context carries no nutrition and no
  catalog data.
