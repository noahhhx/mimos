# 11. The fuel guide

**Step:** 14.4 · **Depends on:** 09, 10 · **Review:** yes

## Build

- `docs/guide/fuel.md`: turning targets on, the four goals, where each
  number comes from (energy availability × fat-free mass plus exercise; the
  carb bands; protein; fat as the remainder), manual training, and the
  sources ADR-0022 cites. Written for people who use Mimos, not
  contributors.
- Add it to the nav in `mkdocs.yml`.
- `docs/guide/log.md`: its promise of no goals now says the log has none
  unless you turn on fuel targets.
- `docs/guide/planning.md`: one line pointing to the fuel guide from "Your
  planned total".
- Screenshots `docs/assets/screenshots/fuel-plan.png` and `fuel-log.png`,
  1280px wide, light theme, against the compose stack.

## Checks

- `devenv shell -- mkdocs build --strict`.
- Read the built page in `mkdocs serve`: both screenshots render, links
  work.

## Review

Post the rendered page and wait for the go. Land on `main`; set row 11 to
`done`. Steps 14.1 to 14.4 are now complete: a self-hoster without
intervals.icu can enter training and see targets.
