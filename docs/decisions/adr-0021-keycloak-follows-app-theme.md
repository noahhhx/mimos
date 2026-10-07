# ADR-0021: Keycloak's pages follow the app's theme

- Status: Accepted
- Date: 2026-10-07
- Supersedes: the "Light only" decision in
  [ADR-0010](adr-0010-keycloak-login-theme.md)
- Builds on: [ADR-0008](adr-0008-visual-direction.md) (light by default,
  dark by choice), [ADR-0020](adr-0020-sign-up.md) (sign-up through
  Keycloak's register page)

## Context

The `mimos` login theme was light only (ADR-0010). Someone who chose dark
in the app saw a light sign-in or register page between two dark ones,
and Keycloak's pages had no toggle of their own.

The app keeps the choice in its own origin's `localStorage` (key `theme`)
and applies it as `data-theme` on `<html>`. Keycloak serves its pages from
another origin, so it cannot read that key. PatternFly's own dark mode
(`darkMode=true` in `theme.properties`) follows the OS setting, which
Mimos does not do (ADR-0008).

## Decision

- **The app sends its theme on the authorization request.** `signIn` and
  `signUp` in `AuthProvider` pass `mimos_theme=light|dark` to
  `signinRedirect` as an extra query parameter, read from the `data-theme`
  on screen. Light is sent too, so it replaces a dark choice stored
  earlier on Keycloak's origin. Keycloak ignores parameters it does not
  know.
- **One script in the theme applies it.** `theme.properties` lists
  `scripts=js/theme.js`. `keycloak.v2`'s template loads it as a classic
  script in `<head>`, after the stylesheets, so it runs before the first
  paint. It reads `mimos_theme` from the URL and stores it in Keycloak's
  origin under `theme`, then applies the stored value as `data-theme`.
  The first page (sign-in, or register with `prompt=create`) renders at
  the authorization URL and has the parameter; later pages
  (`/login-actions/...`, the Register link, a form re-rendered with
  errors) do not, so they read the stored value.
- **Keycloak's pages get the app's toggle.** The script adds the app's
  toggle button to the header, right of the brand. It flips `data-theme`
  and stores the result on Keycloak's origin.
- **The CSS mirrors both token sets.** `mimos.css` adds
  `:root[data-theme="dark"]` with the dark values from `globals.css` and
  `color-scheme: dark`. It also maps PatternFly's "on light" global
  variants (`--pf-v5-global--Color--dark-100` and its siblings), which
  inputs and alerts switch to, onto Mimos tokens. Without that, input
  text stays PatternFly's near-black in dark mode.
- `darkMode=false` stays, so the OS setting is still not followed.

## Alternatives considered

- **PatternFly's dark mode.** It follows the OS setting, not the user's
  choice in the app.
- **A shared cookie.** It needs the app and Keycloak under one parent
  domain, which a self-hosted deployment does not have to use.
- **Reading the parameter in a template override.** It works without
  script but copies `template.ftl`, which ADR-0010 avoids so Keycloak
  upgrades need no merge.

## Consequences

- A toggle on Keycloak's pages lasts on Keycloak's origin only. The next
  sign-in from the app sends the app's choice again and replaces it, and
  the choice made on Keycloak's pages never reaches the app.
- A sign-in that does not start from the app (a bookmarked Keycloak URL,
  the account console's own links) uses what Keycloak's origin last
  stored, or light.
- The theme is no longer CSS only: it adds one script, still with no
  copied templates. A Keycloak upgrade that stops emitting
  `properties.scripts` in `<head>` would make the pages light again; the
  `keycloak-theme` harness scenario fails if that happens.
- A color change is made in `globals.css` and `mimos.css`, light and
  dark. The `keycloak-theme` scenario checks both themes and screenshots
  the dark sign-in, error, and register pages.
