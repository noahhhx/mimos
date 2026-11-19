# Shared test cases

Rules the Android app repeats from the web are tested from one set of cases
here, so the two cannot drift apart. Each file is plain JSON.

| File | What it pins | Read by |
| ---- | ------------ | ------- |
| `recipe-text.json` | How a recipe's ingredient amounts, counted names, and servings read | `apps/web/test/format.test.ts`; the app's `commonTest` (`RecipeFormatTest`) |
| `recipe-order.json` | The recipes list's sorts, tag filter, and tag list, and the sort keys | `apps/web/test/recipe-order.test.ts` (`node --test`); the app's `commonTest`, through a generated constant (`generateTestCases` in `apps/mobile/shared/build.gradle.kts`) |
| `recipe-input.json` | The recipe form's values from a recipe, the `RecipeInput` and per-row errors it makes, the nutrition estimate's request and rows, and the unit choices | `apps/web/test/recipe-input.test.ts`; the app's `commonTest` (`RecipeDraftTest`) |
| `ingredient-links.json` | What an ingredient line links to as it is typed and picked, and how the form describes the link and why it did not count | `apps/web/test/ingredient-links.test.ts`; the app's `commonTest` (`IngredientLinksTest`) |
| `catalog-match.json` | Searching the ingredient catalog, the entry a line's name suggests, and how an entry reads | `apps/web/test/catalog-match.test.ts`; the app's `commonTest` (`CatalogMatchTest`) |
| `recipe-pictures.json` | The picture limits, the size a picture is shrunk to, and the JPEG qualities tried | `apps/web/test/recipe-pictures.test.ts`; the app's `commonTest` (`PictureDraftTest`) |
| `household.json` | How a household and its members are named, and an invite's web link and the app link the web's join page offers (`io.github.noahhhx.mimos://join?instance=…&token=…`), which the app reads back | `apps/web/test/household.test.ts`; the app's `commonTest` (`InviteLinkTest`) |
| `account-data.json` | The export's file name, which files the import sends, and how an import's report reads | `apps/web/test/account-data.test.ts`; the app's `commonTest` (`AccountDataTest`) |
| `panels.json` | Which of a plugin's week-panel blocks show while its wheel spins, their keys, and whether a wheel has a landing (an unknown block type included) | `apps/web/test/panels.test.ts`; the app's `commonTest` (`PanelRulesTest`) |
| `wheel.json` | The wheel's geometry: segment angles, where it rests and where a spin ends, when two angles are one position, the segments' fills, and the icons' size | `apps/web/test/wheel.test.ts`; the app's `commonTest` (`PanelRulesTest`) |
| `plan.json` | A new meal's servings and diners, toggling a diner, the diners' chip labels, how a planned amount reads, a new planned food's amount, a typed food amount, and the note on meals with unknown nutrition | `apps/web/test/plan-cases.test.ts`; the app's `commonTest` (`PlanRulesTest`) |
| `shopping.json` | The shopping list's aisles and their order, the add-an-item form's units, and the item it makes or what to fix | `apps/web/test/shopping.test.ts`; the app's `commonTest` (`ShoppingRulesTest`) |

A recipe in a case is a sketch: `id` and `title`, with `tags`, `createdAt`,
and `updatedAt` defaulting to none and `2025-01-01T00:00:00Z`. Expected
orders are lists of ids. A catalog entry is a sketch too: its calories, with
the other three values 0. A `RecipeInput` a case expects is as the API is sent
it, with absent fields left out, and names only the fields the case is about;
form rows are as the web holds them, `catalogSlug: ""` meaning a line its
author chose not to count. A change to a rule changes its cases first; both
readers must pass.
