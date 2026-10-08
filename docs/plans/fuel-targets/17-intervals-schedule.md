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

Land on `main`; set row 17 to `done`.
