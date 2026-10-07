# ADR-0010: Keycloak login theme

- Status: Accepted
- Date: 2026-10-02
- Supersedes: none

## Context

Signing in leaves the app for Keycloak's own pages (ADR-0004). Those pages
used Keycloak's stock `keycloak.v2` theme: blue PatternFly, rounded
buttons, and a Keycloak background, so the step between the landing page
and the Kitchen felt like a different product. The app's look is Evening
Kitchen ([ADR-0008](adr-0008-visual-direction.md)), and the login pages
should wear it too, on every deployment, including self-hosted ones.

## Decision

- **A `mimos` login theme that extends `keycloak.v2` and only restyles
  it.** It lives in `deploy/keycloak/themes/mimos/` and overrides no
  templates: `css/mimos.css` remaps PatternFly's global tokens (colors,
  fonts, radius, shadows, transitions) onto Evening Kitchen's and restyles
  the few components a login page uses. Keycloak's templates, scripts,
  and form ids (`#username`, `#password`, `#kc-login`) stay upstream's, so
  a Keycloak upgrade does not mean merging copied templates.
- **A small Keycloak image.** `deploy/keycloak/Dockerfile` adds the theme
  and the web app's committed fonts (`apps/web/src/fonts/`) to the stock
  image, and compose builds it from the repo root. The fonts are copied at
  build time, not committed twice, and an image carries the theme to any
  deployment (compose today, AWS later) without volume mounts. The realm
  export stays a compose mount.
- **The realm selects it.** `mimos-realm.json` sets `loginTheme: mimos`
  and the display name "Mimos" (the brand and the page title). The HTML
  display name wraps the brand in a link to the web app's home
  (`${MIMOS_WEB_ORIGIN}/`), so a signed-out visitor can leave the sign-in
  and register pages without the browser's back button. Keycloak's
  sanitizer keeps the link, so no template is copied for it.
- **Light only.** The app keeps its dark choice in the app origin's
  `localStorage`, which Keycloak's pages cannot read, and the OS setting is
  not followed (ADR-0008), so the theme sets `darkMode=false`.

## Consequences

- The token values in `mimos.css` mirror `apps/web/src/app/globals.css`;
  a color change is made in both. The `keycloak-theme` harness scenario
  checks the page's background, accent, straight edges, fonts, and error
  color, and screenshots the sign-in, error, and register pages at phone
  and desktop width.
- Keycloak changes the PatternFly markup underneath on its own schedule.
  The image pins Keycloak's minor version (`26.5`); bumping it means
  re-running that scenario and looking at the screenshots.
- A deployment that runs a stock Keycloak with this realm export (the API
  integration tests do) logs that the `mimos` theme is missing and falls
  back to Keycloak's built-in theme. Nothing else changes.
- Only the login theme is customized. The account console and emails keep
  Keycloak's look until a feature needs them.
