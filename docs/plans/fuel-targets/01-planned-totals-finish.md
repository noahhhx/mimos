# 01. Finish planned totals

**Step:** 14.1 · **Depends on:** nothing · **Start from:** branch
`ft1-planned-totals` at `15bc002`, worktree `.claude/worktrees/ft1` with
uncommitted edits

## What is already built

Four commits on the branch, on `main` `93556b1`:

- `MealPlanService.plannedTotals(ownerId, weekStart, dinerProfileId)`
  returns seven `PlannedDayTotal(date, total, meals, mealsWithoutNutrition)`.
  The arithmetic is the pure `PlannedDayTotal.forWeek`.
- Share rule: only meals the person eats count. A meal they eat alone counts
  every serving. A shared meal counts `max(0.1, round1(servings / diners))`.
  Each value is rounded to 0.1 per meal, then summed. This is what the plan
  page's Log records, so a planned day equals the same day logged.
- A value a recipe lacks adds nothing, and the recipe's known values still
  count, as Log records them. A meal missing any of the four values counts
  in `mealsWithoutNutrition`.
- `GET /api/v1/plans/{startDate}/my-totals` (`getMyPlannedTotals`); a
  non-Monday is a 400. The description states the share rule and that the
  totals are the caller's alone.
- Plan page: in the Mine view, and always in a household of one, a day with
  meals you eat shows a **Planned** line, such as
  `1200 kcal · 60 g protein · 120 g carbs · 30 g fat`, plus a note when a
  meal has unknown values. The Everyone view shows no totals. Totals reload
  with the plan after every write.
- `PlannedTotalsEndpointTests`, `PlannedDayTotalTest`,
  `apps/web/test/planned-totals.test.ts`, scenario `planned-totals`, and
  "Your planned total" in `docs/guide/planning.md` with `plan-totals.png`.

## What is left: round 2 review findings

The uncommitted edits in `.claude/worktrees/ft1` change the data shape:
`NutritionTotal` is deleted, and `PlannedDayTotal.total` is core-recipes'
`Nutrition`, whose values are nullable. A day's value is absent when no meal
that day knows it, else the sum of the known values. The contract's day
`total` refers to the existing `Nutrition` schema.

1. **Done in the edits.** A test checks a meal is flagged when a value
   other than calories is missing (core and endpoint tests, recipes lacking
   fat and protein). A mutant checking only calories failed them.
2. **Done in the edits.** The contract no longer says every value is
   known; `NutritionTotal` is gone.
3. **Check.** `planned-totals.spec.ts` width sweep passed even if
   `.planned-total span` matched nothing. It must assert the expected span
   count first.
4. **Check.** `docs/guide/planning.md` must say a day where no meal has a
   *known value* shows only the note.
5. **Done in the edits.** A day whose only meal lacks calories read
   `0 kcal`. Now the API leaves the value absent and the page omits it.
   The web's old "every meal flagged and all totals 0" heuristic is gone.

## Do

- Review the uncommitted diff (`git -C .claude/worktrees/ft1 diff`), finish
  3 and 4, and fold the fixes into the existing commits so each still
  builds (or add one `fix` commit per concern).
- Update the contract description of `getMyPlannedTotals` and the
  regenerated client to match the nullable total.
- If the AGENTS.md Households row states the planned-totals rule, make it
  match.

## Checks

- Gates: Java, contract, web, docs, harness.
- `devenv shell -- harness up --debug`, then `harness ui planned-totals`,
  then every scenario.

When the gates pass, set row 01 in the index to `on branch` with the head
SHA.
