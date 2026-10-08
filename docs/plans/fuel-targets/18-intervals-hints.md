# 18. Synced body fat and reported calories

**Step:** 14.5 · **Depends on:** 16 · **Review:** owner decision

The two places intervals.icu data feeds the calculator beyond sessions.

## Build

- **Body fat.** Keep the latest wellness `bodyFat` from each sync with the
  connection (a forward migration adds the column). `getMyFuel` passes it
  to `TargetCalculator` as `syncedBodyFatPercent`; a profile's own body
  fat still wins. The basis source reads `INTERVALS_ICU` when it is used.
- **Reported calories.** ADR-0022's step 4: an activity's reported
  `calories`. Add a nullable field to `TrainingSession` (synced sessions
  only, so the export format does not change), the column, the
  translator mapping, and one `EnergyMethod` between `FTP_INTENSITY` and
  `ESTIMATE`.

## Checks

- Translator test: a run with only `calories`.
- `TargetCalculatorTest`: step 4 used when steps 1 to 3 have nothing, and
  never ahead of them; synced body fat used only without a profile value.
- Live against the fake: set wellness body fat to 12% for an account whose
  profile has none and sync. The basis source is `INTERVALS_ICU` and
  fat-free mass uses 12%.
- Gates: Java, contract.

## Review: decision for the owner

ADR-0022 says the athlete record and wellness give "fuel profile hints":
weight, sex, and FTP as well as body fat. Only body fat feeds the
calculator directly. Ask how the others should show: as suggestions on the
Fuel page that a person accepts, or not at all for now. Build what the
owner picks in item 19.

Land on `main`; set row 18 to `done`.
