# ADR-0008: Visual direction (Evening Kitchen)

- Status: Accepted
- Date: 2026-10-02
- Supersedes: none

## Context

The first web design was a warm-neutral Swiss style: monospace labels,
hard black rules, a red square for a mark. It was clean, but it didn't
feel inviting or like home, which is where people use a recipe companion.
The owner chose "Evening Kitchen" out of five cozier directions. The plan
and the reference mockup are in [Evening Kitchen restyle](../design/index.md).

## Decision

- **Evening Kitchen.** Warm paper and espresso, one amber accent, a book
  serif (Newsreader) for headings, titles and method steps, a plain sans
  (Public Sans) for everything else, and large readable type: 17px body
  text, method steps around 23px. Section labels are small uppercase sans,
  not monospace; monospace is only for code. No italics.
- **Light by default.** Dark applies only when the user picks it with the
  header toggle, and the choice persists in `localStorage`. The OS
  `prefers-color-scheme` setting is not followed.
- **Self-hosted fonts.** The font files are committed (`apps/web/src/fonts/`,
  subset to Latin, with their OFL licenses and a README on how to rebuild
  them) and loaded with `next/font/local`. No request goes to a font host
  at build or run time, so the web image builds and runs offline, as a
  self-hosted deployment needs. No new npm dependency.
- **Straight edges, no motion.** No rounded corners, no soft shadows, no
  animations or transitions. Hover changes color only.
- **Colors only through tokens.** Every color is a custom property in
  `apps/web/src/app/globals.css`, defined once for light and once for dark
  (`:root[data-theme="dark"]`). Components never use literal colors.
  Errors and destructive actions use `--danger`, never the accent, so an
  error never looks like a highlight. Text pairings meet WCAG AA (4.5:1).

## Consequences

- A user whose OS is in dark mode sees the light theme until they toggle
  it. That is deliberate: the light theme is the product's face.
- Fonts change only by rebuilding the committed files; the tools for that
  (`fonttools`, `woff2`) come from the devenv shell, and the product build
  needs neither.
- Characters outside the Latin subset (arrows, "✕", non-Latin scripts)
  render in the fallback fonts (`Georgia`, `system-ui`). Adding a subset is
  a font rebuild, not a code change.
- A new color means a new token with light and dark values and a contrast
  check, which keeps both themes complete.
