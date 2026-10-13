# 21. Close step 14

**Step:** 14 · **Depends on:** every item above

## Check ROADMAP step 14's done-when

On a fresh `harness up --debug` from `main`:

1. A self-hoster without intervals.icu (feature off, no secret key) enters
   training by hand and sees targets.
2. A connected person's targets follow their planned and completed
   workouts.
3. A person with targets off sees no change anywhere.

## Do

- Mark ROADMAP step 14 done with a short note of what shipped; leave
  14.6's "later" ideas as their own future steps.
- AGENTS.md: no "planned" left on `core-fueling` or
  `integrations/intervals-icu`.
- Every row in the index reads `done`. Move anything still in "Found along
  the way" to the roadmap or an issue.

## Result

On a fresh `harness up --debug` from `main` at `de07490`:

1. With `MIMOS_SECRET_KEY` empty, `/api/v1/me/intervals-icu` is a 404.
   With targets on, the rest day is 2678 kcal (LIGHT), and a 90-minute
   endurance ride entered by hand makes it 3706 kcal (HIGH,
   `FTP_INTENSITY`).
2. Connected to the fake: today's planned ride counts its work
   (`PLANNED_WORK` 1361 kcal, 4038 kcal for the day), day+2's FTP
   (`FTP_INTENSITY` 1371), and yesterday's activity its `icu_joules`
   (`COMPLETED_WORK` 2204). After a completed ride today and a sync,
   today counts `COMPLETED_WORK` 1500 in place of the plan (4178 kcal).
3. With targets off, the fuel read carries no `target`. The
   `fuel-targets` and `fuel-profile` scenarios check that no page shows
   one.

ROADMAP step 14 is marked done, and its later ideas are step 18. The
"Found along the way" list moved to step 19, along with item 16's two
recorded gaps. The open questions about `joules` and `icu_intensity` wait
for the owner's account (item 20 says why they do not block). AGENTS.md
describes `core-fueling` and `integrations/intervals-icu` as built.
