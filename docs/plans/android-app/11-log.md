# 11. Log

**Depends on:** 08

The Log page (ADR-0016's ingredient logging, ADR-0019's shares). Barcodes
are item 11b.

## Build

- A day's logs, the week's totals table, add and delete a log (the API has
  no edit).
- What: search recipes and ingredients; an ingredient logs ad hoc with the
  nutrition calculated as `apps/web/src/lib/log-entry.ts` does (cases in
  `libraries/test-cases/`, item 07).
- Log a planned meal from the plan's meal inspector, as the web's plan page
  does: alone in one tap, a shared meal with the caller's share from the
  API, editable before saving.
- The Fuel for the day rows in the totals table are item 12.

## Checks

- `commonTest` over the shared fixtures: the ad-hoc nutrition, the log
  request, and the What search.
- Flows: `log-a-meal`, `log-planned-share`.
- Gates: mobile, web (the web's log tests read the shared cases).
