# 17. Scheduled sync

**Step:** 14.5 · **Depends on:** 16

## Build

- A scheduled job syncs each connected person in turn every
  `mimos.intervals-icu.sync-interval` (default 30 minutes). It skips broken
  connections and people still inside a `Retry-After`. One person's
  failure never stops the others.
- Off when the feature is off.

## Checks

- A test that drives the job with an injected clock: connected people
  sync, broken and waiting ones do not, a failure moves on.
- Live: start the stack with a 1-minute interval, change a workout in the
  fake, and wait. The target changes within 2 minutes with no Refresh.
- Gates: Java.

## Result

`IntervalsIcuScheduledSync.syncEveryone` runs on Spring's scheduler with
`mimos.intervals-icu.sync-interval` as both its initial delay and its
fixed delay, so a test context never runs it on its own. It asks
`IntervalsIcuConnections.due` for connected people outside any
`retry_at`, longest-unsynced first, and runs item 16's sync for each in
turn; the sync's claim already skips anyone with a sync running. An
interval under a minute fails startup.

Live with `MIMOS_INTERVALS_ICU_SYNC_INTERVAL=1m`: after day+2's workout
changed in the fake from 120 to 180 minutes, its target moved from 4048
to 4734 kcal 60 s later with no sync call. Every scenario passes, after
fixing `fuel-profile`'s last step, which still expected no fuel on the
Log page from before item 10.

Land on `main`; set row 17 to `done`.
