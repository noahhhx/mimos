# 07c. Recipe pictures

**Depends on:** 07b

A recipe's pictures, shown and added, as the web's `recipe-pictures.tsx`.
ADR-0023. Split from item 07.

## Build

- Show a recipe's pictures in its detail (`getRecipePicture`, read with
  `.response.readRawBytes()`; "Found along the way").
- Take a photo or pick one (Android photo picker, no storage permission),
  shrink to 1600 px and re-encode as JPEG without metadata (the rule in
  `apps/web/src/lib/recipe-pictures.ts`), save fields, then removals, then
  uploads, as the web does. Shrinking sits behind the image service
  interface (ADR-0029).

## Checks

- `commonTest` for the save order and the shrink rule.
- Flow: `recipe-picture` (a fixture image pushed to the emulator).
- Gates: mobile.
