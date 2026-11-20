# 08b. A new ingredient from the plan's picker

**Depends on:** 08

Split from item 08. Planning a food the catalog lacks, without leaving the
picker (ADR-0016, ADR-0026).

## Build

- When a picker search finds no catalog entry by that name, offer
  "+ Add \"<query>\" as a new ingredient", as the recipe form does.
- Move the recipe form's new-ingredient panel (`NewIngredientPanel`,
  `NewIngredientDraft`, and its save in `RecipeFormModel`) into a shared
  component both screens use, rather than copying it.
- Saving creates the household's ingredient and plans it at
  `newFoodEntry`'s amount, as the web's `planFood` does after a scan.
- Item 11 reuses the same panel for a scanned product missing a value.

## Checks

- `commonTest` for the picker's new-ingredient state.
- Flow: extend `plan-a-week` to plan a new ingredient.
- Gates: mobile.
