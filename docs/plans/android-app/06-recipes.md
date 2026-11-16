# 06. Recipes and pictures

**Depends on:** 05

Recipes as on the web's Recipes pages: the library, the household's own
recipes, the form, and pictures. ADRs 0005, 0015, 0016, 0018, 0023.

## Build

- Recipes list (library and own, with search), recipe detail (ingredients,
  method, nutrition as the API calculates it, "Added by", linked recipes),
  readable at arm's length in the kitchen (large type, screen kept awake
  while a recipe is open).
- Recipe form: fields, ingredient lines searching the catalog, own
  ingredients, and recipes (`catalogSlug` or `recipeId`), metric units,
  `nutritionSource`, validation messages from the API's problems.
- Pictures: take a photo or pick from the gallery (Android photo picker,
  no storage permission), shrink to 1600 px and re-encode as JPEG without
  metadata (the web's rule in `apps/web/src/lib/recipe-pictures.ts`),
  upload after the fields save, show with the API's immutable caching.
  Shrinking sits behind the image service interface (ADR-0029).
- Delete a recipe.

## Checks

- `commonTest`: line parsing and unit handling shared with the web's
  rules, against the same cases as the web's tests.
- Flows: `create-recipe` (mirrors the web scenario), `recipe-picture`
  (upload from a fixture image pushed to the emulator).
- Gates: mobile.
