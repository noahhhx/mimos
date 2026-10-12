# 07. Tidy the fuel read's ranges

**Step:** 14.3 · **Depends on:** 06

Three leftovers the calculator work found in the code it built on.

## Build

- **Planned totals for a range.** A 62-day `getMyFuel` calls
  `MealPlanService.plannedTotals` once per week it touches, up to ten
  times. Add a range form in core-planning (`plannedTotals(ownerId, from,
  to, dinerProfileId)`) that loads the entries and recipes once, and have
  the weekly form and `FuelController` use it.
- **One mapping.** `PlannedDayTotal`'s API mapping is in both
  `MealPlansController` and `FuelController`. Keep one.
- **62 days in the log.** `MealLogService`'s range check admits 63 days
  counting both ends while its message says 62. Make 62 inclusive days the
  limit, as `FuelService` and `getMyFuel` have it, and test 62 and 63.

## Checks

- Gates: Java.
- A test that a range spanning three weeks equals the three weekly reads.
- 62-day `GET /api/v1/me/fuel` p95 before and after; it should not get
  slower, and the drop is worth recording here.

Land on `main`; set row 07 to `done`.

## Result

Landed as `e6dec82` (log range), `bcf227c` (one mapping), and `1fc66f9`
(range form). `forWeek` is gone; the weekly `plannedTotals` checks its
Monday and calls the range form. The range query also bounds the plans'
Mondays to the six days before `from` through `to`, so the
`(household_id, start_date)` index narrows it.

Measured against the `harness up --debug` stack, with `test` holding three
planned meals a day over the 62 days from 2031-03-05, reading
`/api/v1/me/fuel?from=2031-03-05&to=2031-05-05` (three runs of 200 after
20 warm-up requests):

| | SQL statements per read | p95 by run (ms) | p50 by run (ms) |
| - | - | - | - |
| Before (`f1d2d19`) | 64 | 9.9, 8.8, 7.7 | 6.8, 5.6, 4.1 |
| After (`1fc66f9`) | 10 | 10.0, 7.5, 6.0 | 6.9, 5.9, 4.4 |

The statement count is the dependable result: ten weekly repeats of six
statements (plan entries, then the five that load recipes) became one of
each. Latency did not get slower. Each run is faster than the one before
it, so the JVM is still warming, and the p95 drop (7.7 to 6.0 ms in the
third run) is about the size of that noise. On a local database a
statement costs well under a millisecond, so the saving grows with the
database's distance from the API more than with the range. The 62-day
response was byte-identical before and after (`jq -S`), and all 19
scenarios pass.
