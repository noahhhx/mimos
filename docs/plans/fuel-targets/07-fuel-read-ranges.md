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
