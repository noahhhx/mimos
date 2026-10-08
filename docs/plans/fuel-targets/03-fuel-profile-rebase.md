# 03. Rebase the fuel profile onto main

**Step:** 14.2 · **Depends on:** 02 · **Start from:** branch
`ft2-fuel-profile` at `5974023` (three commits on `main` `93556b1`)

## What is already built

- `core/core-fueling`, depending on no other Mimos module.
  `Goal` carries its energy-availability level and default protein as
  fields: `FUEL(45, 1.6)`, `GRADUAL_LOSS(40, 2.0)`, `FASTER_LOSS(35, 2.2)`,
  `GAIN(50, 1.8)`, so no code branches on the goal.
- `FuelProfile(profileId, enabled, weightKg, sex, bodyFatPercent?,
  ftpWatts?, goal, proteinGPerKg)` rejects weight outside 30–250, body fat
  outside 3–60, FTP outside 50–600, and protein outside 1.2–3.0.
- `TrainingSession(id, profileId, date, source, kind, sport,
  durationMinutes, workKj?, intensity?, externalId?)`. Source `MANUAL` or
  `INTERVALS_ICU`; kind `PLANNED` or `COMPLETED`; sport `RIDE`, `RUN`,
  `SWIM`, `STRENGTH`, `OTHER`; intensity `EASY`, `ENDURANCE`, `HARD`.
  Duration 1–1440 minutes, work finite and at most 50000 kJ, dates
  1900-01-01 to 9999-12-31. Manual sessions require intensity and default
  to `PLANNED`.
- `FuelService`: profile read and save, sessions by range (at most 62 days
  counting both ends), add and delete manual sessions; deleting a synced
  session is a 400.
- `V15__fueling.sql`: `fuel_profile` keyed by `profile_id`;
  `training_session` indexed on `(profile_id, session_date)`, unique
  `(profile_id, source, external_id)` where `external_id` is set. Both
  cascade from `user_profile`.
- `GET`/`PUT /api/v1/me/fuel-profile`, `GET`/`POST /api/v1/me/training`,
  `GET`/`DELETE /api/v1/me/training/{sessionId}`. A profile never saved
  reads as targets off, goal `FUEL`, protein 1.6, never a 404. A 400 names
  every refused value at once. All six are MCP tools.
- Export format 5: `fuelProfile` (absent when none) and `trainingSessions`
  (manual only). The v4 to v5 `ExportUpgrader` step, frozen fixture
  `export/v5.json`. An account with a fuel profile or a session is not
  empty for import (409). `ImportReport` gains `fuelProfile` and
  `trainingSessions`.
- `/app/fuel`, linked from the profile menu after Plugins: the on and off
  switch, weight, sex, body fat, FTP, goal, protein (which follows the
  goal's default when the goal changes). Per-field errors in `--danger`,
  parsed from the 400's detail by `src/lib/fuel-fields.ts`.
- The Your data page, invite page, Leave confirmation, and Household page
  say fuel settings and training are per person and stay yours.
- Scenario `fuel-profile`; docs in AGENTS.md, `docs/guide/index.md`,
  `your-data.md`, `household.md`, `docs/harness/usage.md`.

All nine round 1 findings are fixed in `5974023`, each with a test that
failed before the fix. Nobody has re-checked the head since.

## Do

- Rebase the three commits onto `main` (which now has 14.1).
- Expect conflicts in `contracts/api/openapi.yaml` (both moved
  `info.version` to 0.12.0; make it 0.13.0), the generated client
  (regenerate rather than merge), `tools/harness/test/args.test.ts`,
  `docs/harness/usage.md`, and AGENTS.md.
- Keep each commit building.

## Checks

- Gates: all of them.
- `harness up --debug`, then `harness ui fuel-profile`, `household`,
  `account-data`, and `planned-totals`, then every scenario.

Set row 03 to `on branch` with the new head SHA.
