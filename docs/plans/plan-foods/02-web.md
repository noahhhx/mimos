# 02. Plan page: pick, scan, show, log a food

**Depends on:** 01

What the person sees.

## Build

- The picker dialog's search offers ingredients (shared and the
  household's own, from `listIngredients`) next to recipes, described as
  the Log page describes them (`Ingredient · 105 kcal each`). Picking one
  plans it with the amount the Log page would start at (one serving, else
  100 g, 100 ml, or 1 piece) for each default diner.
- **Scan a barcode** in the picker, reusing `ScanBarcode`. A found product
  with all four values becomes a household ingredient (`createIngredient`,
  basis from the product's unit) and is planned at its serving size, else
  100 g or 100 ml. A product missing a value opens `NewIngredient`
  prefilled, and saving it plans the result.
- A food entry renders as its name and amount with unit ("Banana, 1",
  "Bagel, 85 g"), no recipe link, and an editable amount (a number input
  with the unit beside it, saved on Enter or blur) in place of the
  servings stepper. Mine and Everyone, diners, and Log behave as for a
  recipe.
- Log on a food entry logs an ad-hoc meal: description from the name and
  the share's amount, nutrition from `measuredTotal` with the ingredient's
  entry from the loaded catalog. The editable share before saving stays.
- The Kitchen home's tonight card names a planned food without a recipe
  link or recipe facts (`apps/web/src/lib/kitchen.ts`, `app/page.tsx`).
- Shared logic (what an entry is, its label, its default amount, its log
  body) lives in a small module under `src/lib/` with `.ts` sibling
  imports, so `node --test` covers it.

## Checks

- `apps/web/test/`: the entry label for a recipe, a piece, and a measured
  food; default amounts by basis and diner count; the ad-hoc log body of a
  shared food; no em dash in any new string.
- Gates: web.
