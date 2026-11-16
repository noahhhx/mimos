# ADR-0033: The docs site moves to Zensical

- Status: Accepted
- Date: 2026-11-16
- Settles: the AGENTS.md open decision "Docs generator"

## Context

The docs site is MkDocs 1.6.1 with Material for MkDocs. MkDocs has had no
release since 2024, and its 2.0 drops plugins and themes. Material is in
maintenance mode, and its authors give its end of life as 2027-05-05:
critical and security fixes stop then. Their successor, Zensical, reads
`mkdocs.yml`. The move was held back because Zensical did not support
`exclude_docs`, which keeps the contributor docs (`docs/design/`,
`docs/harness/`, `docs/decisions/`, `docs/plans/`) off the site.

Zensical 0.0.70 (2026-10-12) added `exclude_docs`, `draft_docs`, and
`not_in_nav`. A side-by-side build of today's `mkdocs.yml` with Zensical
0.1.1 (released 2026-11-10) showed:

- `zensical build --strict` passes, with no edits to the config.
- The same pages are built, and no contributor docs are published.
- The emoji references resolve, and `mimos.css` loads after the theme.
- Unchanged, the site renders Zensical's `modern` look. With
  `theme.variant: classic` it matches the MkDocs build, except for two
  things: search is a button with a keyboard hint, so the twelve
  `.md-search*` rules in `mimos.css` match nothing, and the content sits
  a few pixels to the left at 1280 px.
- The search index moves to `search.json`, the lunr language bundles
  and `sitemap.xml.gz` are gone, and `zensical build` writes only to
  `site_dir` (default `site`, which CI already uploads).

## Decision

Move the docs site to Zensical in one change, well before 2027-05-05:

- `docs/requirements.txt` pins `zensical` exactly, in place of `mkdocs`
  and `mkdocs-material`. Zensical is pre-1.0, so a bump is a reviewed
  change, never a range.
- `mkdocs.yml` sets `theme.variant: classic` and stays the config file.
- The `.md-search*` rules in `mimos.css` are rewritten for Zensical's
  search markup, and the horizontal shift is explained and fixed if it
  is ours. The theme stays CSS-only, with no template overrides.
- CI's docs job and devenv's `enterTest` run `zensical build --strict`.
  AGENTS.md's Docs row and verification commands, and
  `docs/design/index.md`, say Zensical.
- The checks are a strict build, and light and dark screenshots of the
  home page and one guide page at 1280 px against today's site.

Alternatives considered:

- **Stay on MkDocs and Material.** This is safe until 2027-05-05 and
  unsafe after it. Moving now, while the old build still runs side by
  side for comparison, is cheaper than moving under a deadline.
- **Wait for Zensical 1.0.** No date is announced. The exact pin
  handles a pre-1.0 dependency.
- **Another generator.** Every page, the nav, and the theme would be
  rewritten. Zensical reads what exists.

## Consequences

- Until the move lands, `mkdocs build --strict` stays the check.
- The search box looks different until `mimos.css` is updated, so the
  CSS rewrite is part of the move, not a follow-up.
- Zensical is a young project from a small team. If it stalls, the
  site's sources are still plain Markdown and a `mkdocs.yml`.
