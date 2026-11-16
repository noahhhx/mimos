# 12. Fuel and intervals.icu

**Depends on:** 11

Fuel for the day (ADR-0022): the profile, targets, training sessions, and
the intervals.icu connection.

## Build

- Fuel page: turn targets on, the profile form, the intervals.icu section
  (connect with a key, refresh, disconnect, status, last error, and the
  weight, sex, and FTP suggestions to apply).
- Training sessions added and removed by hand on a day.
- Targets beside planned and logged totals on the plan (item 08's rows)
  and the Log page's week table, all from one `GET /api/v1/me/fuel`.
- The intervals.icu key is typed or pasted, sent once, and never stored on
  the device.

## Checks

- Flows: `fuel-targets` (profile on, a manual session, targets shown),
  `intervals-icu` against the fake.
- Gates: mobile.
