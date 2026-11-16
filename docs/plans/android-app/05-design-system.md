# 05. Evening Kitchen in Compose

**Depends on:** 01

The app looks like Mimos before any feature screen exists.

## Build

- Tokens in `shared`: the colours of `apps/web/src/app/globals.css`
  (`:root` light, `[data-theme="dark"]` dark) as a Compose colour scheme,
  plus spacing and type scales. A small generator reads `globals.css` and
  writes the Kotlin tokens, and CI fails when they differ, so the "change
  them together" rule is checked, not remembered.
- Fonts: Newsreader and Public Sans from `apps/web/src/fonts/`, as files
  Compose can load. Not the Twemoji flags, which are web only. Extend
  `apps/web/src/fonts/README.md` with the mobile build of the same subsets
  (its tools are in devenv); commit the files.
- Shape: straight edges, no elevation shadows, `--danger` for errors.
- No animation: a no-op `Indication` for presses, and navigation
  transitions, bottom sheet, and dialog enter and exit animations turned
  off by name. The week panel's wheel (item 09) is the one exception.
- Theme: light by default, dark when chosen in the app, never following
  the system, as on the web (ADR-0021). Stored per install.
- Components the screens reuse: page header (eyebrow, serif title, amber
  rule, actions), buttons, text fields with errors, lists, a bottom sheet,
  a dialog, empty and error states.
- The app shell, matching the web: tabs Kitchen, Recipes, Plan, Shopping,
  and Log (`apps/web/src/components/app-nav.tsx`); Fuel, Household,
  Plugins, and Account in the profile menu (`profile-menu.tsx`).

## Checks

- The token generator's test, and the CI diff check.
- A gallery screen in debug builds with every component in both themes;
  screenshots of it on the emulator at a phone size, posted with the item.
- Gates: mobile.
