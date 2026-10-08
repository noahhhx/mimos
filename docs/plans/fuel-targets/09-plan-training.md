# 09. Training on the plan page

**Step:** 14.4 · **Depends on:** 08 · **Review:** yes

## Build

- Where Fuel for the day shows (Mine view or a household of one, targets
  on), each day card lists that day's sessions: sport, duration,
  intensity, and planned or completed.
- A way to add a manual session from the day card with sport, duration,
  and intensity (`POST /api/v1/me/training`), and a way to remove one
  (`DELETE`). Until item 19 every session is manual.
- After an add or remove, the day's target updates without a reload.
- Copy and formatting in `src/lib/fuel.ts` with unit tests; same copy rules
  as item 08.

## Checks

- Extend `fuel-targets.spec.ts`: add a 90-minute ENDURANCE ride to Tuesday;
  Tuesday's target rises by that ride's exercise calories (the basis's
  `SessionEnergy`) and the carb range follows the new load band, without a
  reload. Remove it; Tuesday returns to its earlier target.
- Gates: web, harness, then every scenario.

## Live checks

1. The add and remove above, by hand. Save `add-session.png`.
2. A 0-minute or 1441-minute session shows the API's refusal as a plain
   error under the field, and nothing is added.
3. 390px in light and dark: the session form fits and works with one
   thumb; nothing scrolls sideways. Save `training-mobile.png`.

## Review

Post the screenshots and wait for the go. Land on `main`; set row 09 to
`done`.
