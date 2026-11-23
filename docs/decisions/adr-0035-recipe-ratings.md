# ADR-0035: Recipe ratings

- Status: Accepted
- Date: 2026-11-18

## Context

People want to mark which of their recipes turned out well. A recipe
already belongs to a household (ADR-0019), so a rating should too: the
household has one opinion of a dish, not one per member.

## Decision

- **One rating per personal recipe, 1 to 5 stars, shared by the
  household.** `recipe.rating` (`V23__recipe_rating.sql`, `smallint`,
  null for unrated, `check (rating between 1 and 5)`). Library recipes stay
  null and cannot be rated. Any member sets or overwrites it, and the last
  write wins; nobody's own rating is kept.
- **Not part of the recipe's saved content.** `RecipeDraft` and
  `RecipeInput` carry no rating, so replacing a recipe keeps it, as it keeps
  pictures (ADR-0023). Rating does not change `updatedAt`, which still means
  "when its fields were last saved". `RecipeRepository.setRating` writes the
  one column.
- **One validation.** `RecipeService.rate(ownerId, recipeId, rating)` is the
  only place the range is checked: 400 outside 1 to 5, 404 for a recipe the
  household does not own, 403 for a library recipe. A null rating clears it.
  Clearing an unrated recipe is not an error.
- **API.** `PUT /api/v1/recipes/{recipeId}/rating` (`rateRecipe`) takes
  `{"stars": 1..5}` and answers the recipe. `DELETE` on the same path
  (`clearRecipeRating`) answers 204. Both are MCP tools. `RecipeSummary` and
  `RecipeDetail` carry an optional `rating`, absent when unrated and always
  absent for library recipes. A body without `stars` is a 400, never a clear.
- **Export version 9.** `ExportedRecipe.rating` is absent when unrated.
  Version 8 had no ratings, so its upgrade step changes nothing;
  `export/v9.json` is the frozen fixture. Import restores a rating through
  `RecipeService.rate`, so it is checked like any other.
- **Web.** The recipe page shows five star buttons on personal recipes
  (`aria-label` "Rate 3 of 5 stars", `aria-pressed` on the current one);
  clicking another star overwrites and clicking the current star clears. The
  recipe list shows the stars read-only on rated recipes.
- **Android app.** The same rule and the same two calls: `StarRating` on a
  personal recipe's page and `RatedStars` in the list, with the tap rule in
  `nextRating` (`apps/mobile/shared/.../recipes/RecipeRating.kt`). Ratings
  need the server, so a rating made offline fails and says so.

## Consequences

- A rating is household data, so leaving a household takes your say with you
  and joining one brings its ratings with the recipes.
- Plugins never see ratings, so their contract is unchanged.
- Sorting or filtering by rating is not built; the column and the list
  response make it cheap to add.
