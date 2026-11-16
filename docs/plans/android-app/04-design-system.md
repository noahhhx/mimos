# 04. Evening Kitchen in Compose

**Depends on:** 01

The app looks like Mimos before any feature screen exists.

## Build

- Tokens in `shared`: the colours of `apps/web/src/app/globals.css`
  (`:root` light, `[data-theme="dark"]` dark) as a Compose colour scheme,
  plus spacing and type scales. A small generator reads `globals.css` and
  writes the Kotlin tokens, and CI fails when they differ, so the "change
  them together" rule is checked, not remembered.
- Fonts: the families in `apps/web/src/fonts/`, as TTF or OTF Compose
  can load. Extend `apps/web/src/fonts/README.md` with the mobile build of
  the same subsets (its tools are in devenv); commit the files.
- Shape: straight edges, no elevation shadows, no ripple or other
  animation (a no-op `Indication`), `--danger` for errors.
- Theme: light by default, dark when chosen in the app, never following
  the system, as on the web (ADR-0021). Stored per install.
- Components the screens reuse: page header (eyebrow, serif title, amber
  rule, actions), buttons, text fields with errors, lists, a bottom sheet,
  a dialog, empty and error states, and the app shell with navigation
  matching the web's (Kitchen, Recipes, Plan, Shopping, Log, Fuel; profile
  menu for the rest).
- Window size classes: compact (phone) and expanded (tablet) layouts for
  the shell.

## Checks

- The token generator's test, and the CI diff check.
- A gallery screen in debug builds showing every component in both themes;
  screenshots of it at phone and tablet sizes, posted with the item.
- Gates: mobile.
