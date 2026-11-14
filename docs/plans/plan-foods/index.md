# Planned foods: work items

Built to [ADR-0026](../../decisions/adr-0026-planned-foods.md). A person
plans a single food (a banana, a scanned bagel) in a meal slot without
making a recipe for it. It shows in the plan, counts in the day's planned
totals and Fuel for the day, lands on the shopping list, and can be
logged.

## How to work an item

1. Take the first `todo` item whose dependencies are `done`.
2. Read AGENTS.md, ADR-0026, and the item before writing code.
3. Build what the item says. If it turns out bigger than one session, split
   it here first.
4. Run the item's checks, plus the gates below for what you touched.
5. Land on `feat/plan-foods`: conventional commits, present tense, each
   commit builds. In the item's last commit, set its row below to `done`
   with the commit's short SHA.
6. Anything out of the item's scope goes in "Found along the way".

## Items

| #  | Item | Depends on | Status |
| -- | ---- | ---------- | ------ |
| 01 | [Domain, schema, API, export](01-backend.md) | – | done (a2832ff) |
| 02 | [Plan page: pick, scan, show, log a food](02-web.md) | 01 | done (3399e83) |
| 03 | [Guide page and scenario](03-docs-and-scenario.md) | 02 | todo |

## Gates

- Backend: `./mvnw -B verify`.
- Client: `npm run generate -w @mimos/api-client` leaves no diff.
- Web: `npm run typecheck -w @mimos/web`, `npm test -w @mimos/web`,
  `npm run build -w @mimos/web`.
- Harness: `npm run typecheck -w @mimos/harness`, `npm test -w @mimos/harness`,
  then `devenv shell -- harness up` and the scenario.
- Docs: `devenv shell -- mkdocs build --strict`.

## Found along the way

- Item 01's endpoint tests for planned foods are in
  `PlannedFoodsEndpointTests` (fresh users per test), not in
  `MealPlansEndpointTests`, which still uses the shared `test` account.
- `npm run typecheck -w @mimos/web` passes against the regenerated client.
  `MealPlanEntry.recipeId` and `recipeTitle` are now optional, so item 02
  has to branch on `food` where the web reads them (the Kitchen tonight
  card, the plan page, the Log page's "log a planned meal").
- Entries in one slot (same date and meal type) are ordered by random id,
  so two planned items in the same slot have no stable order. Existing
  behavior, now easier to hit with foods; item 02 or later may want a
  stable tiebreak.
- The plan page's picker is still `role="dialog"` named "Pick a recipe",
  because the `shared-week` and `planned-totals` scenarios find it by that
  name. Item 03 may rename it ("Pick a meal") together with those two
  scenarios. The guide's planning page (`docs/guide/planning.md`) still
  says "Search your recipes and the library".
- Scanning a product whose name, basis, and four values match one of the
  household's own ingredients reuses it instead of creating a second one
  (`existingIngredient`, `apps/web/src/lib/planned-item.ts`), so scanning
  the same bagel on two days adds one ingredient. Two different products
  with the same name and values also collapse into one; that is harmless
  for planning.
- The plan page lists a food as its name with a number input and unit
  ("Banana", `2` pieces); the entry's one-line label ("Banana, 2") is for
  the Kitchen home. The Log page's description of the same food is
  "Banana, 2 pieces", the wording the Log page already used for
  ingredients.
- A scanned product missing a value opens `NewIngredient` inside the
  picker, started from what Open Food Facts gave (`initialNutrition`).
  Cancelling it leaves the picker open; nothing is saved.
- Same-slot order is still random-id order (see above). With two foods in
  one slot it shows plainly in the UI (Banana between two Nutellas, in the
  order the ids fell), so a stable tiebreak (created time) is now worth
  doing.
