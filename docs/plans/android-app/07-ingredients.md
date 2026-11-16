# 07. Household ingredients

**Depends on:** 06

The household's own ingredients, as on the web (ADR-0016, ADR-0019).

## Build

- List, create, edit, and delete own ingredients; the shared catalog is
  read-only and says so (a 403 is not reachable from the UI).
- Deleting an ingredient planned somewhere warns that its plan entries go
  with it (ADR-0026).

## Checks

- Flow: `own-ingredient` (create, use in a recipe line, edit, delete).
- Gates: mobile.
