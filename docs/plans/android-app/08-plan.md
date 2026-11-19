# 08. The week's plan

**Depends on:** 07 · **Review**

The plan page (ADR-0028, ADR-0019, ADR-0026, ADR-0022) on a phone.

## Build

- Backend first: the plan entry answers the caller's share
  (`servings / diners`, to 0.1) so neither client computes it. The web
  switches to it and `shareOf` in `apps/web/src/lib/diners.ts` is
  deleted (ROADMAP step 19).
- Week navigation by Monday; Mine and Everyone, remembered per install; a
  household of one sees no toggle or chips, as on the web.
- A list of days, each with its meals; tapping a meal or a day opens its
  inspector as a full screen. The tablet ledger is deferred (index,
  "Later").
- Add an entry (a recipe, or a food from the catalog), change servings
  and diners, remove it; default
  diners and servings as the web's rules. Scanning a barcode while planning
  arrives with the scanner in item 11.
- Planned totals (calories in the day summary, all four in the inspector)
  in Mine and for a household of one.
- Log a planned meal: opens the log form with the share (wired in item 11).
- Fuel for the day rows when targets are on (wired in item 12).

## Review

The phone layout is new: the web's ledger does not fit 360 px. Post
screenshots in both themes before landing.

## Checks

- `MealPlansEndpointTests`: the share for one diner, several, and a
  caller who is not a diner.
- Flows: `plan-a-week` (add a recipe and a food, change diners, remove),
  `shared-week` (`harness household join` puts two fresh accounts in one
  household first).
- Gates: backend, contract, web, mobile.

## Split

A new own ingredient planned inline moved to item 08b, so this item could
land in one session.

## Decided while building

- Each plan entry answers `yourShare` (the caller's share, present only
  while they eat it), from `PlannedMeal.shareOf` in core-planning. The web
  reads it and its `shareOf` is gone. The tests are in
  `MealPlanDinersEndpointTests`, which has the household helpers.
- The rules (default servings and diners, toggling a diner, chip labels,
  amounts, a new food's amount, the unknown-nutrition note) are ports in
  `plan/PlanRules.kt`, tested from `libraries/test-cases/plan.json`, which
  the web's `plan-cases.test.ts` reads too.
- The week is a list of days. Each day has a heading (with the planned
  calories, tappable for the day, while totals show) and its four meals,
  each with its entries and a "+" that opens the picker. A meal eaten by
  others only is muted in Everyone, as the web's `others`.
- Totals come from `getMyPlannedTotals`; item 12 switches the read to
  `getMyFuel` when it adds targets and training.
- The meal inspector has "Open recipe", which shows the recipe in the
  Recipes tab (the web links the title). Log arrives with item 11.
- A food's amount saves from the keyboard's Done or a Save button that
  shows once the typed amount differs and is valid; the web saves on blur.
- `Button` takes `pressed`, a toggle with checked state, for the diner
  chips (the web's `aria-pressed`).
- A flow whose Maestro config has `tags: [household]` signs in as one of
  two fresh accounts in one household (`harness mobile flow`), with the
  other's name as `MIMOS_PARTNER`.
