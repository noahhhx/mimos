# 11. Log and barcode scanning

**Depends on:** 08

The Log page (ADR-0025, and ingredient logging from ADR-0016), with a
native barcode scanner.

## Build

- A day's logs, the week's totals table, add and delete a log (the API has
  no edit).
- What: search recipes and ingredients; an ingredient logs ad hoc with the
  nutrition calculated as `apps/web/src/lib/log-entry.ts` does (cases in
  `libraries/test-cases/`, item 07).
- Log a planned meal with the caller's share from the API, editable before
  saving.
- Scan a barcode: CameraX preview with zxing-cpp
  (`io.github.zxing-cpp:android`) reading EAN-8, EAN-13, UPC-A, and
  UPC-E. The camera permission is asked only when scanning starts, and a
  typed Barcode number is always offered. A found product is logged by
  amount, shown as "From Open Food Facts"; not found and Open Food Facts
  down have their own messages.
- The scanner is a shared service. Planning a scanned food (ADR-0026:
  create a household ingredient from the lookup and plan it) is added to
  the plan here.

## Checks

- `commonTest` over the shared fixtures: barcode validation and the
  ad-hoc nutrition.
- Flows against the Open Food Facts fake, with typed numbers: `log-a-meal`,
  `log-planned-share`, `scan-barcode`, `plan-scanned-food`. Camera
  scanning is checked by hand on a device, or with the emulator's virtual
  scene showing a barcode image if that proves reliable.
- Gates: mobile.
