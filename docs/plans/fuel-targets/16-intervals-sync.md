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

## Interrogation (items 14 to 16)

Three reviewers (Opus, Fable, Sonnet) read `SecretBox`, connect and
disconnect, and the sync. Fixed in this item:

- **A workout moved into the window from before it failed every sync**
  (all three). Its id is the same, its old row sat outside the range the
  delete covered, and the insert collided. `replaceSynced` now also
  deletes the incoming sessions' own rows wherever they are dated.
- **The unique index left out `kind`** (two), so a planned workout and an
  activity sharing a source id could not both be stored. V18 adds it.
- **Overlapping syncs skipped `retry_at`, and a stale result could
  overwrite a newer one** (all three). A sync now claims the row
  (`sync_claim`, a two-minute lease) before calling intervals.icu and
  stores its outcome only while the claim stands. Connecting again clears
  the claim and disconnecting deletes it, so either wins over a sync that
  started before. The scheduled job in item 17 gets "skip if busy" from
  the same claim.
- **No test covered that race** (all three). Tests hold the stub's events
  read and disconnect, reconnect, or sync again meanwhile; removing the
  claim check fails two of them.
- **A failure to store was an unrecorded 500** (two). It is now a
  `lastError` on the connection, and the old rows stay.
- **The contract promised a scheduled sync** (all three). The sentence
  goes back in with item 17.
- **Sealed keys were not bound to their row** (two). `SecretBox` takes
  the profile id as GCM associated data, and a malformed nonce reads as
  unreadable instead of an error.
- **Reconnecting cleared `retry_at`** (one). A same-athlete reconnect
  keeps it.
- `replaceSynced` takes the source it replaces; times are taken after
  the calls, and stored truncated to Postgres's microseconds.

Recorded, not changed:

- "Today" is the server's UTC date, while session dates are the
  athlete's local dates. The 7 and 14 day margins absorb it; the
  athlete record's time zone could set it later.
- A `Retry-After` given as an HTTP date waits the 15-minute default, and
  one over 24 hours waits 24 hours.
- On a slow intervals.icu a sync can hold its request for up to 40 s (two
  calls, 10 s connect and 10 s read each). Against the fake, 20 syncs
  took at most 25 ms.
- A client failure's message, which can name the athlete id and the date
  range, goes into the API log. Never the key.

## Result

Live against the fake (test2): changing day+2's workout from 120 to 180
minutes moved its target from 4048 to 4734 kcal, with 3 synced rows after
each of two syncs. A completed activity yesterday counted its
`icu_joules` (1234 kcal, `COMPLETED_WORK`) in place of the planned 800.
100 reads of the plan page's endpoints reached the fake 0 times; 20 syncs
took 10 to 25 ms and made 40 reads. All 21 scenarios pass.

Land on `main`; set row 16 to `done`.
