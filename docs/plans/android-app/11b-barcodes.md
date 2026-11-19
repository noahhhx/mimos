# 11b. Barcode scanning

**Depends on:** 11

Packaged food by barcode (ADR-0025, ADR-0026), on the Log tab and in the
plan's picker. Split from item 11.

## Build

- Scan a barcode: CameraX preview with zxing-cpp
  (`io.github.zxing-cpp:android`) reading EAN-8, EAN-13, UPC-A, and
  UPC-E. The camera permission is asked only when scanning starts, and a
  typed Barcode number is always offered. A found product is logged by
  amount, shown as "From Open Food Facts"; not found and Open Food Facts
  down have their own messages.
- Declaring the `CAMERA` permission makes the recipe form's "Take a photo"
  need it too (item 07c's note), so that asks for it first.
- The scanner is a shared service. Planning a scanned food (ADR-0026:
  create a household ingredient from the lookup and plan it) is added to
  the plan here.

## Checks

- `commonTest` over the shared fixtures: barcode validation, a product's
  name and serving, and the lookup's messages.
- Flows against the Open Food Facts fake, with typed numbers:
  `scan-barcode`, `plan-scanned-food`. Camera scanning is checked by hand
  on a device, or with the emulator's virtual scene showing a barcode image
  if that proves reliable.
- Gates: mobile.
