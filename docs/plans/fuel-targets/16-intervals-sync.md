# 16. Sync on demand

**Step:** 14.5 · **Depends on:** 15

## Build

- `FuelService.replaceSynced(profileId, from, to, sessions)`: deletes and
  inserts that person's `INTERVALS_ICU` sessions in the range in one
  transaction, so a repeated or interrupted sync converges.
- `IntervalsIcuSync.sync(profileId)`: from 7 days back to 14 ahead of
  today by the injected `Clock`, reads events and activities, translates,
  and calls `replaceSynced`; records the last sync time or error.
- `POST /api/v1/me/intervals-icu/sync` (an MCP tool).
- A 401 marks the connection broken. A 429 records when to try again from
  its `Retry-After`, and nothing calls intervals.icu for that person before
  then. The request answers at once with the status; it never waits out a
  `Retry-After`.
- No page load ever calls intervals.icu.

## Checks

- Sync tests against an in-JVM stub: replacement within the range only, a
  repeated sync leaving the same rows, a failure mid-transaction leaving
  the old rows, a 429 with `Retry-After`, a 401 marking the connection
  broken.
- Live against the fake: change a planned workout's duration, sync twice;
  the target follows and the row count is the same after both. Add a
  completed activity for yesterday, sync; yesterday's exercise calories
  come from its `icu_joules` and the planned session no longer counts.
- Load the plan page 20 times; the fake's request log shows no call.
- 20 syncs with `harness api POST /api/v1/me/intervals-icu/sync`; each
  takes at most 2 s against the fake.
- Interrogate the secret handling and the sync (items 14 to 16) with the
  `interrogate` skill; fix or record each finding here.
- Gates: Java, contract.

Land on `main`; set row 16 to `done`.
