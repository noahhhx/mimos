# ADR-0026: Planned foods

- Status: Accepted
- Date: 2026-11-14

## Context

A meal plan entry has always been a recipe: `meal_plan_entry.recipe_id` is
not null (V4). People also plan single foods. A banana as a snack, a bagel
before a ride, a yoghurt at breakfast. Today each of those needs a
one-ingredient recipe first, so most people never plan them, and the day's
planned totals and Fuel for the day (ADR-0022) miss them.

Ingredients already carry everything a planned food needs. The shared
catalog and the household's own ingredients (ADR-0015, ADR-0016) give a
name, a nutrition basis (per 100 g, per 100 ml, or one piece), and the four
values. Recipe lines count an amount of one with the same units. A barcode
lookup (ADR-0025) gives the same shape for packaged food.

## Decision

- **A plan entry plans exactly one of two things.** In `core-planning`,
  `PlannedItem` is a sealed interface: `PlannedItem.Recipe(recipeId)` or
  `PlannedItem.Food(catalogSlug, unit)`. `PlannedMeal` carries the item, a
  title resolved for display (the recipe's title or the ingredient's name),
  and one `amount`: servings of the recipe, or the food's quantity in its
  unit (pieces when the unit is absent). The amount is what is planned for
  all the diners together, so the caller's share stays `amount / diners`,
  rounded as ADR-0019 rounds servings.
- **A food is an ingredient.** `catalogSlug` names a shared catalog entry
  or one of the household's own, as a recipe line does. The unit must fit
  the entry's basis (`g`, `kg` for per 100 g; `ml`, `l`, `tsp`, `tbsp` for
  per 100 ml; none for pieces), checked when the entry is written, and the
  quantity is at most 10,000. `CatalogIngredient.nutritionOf(quantity,
  unit)` in `core-recipes` is the one place that turns an amount of an
  ingredient into nutrition, so `MetricUnit` stays package-private and
  `core-planning` keeps to the public interface.
- **Schema.** `V20__planned_foods.sql` makes `recipe_id` nullable, adds
  `food_slug` (references `catalog_ingredient` on delete cascade, like a
  recipe's cascade) and `food_unit`, and a check that exactly one of
  `recipe_id` and `food_slug` is set. The `servings` column keeps its name
  and holds the amount for both.
- **Everything that reads the plan treats a food like a recipe.** Planned
  totals count a food's share from its ingredient's nutrition. The shopping
  list adds the food's name, unit, and amount as a line, merged with recipe
  lines of the same name and unit. Plugins see a food entry as a planned
  slot with no `recipeId`, as they see a personal recipe (ADR-0006); the
  plugin contract does not change.
- **API, additive.** `MealPlanEntry.recipeId` and `recipeTitle` become
  optional, and a `food` object (`catalogSlug`, `name`, `unit`) appears
  instead for a planned food. `MealPlanEntryInput` takes `recipeId` or
  `food` (`catalogSlug`, `unit`), never both. `servings` stays the one
  amount in the entry, its input, and its patch. The operation descriptions
  state the exactly-one rule, since they are all an agent reads (ADR-0014).
- **Barcodes add an ingredient, not a new kind of record.** Scanning a
  product while planning creates a household ingredient from the lookup
  (`createIngredient`, basis from the product's unit) and plans that. When
  Open Food Facts lacks a value, the form asks for it before saving. The
  product is then an ingredient the person can search for next week. No
  product table, no cached lookups.
- **Logging a planned food** logs an ad-hoc entry with the share's amount
  and the nutrition the browser calculates, as the Log page logs an
  ingredient today. No API change.
- **Account export is version 7.** `ExportedPlanEntry` gains `food`
  (`catalogSlug`, `unit`), with exactly one of `recipeId`, `librarySlug`,
  and `food`. A food's slug is an own ingredient's slug within the document
  or a shared catalog slug. Import skips an entry whose slug this instance
  does not know, as it skips a library recipe it lacks. The upgrade step
  from 6 is a no-op, since version 6 had no foods.

## Alternatives considered

- **Wrap each food in a hidden one-line recipe.** No schema change, but a
  recipe per banana per household, shown in recipe lists or hidden by a
  flag everything must honor, and a recipe's servings standing in for an
  amount. The data lies about what the person did.
- **Nullable `recipe_id` and food columns checked ad hoc.** The same
  schema, but every reader branches on nulls. The sealed type makes the
  "one of two" rule the compiler's job.
- **A separate `quantity` field for foods next to `servings`.** Two
  optional numbers with an exactly-one rule of their own, and the share,
  patch, and export each branch on which is set. One amount with one
  meaning per branch is smaller.
- **A food that is not an ingredient (name and nutrition on the entry).**
  Nothing to search for next week, and nothing the shopping list could
  name consistently.

## Consequences

- `core-planning` depends on `IngredientService` as well as `RecipeService`,
  both through their public interfaces.
- Deleting a household ingredient deletes the entries that plan it. The
  shared catalog is never deleted (ADR-0015).
- The Kitchen home's tonight card shows a planned food by name without a
  recipe link or recipe facts.
- The web plan page's picker searches ingredients next to recipes and
  offers **Scan a barcode**. A food entry shows its amount with its unit
  and an editable amount instead of the servings stepper.
