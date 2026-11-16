# 07. Recipes and pictures

**Depends on:** 06

Recipes as on the web's Recipes pages: the library, the household's own
recipes, the form, pictures, and cooking. ADRs 0005, 0015, 0016, 0018,
0023.

## Build

- Recipes list (library and own, search, the web's sorts in
  `apps/web/src/lib/recipe-order.ts`).
- Recipe detail: ingredients, method, nutrition as the API calculates it,
  "Added by", linked recipes, and cooking: ingredient and step ticks and the
  linked-recipe trail (`lib/cook-trail.ts`, ADR-0018), readable at arm's
  length, the screen kept awake while cooking.
- Recipe form: fields, ingredient lines searching the catalog, own
  ingredients, and recipes (`catalogSlug` or `recipeId`), creating a new
  own ingredient inline (as `new-ingredient.tsx`), metric units,
  `nutritionSource`, the live nutrition estimate
  (`estimateRecipeNutrition`), and the API's problems as field errors.
- Pictures: take a photo or pick one (Android photo picker, no storage
  permission), shrink to 1600 px and re-encode as JPEG without metadata
  (the rule in `apps/web/src/lib/recipe-pictures.ts`), save fields, then
  removals, then uploads, as the web does. Shrinking sits behind the image
  service interface (ADR-0029).
- Delete a recipe.

## Shared test cases

Rules the app repeats from the web (units, the estimate, sorting) are
tested from one set of cases: JSON fixtures in `libraries/test-cases/`,
read by the web's `node --test` suites and by `commonTest`. Move the
web's existing cases there in this item rather than copying them.

## Checks

- `commonTest` and the web's tests over the shared fixtures.
- Flows: `create-recipe` (mirrors the web scenario, including an inline
  new ingredient), `recipe-picture` (a fixture image pushed to the
  emulator), `cook-a-recipe`.
- Gates: mobile, web.
