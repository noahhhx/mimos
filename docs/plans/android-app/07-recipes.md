# 07. Recipes: browse, read, and cook

**Depends on:** 06

Recipes as on the web's Recipes pages, read-only for now: the library, the
household's own recipes, a recipe's detail, cooking it, and deleting it.
ADRs 0005, 0015, 0016, 0018. The form is item
[07b](07b-recipe-form.md) and pictures are item
[07c](07c-recipe-pictures.md); this item was split from one that held all
three.

## Build

- Recipes list (library and own, search, the web's sorts and tag filter in
  `apps/web/src/lib/recipe-order.ts`, the sort remembered on the device).
- Recipe detail: ingredients, method, nutrition per serving as the API
  calculates it, "Added by", linked recipes, and cooking: ingredient and
  step ticks and the linked-recipe trail (`lib/cook-trail.ts`, ADR-0018),
  readable at arm's length, the screen kept awake while a recipe is open.
  Ticks last while the app is open, as the web's last for the tab.
- Library recipes open in the app, read-only, with a way to their public
  page. Until item 07b, "New recipe" and "Edit" open the web's pages.
- Delete a recipe, after a confirmation.

## Shared test cases

Rules the app repeats from the web are tested from one set of cases: JSON
fixtures in `libraries/test-cases/`, read by the web's `node --test` suites
and by `commonTest`. This item moves the recipe sort cases there; 07b
moves the units and the estimate.

## Checks

- `commonTest` and the web's tests over the shared fixtures.
- Flow: `cook-a-recipe` (a library recipe: tick an ingredient and a step,
  leave, come back, the ticks remain).
- Gates: mobile, web.
