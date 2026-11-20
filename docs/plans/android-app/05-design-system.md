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

## What was built

**Tokens.** `generateDesignTokens` (the `mimos.design-tokens` plugin in
`apps/mobile/build-logic`, applied by `shared`) parses `globals.css` at
build time and writes `MimosColors` into `shared/build/generated/`, with
`Light` and `Dark` instances and one field per colour. It fails the build
when one of the nine colours is missing from either theme, a theme has a
colour the other lacks, or a value is not `#rrggbb`. Any further `#hex`
custom property that both themes define becomes a field too. Its tests
run in `build-logic` under `check` (`DesignTokensTest`: the parser, each
failure, and the task through TestKit).

**Type and spacing.** `MimosTypography` and `MimosSpacing` are hand-written
copies of the web's values (`ui/Typography.kt`, `ui/Spacing.kt`), provided
through `MimosTheme` with the colours. The page title is 40sp, the phone
end of the web's `clamp(40px, 7vw, 68px)`. Button hover is
`lerp(accent, fg, 0.18f)`, applied while pressed.

**Fonts.** `newsreader.ttf` and `public_sans.ttf` are the web's `woff2`
files unpacked with `woff2_decompress`, byte for byte reproducible from the
commands in `apps/web/src/fonts/README.md` ("Mobile"). They stay variable:
weights are `FontVariation.weight`, and each Newsreader style also sets
`opsz` to its own size, as the browser does. Their `OFL.txt` files are in
`shared/fonts/`.

**Components** (`shared/.../ui/`): `Text`, `Label`, `Rule`, `PageHeader`,
`Button` (primary, secondary, danger, small, disabled at 0.45),
`TextField` (label, error line, `accent` border while focused), `ListRow`,
`Notice`, `EmptyState`, `ErrorState`, `MimosDialog`, `BottomSheet`,
`IconToggle` with the theme and profile glyphs, `Brand`, and `EntryPage`
(the layout before sign-in). Nothing uses Material. Presses show no
ripple (`NoIndication` through `LocalIndication`); buttons and rows take
their pressed colour instead. Dialogs and sheets are windows
(`Overlay`, an `expect` in `commonMain` with its one Android `actual`)
whose animation is the empty system style, which `dumpsys window` shows as
`wanim=0x1030000`.

**Theme choice.** `ThemeModel` holds the choice behind `ThemeStore`
(`SharedPreferencesThemeStore` on Android); `SessionModel` reads it when a
sign-in starts and sends it as `mimos_theme`, replacing `AUTH_THEME`.
`MainActivity` follows it with the window background and the system bar
icons. Keycloak's page came up dark after choosing dark in the app.

**Shell.** `Destination` is one enum with each place's label, web path, and
placement. `ShellModel` (in `AppModel`) keeps the destination and whether the
profile sheet is open. `AppShell` is the header (duck, "Mimos", theme
toggle, profile), the destination, and a bottom `TabBar`. The profile
sheet shows the name and "Member since" from `getMe`, then Household,
Plugins, Fuel, Your data, Sign out, and Change instance. Each destination
shows its `PageHeader` and an `EmptyState` with "Open on the web", which
opens the instance's matching page. `ConnectScreen` and the signed-out
`SessionScreen` states use the same components; the connect form is still
emitted from one call site.

**Gallery.** Debug builds only: `adb shell am start -n
io.github.noahhhx.mimos.debug/io.github.noahhhx.mimos.MainActivity --ez
gallery true`, or "Component gallery" in the profile sheet. It shows type,
every component, a dialog, and a sheet, with the theme toggle on top.
`AppModel.openGallery` does nothing unless the build is debuggable.

## Deviations

- **No CI diff check.** The tokens are generated into `build/` and never
  committed, so a committed copy cannot differ. A colour change in
  `globals.css` reaches the app on the next build, and a malformed one
  fails it. The spacing and type scales and the fonts are copies, not
  read from the CSS; `AGENTS.md` lists them with the other "change them
  together" places.
- **The tabs are a bottom bar.** The web puts them in a row under its
  header. A phone's thumb reaches the bottom, so the bar sits there, with the
  active tab in `fg` and a 2dp amber rule, and a `line` rule above it.
- **No Material and no navigation library.** Navigation is a state
  switch in `ShellModel`, with no transition to turn off.

## Follow-ups

- The icon toggles are 34dp, as on the web. Android's guidance is 48dp;
  item 17's parity audit should decide whether the app keeps the web's
  size.
- The gallery's code ships in release builds, unreachable. Moving it to a
  `debug` source set needs a `commonMain` seam for it; not worth it yet.
- The profile's name loads where the shell is composed, so a rotation
  asks again. It is one small call, and it cannot show one person's name
  to the next; a model would need a reset on every way a session ends.
- Item 06's flows type `http://localhost:3000` on the connect screen: a
  bare `localhost:3000` is read as `https://`.
