# 07b. The recipe form

**Depends on:** 07

Creating and editing the household's own recipes, as the web's
`recipe-form.tsx`. ADRs 0015, 0016, 0018. Split from item 07.

## Build

- Recipe form: fields, ingredient lines searching the catalog, own
  ingredients, and recipes (`catalogSlug` or `recipeId`), creating a new
  own ingredient inline (as `new-ingredient.tsx`), metric units,
  `nutritionSource`, the live nutrition estimate
  (`estimateRecipeNutrition`), and the API's problems as field errors.
- "New recipe" and "Edit" open the form instead of the web's pages.

## Shared test cases

Move the web's units and estimate cases to `libraries/test-cases/`, read
by both the web's tests and `commonTest`, as item 07 did for sorting.

## Checks

- `commonTest` and the web's tests over the shared fixtures.
- Flow: `create-recipe` (mirrors the web scenario, including an inline new
  ingredient), ending by deleting the recipe it made.
- Gates: mobile, web.
