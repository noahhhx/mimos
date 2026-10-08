# 10. Targets on the Log page

**Step:** 14.4 · **Depends on:** 08 · **Review:** yes

## Build

- On the Log page, when targets are on, the weekly table shows each day's
  target beside its logged totals. Days with nothing logged keep their
  dash. With targets off the page is unchanged.
- One `getMyFuel` read for the week; formatting from `src/lib/fuel.ts`.

## Checks

- Extend `fuel-targets.spec.ts`: log two meals and open the Log page; each
  day shows its target beside the logged totals.
- Gates: web, harness, then every scenario.

## Live checks

1. Targets off: the Log page's visible text is identical on `main` and at
   the head.
2. Targets on with two logged meals. Save `fuel-log.png`.
3. 390px in light and dark: the table fits or scrolls inside its own box,
   never the page. (The table already reaches 376px at 320px wide on
   `main`; see "Found along the way". Do not make it worse.)

## Review

Post the screenshots and wait for the go. Land on `main`; set row 10 to
`done`.
