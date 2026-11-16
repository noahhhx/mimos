# 09. Plugins: page, suggestions, week panels

**Depends on:** 08

Plugins in the app, from the same declarative JSON the web renders
(ADR-0006, ADR-0013, ADR-0017). No plugin code runs in the app.

## Build

- Plugins page: each registered plugin, its manifest's name, description,
  and homepage link (http(s) only, opened in the browser), and the
  household's on/off switch.
- On the plan: the week's panels (collapsed summary, then `text`,
  `highlight`, `wheel`, `actions` blocks) and suggestion cards with
  Apply, which adds entries through the plan-entry endpoint. Rendered as
  plain text, attributed to the plugin.
- The wheel spins, the one animation allowed, and lands at once when the
  system's "remove animations" setting is on.
- An unknown block type is skipped, not a crash, so a newer plugin keeps
  working in an older app.

## Checks

- `commonTest`: decoding each block type and an unknown one.
- Flow: `country-week` (turn the plugin on, choose a country with the
  wheel, apply a suggestion), mirroring the web scenario.
- Gates: mobile.
