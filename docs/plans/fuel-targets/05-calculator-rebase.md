# 05. Rebase the target calculator onto main

**Step:** 14.3 · **Depends on:** 04 · **Start from:** branch
`ft3-fuel-targets`, commits `48d84ea` (calculator) and `40ab9dc` (read
endpoint), which sit on older FT1 and FT2 heads

## What is already built

- `core-fueling` package `targets` (not `target`: `.gitignore` and
  `.dockerignore` both drop any directory named `target`).
  `TargetCalculator.target(date, profile, day, syncedBodyFatPercent)` is a
  pure function with no clock, I/O, or Spring. It returns a `DailyTarget`
  (calories, protein, carbs, fat) and a `TargetBasis`: level, body fat and
  its source (`PROFILE`, `INTERVALS_ICU`, `DEFAULT`), fat-free mass, one
  `SessionEnergy` per counted session with its `EnergyMethod`, the
  `LoadBand`, and `carbsLeftBand`.
- `LoadBand` carries its minute threshold and carb range in g/kg:
  `LIGHT(0, 3, 5)`, `MODERATE(1, 5, 7)`, `HIGH(75, 6, 10)`,
  `VERY_HIGH(240, 8, 12)`, chosen by the day's total minutes.
- Exercise energy per session in ADR-0022's order: completed work, planned
  work, FTP × intensity factor × duration (rides only), then a MET estimate
  by intensity and weight. Step 4, an activity's reported calories, waits
  for item 18. On a day with a completed session, planned ones do not
  count.
- Macros: calories never move. Protein is g/kg × weight. Carbs take what is
  left after protein and 25% fat, clamped to the band. Fat takes the rest.
  When fat would leave 20–35% of calories, carbs leave the band by the
  least amount that brings it back.
- `GET /api/v1/me/fuel?from&to` (`getMyFuel`), every day of an inclusive
  range of at most 62 days. Each day has `target` (absent while targets are
  off), `planned` (14.1's `plannedTotals`), `logged`
  (`MealLogService.summarize`), and `sessions`. Targets in whole kcal and
  grams; fat-free mass to 0.1 kg. The description says a target is
  information, not a score. One sentence on fuel in the MCP `instructions`.
- `TargetCalculatorTest`: the golden week for all four goals to 1 kcal and
  1 g; each energy step; completed replacing planned; body-fat sources;
  load-band edges; a property test over 1,225,728 days that fat stays in
  20–35% and calories equal level × fat-free mass + exercise.
  `FuelEndpointTests` covers the sketch week over HTTP, targets off, planned
  equal to logged, the 62/63-day range, and two household members.
- Interrogated by three models before code-ready.

## Do

- Rebase the two commits onto `main` (now 14.1 and 14.2). Drop the copies
  of the older FT1 and FT2 commits.
- 14.1's final shape changed under it: `NutritionTotal` is gone, and a
  planned day's `total` is a nullable `Nutrition`. Make `FuelController`'s
  `planned` follow, and say in the contract that an absent value is
  unknown, not zero.
- Contract version 0.13.0 to 0.14.0; regenerate the client.
- `FuelService.sessions` already rejects 63 days on `main`; drop any note
  or workaround that assumed it did not.

## Checks

- Gates: Java, contract, web (client only), docs.
- `harness up --debug`, every scenario.

Set row 05 to `on branch` with the new head SHA.
