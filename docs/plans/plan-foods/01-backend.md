# 01. Domain, schema, API, export

**Depends on:** –

The data shape, its storage, and every server-side reader of the plan.

## Build

- `core-recipes`: `CatalogIngredient.nutritionOf(double quantity, @Nullable String unit)`
  returns `Optional<Nutrition>`: the nutrition of that amount, exact, or
  empty when the unit does not fit the basis. `NutritionCalculator` keeps
  its own path; a unit test pins that both agree for a recipe line.
- `apps/api` migration `V20__planned_foods.sql`: `recipe_id` nullable,
  `food_slug text references catalog_ingredient (slug) on delete cascade`,
  `food_unit text`, check `(recipe_id is null) <> (food_slug is null)`,
  check `food_unit is null or food_slug is not null`, index on `food_slug`.
- `core-planning`: `PlannedItem` (sealed: `Recipe(UUID recipeId)`,
  `Food(String catalogSlug, @Nullable String unit)`), `PlannedMeal(id,
  date, mealType, item, title, amount, dinerProfileIds)`, the repository's
  input record and row mapper, `MealPlanService.addEntry` taking a
  `PlannedItem` and an amount (a food's slug must be visible to the owner
  and its unit fit the basis, else 400 as `IllegalArgumentException`;
  amount bounds: `MAX_SERVINGS` 100 for a recipe, `MAX_FOOD_QUANTITY`
  10,000 for a food), `PlannedDayTotal` over a `Map<PlannedItem, Nutrition>`
  of per-unit nutrition (a recipe's per serving, a food's `nutritionOf(1,
  unit)`), the shopping list adding a food as a line, and
  `SuggestionService` mapping a food to a slot with no `recipeId`.
- Contract: `MealPlanEntry` (`recipeId`, `recipeTitle` optional; `food`
  object with `catalogSlug`, `name`, `unit`), `MealPlanEntryInput`
  (`recipeId` or `food`, exactly one), descriptions of `addMealPlanEntry`,
  `updateMealPlanEntry`, `getMealPlan`, and `createMealLog` naming the
  rule and how a food is logged. Regenerate `@mimos/api-client`.
- Export version 7: `ExportedPlanEntry.food`, exporter and importer
  branches, `ExportUpgrader` step (no-op), frozen `export/v7.json` with a
  planned shared-catalog food and a planned own ingredient.

## Checks

- `PlannedDayTotalTest`: a food eaten alone, a food shared, a food with a
  unit the basis does not take counts as unknown.
- `MealPlansEndpointTests`: plan a banana, plan 85 g of an own ingredient,
  both and neither are 400s, a wrong unit is a 400, PATCH changes the
  amount, the shopping list lists the food, `my-totals` counts it, a
  deleted own ingredient takes its entry with it.
- `AccountEndpointTests` and `ExportFixturesTests`: a round trip with a
  food entry, and v6 imports unchanged.
- `McpEndpointTests` still pins the opt-out list.
- Gates: backend, client.
