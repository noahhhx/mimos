# 14. Account, settings, export and import

**Depends on:** 05

Everything in the web's profile menu not covered elsewhere (ADR-0011).

## Build

- Profile: name and what the web shows.
- Theme toggle (light, dark), the connected instance and its version, Change
  instance (signs out), Sign out, the app's version and licence.
- Export: `GET /api/v1/account/export` saved through the Storage Access
  Framework (the person picks where). Import: pick a file, show the API's
  409 for a non-empty household as the web words it.
- Use the API's copy for errors; no new strings an agent would guess.

## Checks

- Flows: `export-import` (export as `test`, import into a fresh account),
  `theme-toggle` (the choice survives a restart and reaches Keycloak's
  pages).
- Gates: mobile.
