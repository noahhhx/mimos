# 15. Account, settings, export and import

**Depends on:** 06

Everything in the web's profile menu not covered elsewhere (ADR-0011).

## Build

- Profile: what the web's Account page shows.
- Theme (light, dark), the connected instance and its version, Change
  instance (signs out), Sign out, the app's version and licences.
- Export: `GET /api/v1/account/export` saved through the Storage Access
  Framework (the person picks where). Restore: pick a file, and show the
  API's 409 for a non-empty account as the web words it.

## Checks

- Flows: `export-import` (export as one account, import into a fresh
  one), `theme-toggle` (the choice survives a restart and reaches
  Keycloak's pages).
- Gates: mobile.
