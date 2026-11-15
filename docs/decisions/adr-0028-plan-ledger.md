# ADR-0028: The plan page is a ledger

- Status: Accepted
- Date: 2026-11-14

## Context

The plan page showed each day as a card of seven labelled sections:
Training, Fuel for the day, Planned, and the four meal slots. Every
planned meal carried its own row of controls: −, +, Log, ✕, and the
household's diner chips. A training week with targets on came to about
sixty buttons, each as prominent as the meal names. Fuel for the day and
Planned were two four-part strings, one above the other, to compare by
reading both. The seven cards wrapped into rows of three of different
heights, so nothing lined up across days, and plugin panels pushed the
week down the page.

Four directions were mocked up (`docs/design/mockups/plan-redesign/`): a
ledger, one day at a time, separate Plan and Eat modes, and days drawn as
columns sized by their fuel target. The owner chose the ledger.

## Decision

- **A table of days by meals.** Days run across (today marked at the
  top), the four slots run down, and each cell shows only what is planned:
  its name and amount, plus the diners' initials in a shared household.
  With fuel on (Mine, or a household of one), a **Planned** row shows each
  day's planned calories, "of N kcal" against Fuel for the day while
  targets are on, and a **Training** row shows each day's sessions in
  short form ("Ride 90 min", or Rest) while targets are on.
- **Controls live in an inspector.** Selecting a meal opens servings or
  amount, who eats it, Log (with the share form), and Remove beside the
  table. Selecting a day, by its head or its Training or Planned cell,
  opens its training (add and remove) and a two-row table of Fuel for the
  day and Planned under the same columns, with the unknown-nutrition
  note. Escape, ✕, or selecting the same thing again closes it and gives
  focus back to the cell. Selection is page state, never stored; a meal
  that is removed or hidden by the view closes it.
- **Empty slots are quiet.** A slot's **+** shows when the cell is
  hovered or focused (always on touch screens), and opens the picker as
  before.
- **Plugins sit in the rail under the inspector**, so a wheel's landed
  spin is not lost when something is selected, and they no longer push the
  week down.
- **The plan page is wider than the reading column.** Its `main` and the
  site header grow to 1440 px (`:has(.plan-ledger)` in `globals.css`); every
  other page keeps 1080 px. From 1100 px up, the rail is a 340 px column
  beside the table. Below that, the rail stacks under the table and the
  inspector becomes a sheet fixed to the bottom of the screen, and below
  640 px the table scrolls sideways with its slot names kept in view.
- **No new API.** The page reads what it read before
  (`getMealPlan`, `getMyFuel`, suggestions, panels, household). Planned
  meals carry no "logged" mark, since nothing links a log to its plan
  entry.

## Consequences

- The week fits one screen on a laptop, and the page holds about one
  control per meal until something is selected.
- Every change to a meal is one click more than before.
- The inspector's labels keep the old controls' accessible names
  ("More servings", "Amount of Banana", "Who eats it", "Remove …",
  "Add Dinner for …", "Training for …"), so agents and scenarios find
  them as before; harness scenarios reach the page through
  `planLedger(page)` in `tools/harness/scenarios/fixtures.ts`.
- Fuel stays information, not a score (ADR-0022): figures only, no
  colours or words for over or under.
- A "logged" mark on planned meals would need the API to link a log to
  its plan entry; that is a separate decision.
