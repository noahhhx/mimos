# Shared test cases

Rules the Android app repeats from the web are tested from one set of cases
here, so the two cannot drift apart. Each file is plain JSON.

| File | What it pins | Read by |
| ---- | ------------ | ------- |
| `recipe-text.json` | How a recipe's ingredient amounts, counted names, and servings read | `apps/web/test/format.test.ts`; the app's `commonTest` (`RecipeFormatTest`) |
| `recipe-order.json` | The recipes list's sorts, tag filter, and tag list, and the sort keys | `apps/web/test/recipe-order.test.ts` (`node --test`); the app's `commonTest`, through a generated constant (`generateTestCases` in `apps/mobile/shared/build.gradle.kts`) |

A recipe in a case is a sketch: `id` and `title`, with `tags`, `createdAt`,
and `updatedAt` defaulting to none and `2025-01-01T00:00:00Z`. Expected
orders are lists of ids. A change to a rule changes its cases first; both
readers must pass.
