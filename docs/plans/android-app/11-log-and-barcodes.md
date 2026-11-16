# 11. Log and barcode scanning

**Depends on:** 08

The Log page (ADR-0025 and the ingredient logging in ADR-0016), with a
native barcode scanner.

## Build

- A day's logs, the week's totals table, add, edit, and delete a log.
- What: search recipes and ingredients; an ingredient logs ad hoc with the
  nutrition calculated as `apps/web/src/lib/log-entry.ts` does.
- Log a planned meal with the caller's share, editable before saving.
- Scan a barcode: CameraX preview with zxing-cpp reading EAN-8, EAN-13,
  UPC-A, and UPC-E, the camera permission asked only when scanning starts,
  and always a typed Barcode number. A found product is logged by amount,
  shown as "From Open Food Facts"; not found and Open Food Facts down each
  have their own message.
- The scanner is a shared service: the connect screen's QR scan (item 02)
  and planning a scanned food (ADR-0026) reuse it.

## Checks

- `commonTest`: barcode validation and the ad-hoc nutrition maths
  against the web's test cases.
- Flows: `log-a-meal`, `log-planned-share`, and `scan-barcode` using
  the typed number against the Open Food Facts fake. Camera scanning is
  checked by hand on a device, or with the emulator's virtual scene
  showing a barcode image if that proves reliable.
- Gates: mobile.
