# 19. Connect from the Fuel page

**Step:** 14.5 · **Depends on:** 11, 17 (and 18's decision) · **Review:** yes

## Build

- The Fuel page gains a section with athlete id and API key fields,
  status, last sync time, Refresh, and Disconnect. A broken connection
  says plainly that it needs a new key. With the feature off the section is
  absent and manual training still works.
- The profile hints, if item 18's decision wants them shown.
- The plan page labels each session with its source; synced sessions have
  no remove control.
- Copy rules as item 08.
- Scenario `intervals-icu.spec.ts` against the fakes overlay (add it to
  `args.test.ts`): a fresh account connects with `i1` and the fake's good
  key; the Fuel page reads Connected with a last sync time; the plan page
  shows the fake's planned workouts with their source; each day's target
  uses their work in kJ.

## Live checks

1. The scenario's flow by hand. Save `connected.png`.
2. A wrong key: a plain error, the API answered 400, and
   `harness sql "select count(*) from intervals_icu_connection"` is 0.
3. Refresh after changing a workout in the fake. Save `refresh.png`.
4. Make the fake answer 429, then 401. The plan page still loads with the
   last synced sessions, and after the 401 the Fuel page asks for a new
   key. Save `errors.png`.
5. Disconnect: synced sessions are gone, manual ones stay; MCP `tools/list`
   has the status and sync tools but not connect or disconnect.
6. Restart with `MIMOS_INTERVALSICU_ENABLED=false`: the section is gone and
   manual training still works. An account that never connects sees the
   plan and log pages exactly as before.
7. 390px in light and dark: nothing scrolls sideways.

## Review

Post the screenshots and wait for the go. Land on `main`; set row 19 to
`done`.
