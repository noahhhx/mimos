# 08. The week's plan

**Depends on:** 06 · **Review**

The plan page (ADR-0028, ADR-0019, ADR-0026, ADR-0022) on a phone and a
tablet.

## Build

- Week navigation by Monday; Mine and Everyone, remembered per install;
  a household of one sees no toggle or chips, as on the web.
- Phone: a list of days, each with its meals; tapping a meal or a day
  opens its inspector as a full screen or sheet. Tablet: the ledger of days
  by meals, with the inspector beside it.
- Add an entry (recipe, or a food from the catalog), change servings and
  diners, remove it; default diners and servings as the web's rules.
- Planned totals (calories in the day summary, all four in the inspector)
  in Mine and for a household of one.
- Log a planned meal: opens the log form with the caller's share (wired
  fully in item 11).
- Fuel for the day rows appear when targets are on (wired in item 12).

## Review

The phone layout is new: the web's ledger does not fit 360 px. Post
screenshots of both layouts in both themes before landing.

## Checks

- `commonTest`: the share rule against the web's `diners.ts` cases (see
  ROADMAP step 19 on the duplicated rule; if the API now answers each
  entry's share, use it and drop the copy).
- Flows: `plan-a-week` (add a recipe and a food, change diners, remove),
  `shared-week` with `test` and `test2` in one household.
- Gates: mobile.
