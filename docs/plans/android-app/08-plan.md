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
- Add an entry (a recipe, or a food from the catalog, or a new own
  ingredient inline), change servings and diners, remove it; default
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
