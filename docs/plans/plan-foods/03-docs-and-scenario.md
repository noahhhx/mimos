# 03. Guide page and scenario

**Depends on:** 02

## Build

- `docs/guide/planning.md`: a section on planning a single food, with the
  search, the barcode scan, the amount, and what the shopping list and
  the planned total do with it. Mention it from `docs/guide/log.md` where
  logging a planned meal is described, and from `docs/guide/your-data.md`
  if the export format is listed there.
- Scenario `tools/harness/scenarios/plan-food.spec.ts` (added to
  `args.test.ts`'s list): a fresh account plans a banana as Monday's
  snack, sees "Banana, 1" and Monday's planned total of its calories from
  the seed, changes the amount to 2 and sees the total double, generates
  the shopping list and finds Banana in it, logs the snack and sees it on
  the Log page. Then, with the camera refused, scans Marmite by its typed
  barcode against the fake Open Food Facts, sees the planned entry at 8 g,
  and finds the new ingredient by searching "marmite" in the picker.

## Checks

- Gates: harness, docs, then the scenario against `harness up`.
