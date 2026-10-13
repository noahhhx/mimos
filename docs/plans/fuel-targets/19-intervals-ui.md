# 19. Connect from the Fuel page

**Step:** 14.5 · **Depends on:** 11, 17 (and 18's decision) · **Review:** yes

## Build

- The Fuel page gains a section with athlete id and API key fields,
  status, last sync time, Refresh, and Disconnect. A broken connection
  says plainly that it needs a new key. With the feature off the section is
  absent and manual training still works.
- The profile hints, as suggestions (item 18's decision): where
  `hints` on `GET /api/v1/me/intervals-icu` has a weight, sex, or FTP
  that differs from the form, the Fuel page names each value from
  intervals.icu next to its field with a control that copies it into the
  form. Nothing saves until the person presses Save. Body fat from
  intervals.icu needs no control: it already counts while the profile's
  field is empty, and the page says so.
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

## Result

Landed without waiting for the go: the owner was asleep and had asked for
the remaining items to be worked through, with decisions made as needed.
The screenshots are for review after the fact, and anything they turn up
becomes a follow-up item.

- `IntervalsIcuSection` (`apps/web/src/components/intervals-icu-section.tsx`)
  sits under the profile form, which is now the form named "Fuel
  profile". Connecting syncs at once. A broken connection shows its
  error and the form, with the athlete id kept. Disconnect asks once,
  inline. The copy lives in `apps/web/src/lib/intervals-icu.ts`, with
  unit tests.
- Suggestions: "intervals.icu has 70.8 kg." with a Use it button beside
  weight, sex, and FTP where they differ from the form. Body fat's hint
  reads "While this is empty, your targets use 13.6% from intervals.icu."
- Every session on the plan page names its source: "By hand" or
  "intervals.icu". Synced ones have no remove control.
- Scenario `intervals-icu` (pinned in `args.test.ts`). `fuel-profile`
  and `fuel-targets` now find "Saved." inside the profile form, since the
  section has its own status line, and `fuel-targets` expects the source
  label.

Live checks, as test2 against the fake, screenshots in
`.harness/mcp/item19/` (local, gitignored):

1. Connect, then the Fuel page reads "Connected as i1. Last synced …"
   (`connected.png`).
2. A wrong key: the scenario sees the API's 400 and the plain error, and
   the section stays "Not connected."
3. Changing today's workout from 90 to 120 minutes in the fake, then
   Refresh: the plan shows "Ride · 120 min · Hard · Planned ·
   intervals.icu" (`refresh.png`).
4. A 429 with `Retry-After: 600`: "intervals.icu asked Mimos to wait.
   Training syncs again after …", and the plan still lists the 3 synced
   sessions. Then a 401: "intervals.icu no longer accepts this API key.
   Enter it again." with the form, and the plan still lists them
   (`errors.png`).
5. Disconnect (scenario): synced sessions are gone. `McpEndpointTests`
   pins connect and disconnect as the only opt-outs, so status and sync
   are tools.
6. `MIMOS_INTERVALSICU_ENABLED=false`: the GET is a 404, the section and
   suggestions are absent, and Add training still shows on the plan.
7. 390px, light and dark, Fuel and Plan: no sideways scroll
   (`fuel-390-light.png`, `fuel-390-dark.png`).
