# 08. Fuel for the day on the plan page

**Step:** 14.4 · **Depends on:** 06 · **Review:** yes

The first time a person sees a target.

## Build

- `apps/web/src/lib/fuel.ts`: formatting and copy for a day's target,
  in a module that touches neither the browser nor Next, so `node --test`
  covers it (siblings imported with `.ts`).
- In the plan page's Mine view, and in a household of one, each day card
  shows the line **Fuel for the day** with calories and the three macros,
  beside 14.1's Planned line. Nothing shows when targets are off: the page
  must look exactly as it does on `main`.
- Data: `getMyFuel` for the week. It carries `planned` too, so prefer one
  request that feeds both lines over adding a second one next to
  `my-totals`. A page load may make at most one request more than `main`.
- Reload the week's fuel after every plan write, as the Planned line does.
- Tokens only; no fuel element uses `--danger` or `--accent`.

## Checks

- `apps/web/test/fuel.test.ts`: number formatting, the absent-target case,
  and a copy check that no string has an em dash or the words over, under,
  missed, or exceeded.
- New scenario `fuel-targets.spec.ts` (add it to `args.test.ts`): a fresh
  account saves the golden profile (70 kg, male, body fat 15%, FTP 280,
  goal Fuel) and sees Monday's card read **Fuel for the day** with
  2678 kcal and 350 g carbs.
- Gates: web, harness, then every scenario.

## Live checks

1. Targets off: the visible text of the plan page is identical on `main`
   and at the head for the same fresh account.
2. Targets on, golden profile with sessions added over the API: every day
   card's numbers equal `GET /api/v1/me/fuel` for that day. Save
   `fuel-plan.png`.
3. Shared household: Mine shows only the viewer's targets; Everyone shows
   none. A household of one shows targets with no toggle.
4. 390px wide in light and dark: nothing scrolls sideways. Save
   `fuel-plan-mobile-light.png` and `-dark.png`.

## Perf

Time from navigation to every day card visible, targets off, on `main`
then at the head; and to every Fuel for the day line, targets on. Count API
requests per load from the HAR.

- Targets off: within 10% plus 50 ms of `main`.
- Targets on: within 300 ms of `main`'s targets-off time.
- At most one request more than `main`.

## Review

Post the screenshots and wait for the go. Land on `main`; set row 08 to
`done`.
