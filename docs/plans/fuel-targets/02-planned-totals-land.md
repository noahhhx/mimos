# 02. Check and land planned totals

**Step:** 14.1 · **Depends on:** 01 · **Review:** yes

## Live checks

Run against `harness up --debug` at the branch head, each with a fresh
account from the scenario fixtures' `register()` unless it says otherwise.
Save screenshots at 1280px wide unless stated.

1. A household of one plans a breakfast at 1 serving and a dinner at 2 on
   one day. The day's calories and macros equal the hand sum of per-serving
   values times servings, to 0.1 g. Save `solo-day.png`.
2. Two fresh accounts join one household by invite (as `shared-week.spec.ts`
   does) and plan a dinner at 4 servings for both and a lunch for one. Each
   Mine view shows half the dinner; only the luncher's total has the lunch.
   Everyone shows no total. Save `shared-mine.png`.
3. A personal recipe with blank calories but known protein: the day shows
   the note and the protein, and shows no `0 kcal`.
4. Change a meal's servings with minus and plus, then remove a diner chip.
   The total follows each change without a reload.
5. `harness api POST /mcp` with a JSON-RPC `tools/list` body lists
   `getMyPlannedTotals`, and its description states the servings ÷ diners
   rule.
6. The plan page at 390px wide in light and dark themes with a full day:
   the totals wrap inside the card and nothing scrolls sideways
   (`document.documentElement.scrollWidth` equals the viewport width).
   Save `mobile-light.png` and `mobile-dark.png`.

Isolation (`test2` sees zeros for `test`'s week) and the non-Monday 400 are
covered by `PlannedTotalsEndpointTests`; no live check needed.

## Perf

Seed a week with 28 entries. Measure `GET /api/v1/plans/{monday}` on `main`
first, then at the head, plus `GET /api/v1/plans/{monday}/my-totals` at the
head (see "Measuring a read" in the index).

- The head's plan read p95 is within 10% plus 5 ms of `main`'s.
- `my-totals` p95 is at most 100 ms.

## Review

Post `solo-day.png`, `shared-mine.png`, and the two mobile screenshots.
Point out that a household of one plans 2 servings by default, so its
Planned line counts 2 servings eaten, as Log does today; ADR-0022 accepts
this. Wait for the go.

## Land

Rebase onto `main` if it moved, run the gates again if it did, fast-forward
`main`, and set rows 01 and 02 to `done`.
