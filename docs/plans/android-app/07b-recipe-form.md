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

## Decided while building

- The form's rules are ports in `recipes/form/` (`RecipeDraft`,
  `IngredientLinks`, `CatalogMatch`), tested from `recipe-input.json`,
  `ingredient-links.json`, and `catalog-match.json`. A row's link is a sealed
  `RowLink` (`Unlinked`, `Entry`, `Recipe`, `DontCount`), so a row that holds
  both links, which the web allows and resolves, cannot be written.
- The app checks what the web leaves to the browser's input constraints:
  servings a whole number from 1 to 50, times from 0 to 1440, and typed
  nutrition of 0 or more, each with its own message at the top of the form.
- The estimate is asked again when what it is sent or the rows it came from
  change, not on what it is sent alone, so a "Not counted" hint never sits
  on the wrong row. While it waits, the last answer stays on screen.
- The form waits for the catalog and the household's recipes before it
  shows, and says so if they fail to load, where the web shows the form
  with empty searches.
- The form has no "done" state: a save navigates (a new recipe opens, an
  edit returns to its recipe and reloads it), and leaving the form closes
  it. `Edit` keeps the recipe's cook trail to return to.
- The flow also writes a second recipe that uses the first, follows it from
  the detail, and saves it through Edit, which item 07 could not test.
