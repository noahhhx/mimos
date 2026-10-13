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

## Decision

The owner was asleep, with standing instructions to make decisions as
needed, so this was decided in the session and can be reversed: **show
the hints as suggestions on the Fuel page that a person accepts.** Each
sync keeps the latest weight, body fat, sex, and cycling FTP with the
connection (V19, each kept until a later sync finds a new value), and
`GET /api/v1/me/intervals-icu` returns them as `hints`. Item 19 shows
them. A suggestion costs one read the sync already makes, and an FTP or
weight that changes in intervals.icu reaches the targets only when the
person agrees, which keeps the profile theirs. "Not at all for now" would
have dropped values the owner, a cyclist, already keeps in intervals.icu.

Each sync now makes four reads (events, activities, 30 days of wellness,
and the athlete record): 192 a day per person at the default interval,
against intervals.icu's 5,000 per key.

## Result

Live against the fake (test2, profile without body fat): before
connecting, the basis read `DEFAULT` 15% and 59.5 kg fat-free mass. After
setting yesterday's wellness body fat to 12% and syncing, `hints` read
70.8 kg, 12%, male, FTP 282, and the basis `INTERVALS_ICU` 12% and 61.6
kg. All gates and all 21 scenarios pass.

Land on `main`; set row 18 to `done`.
