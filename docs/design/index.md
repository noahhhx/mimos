# Evening Kitchen restyle: plan

**Status:** step 1 (tokens, fonts, theme default) is done, with step 4's
[ADR-0008](../decisions/adr-0008-visual-direction.md) and AGENTS.md update;
steps 2 and 3 are next. This page is the brief for the agent that builds
it. Decisions made with the owner are marked **decided**; don't
relitigate them without asking. The new Kitchen home screen is a separate
plan, [Kitchen home](kitchen-home.md), and comes after this one.

## Why

The current web design (warm-neutral Swiss: monospace labels, hard black
rules, a red square) is clean but doesn't feel inviting or like home. The
owner picked "Evening Kitchen" out of five cozier directions: warm paper,
cream and espresso, one amber accent line, a book serif for headings,
large readable type, and a left rail that keeps context in view.

## Reference

- **Mockup:** [`evening-kitchen.html`](evening-kitchen.html) (open it in a
  browser). It shows the Kitchen home, the library and a recipe page with
  sample data, in both themes (toggle in its header). It loads fonts from
  Google for convenience; the product must not (see below).
- Where the mockup and this page disagree, this page wins.

## Decided

| Area | Decision |
| ---- | -------- |
| Direction | Evening Kitchen, as in the mockup. **No italics anywhere**: the owner rejected the italic "evening" in the greeting, and the mockup no longer uses italics. |
| Theme default | **Light by default.** Dark only when the user picks it with the toggle, and the saved choice persists. The OS `prefers-color-scheme` setting is no longer followed. |
| Fonts | **Commit the font files** to the repo and load them with `next/font/local`. No Google Fonts requests at build or run time, and no new npm dependency. |
| Kitchen home | Its own plan and its own change: [Kitchen home](kitchen-home.md). This restyle leaves the home page's content as it is. |
| Shape and motion | Straight edges only (no `border-radius`, no soft shadows), and no animations or transitions. Hover can change color, not move or fade. |

## Design tokens

Keep the existing token names in `apps/web/src/app/globals.css` where they
still fit, so the diff stays a restyle, not a rewrite. The values come from
the mockup, with the light accent and danger colors darkened to pass WCAG AA
(4.5:1) for normal text. Contrast was measured against `--bg`:

| Token | Light (default) | Dark | Role / notes |
| ----- | --------------- | ---- | ------------ |
| `--bg` | `#f4eee5` | `#191512` | Page ground. |
| `--surface` | `#fbf7f1` | `#221c18` | Panels such as the "Tonight" block, inputs. |
| `--hover` | `#efe6da` | `#2b241f` | Raised/hover fill (the mockup calls it `--raised`). |
| `--fg` | `#2b221c` | `#f1e7d9` | Text. 13.5:1 / 14.8:1. |
| `--muted` | `#76685c` | `#a8998b` | Secondary text. 4.7:1 / 6.6:1. |
| `--line` | `#ddd2c3` | `#3a312a` | All rules and borders. Low contrast by design. |
| `--accent` | `#94560f` | `#e9aa50` | Amber: the one accent (rules, active nav, primary button, links on hover, focus ring). 5.1:1 / 8.9:1. |
| `--on-accent` | `#fbf7f1` | `#191512` | **New.** Text on amber buttons. 5.5:1 / 8.9:1. |
| `--danger` | `#a84a25` | `#d97a4f` | **New.** Errors and destructive buttons. 5.0:1 / 5.9:1. |
| `--line-strong` | — | — | **Retire.** Audit each use: page-header rules become the short amber rule, button borders use `--accent` or `--line`, and the rest use `--line` or `--fg`. |

Today `--accent` also marks errors (`.card.error`, `.button.danger`). Move
those to `--danger`, so an error never looks like a highlight.

Typography:

- `--serif`: **Newsreader**, upright, weights 400–500, with the optical-size
  axis. Use it for `h1`, recipe and meal titles, the lede, method steps and
  big numbers. Headings are weight 400 and large (`h1` around
  `clamp(40px, 7vw, 68px)`, as in the mockup).
- `--sans`: **Public Sans**, weights 400–600. Use it for body text, the UI
  and labels.
- Section labels (`h2`, `.eyebrow`, `dt`, form labels) change from monospace
  to 13px Public Sans, weight 600, uppercase, `letter-spacing: 0.12em`, in
  `--muted`.
- `--mono` stays only for `pre`/`code` (the self-hosting snippet).
- Body text is 17px with `line-height: 1.6`. Method steps are serif at
  about 23px, so they can be read from the counter.

## Fonts: committing the files

- Put the files in `apps/web/src/fonts/`: one variable `woff2` per family,
  upright only and subset to Latin. That means `Newsreader[opsz,wght]` and
  `PublicSans[wght]`, taken from the families' upstream sources (the
  `google/fonts` repository, `ofl/newsreader` and `ofl/publicsans`). Done:
  `newsreader/Newsreader.woff2` and `public-sans/PublicSans.woff2`, with
  the weight axes narrowed to the weights used.
- Commit each family's `OFL.txt` next to its file. Add a short `README.md`
  there recording the source URL, the upstream version or commit, and the
  exact subsetting command, so the files can be rebuilt.
- If converting to `woff2` or subsetting needs a tool (for example
  `fonttools` with `brotli`), add it to `devenv.nix` from nixpkgs. Per
  AGENTS.md, never use ad-hoc installs. The product build must not need
  that tool, only the committed files.
- Load the fonts in `apps/web/src/app/layout.tsx` with `next/font/local`
  (part of Next, so no new dependency), using `display: "swap"` and the
  `variable` option. That puts `--font-serif`/`--font-sans` on `<html>`,
  and `globals.css` maps `--serif`/`--sans` to them, each with a real
  fallback stack (`Georgia, serif` and `system-ui, sans-serif`).
- Check that the Docker image build (`apps/web/Dockerfile`) still works
  without network access to any font host.

## Layout: the rail

The mockup's main layout idea: on desktop, a page is two columns. The left
**rail** (about 300px) holds the page's identity and context: an eyebrow,
the `h1`, a short amber rule (64×2px) and a lede or a compact list. The
**main** column holds the work. Below about 880px the rail stacks above
the main column.

Build it once as a small server component, for example
`src/components/split-page.tsx` with `rail` and `children` slots, plus
`.split`/`.rail` CSS. Don't build a per-page grid. Pages that don't suit a
rail (forms, for example) keep a single column, with the same header
treatment: eyebrow, serif `h1` and amber rule.

The header keeps its structure. The brand becomes "Mimos" in the serif
with a short amber dash for the mark (see the mockup), and the theme toggle
stays where it is. The app nav stays a row under the header, with the
active item marked by an amber underline.

## Steps

One change per step, each with its tests and docs. Each step must leave
the site working.

### 1. Tokens, fonts, theme default

- Replace the token values and add `--on-accent` and `--danger` as in the
  table, retire `--line-strong`, and add the font files and `next/font/local`.
- Theme: the bare `:root` block holds the light values, and
  `:root[data-theme="dark"]` holds the dark values with
  `color-scheme: dark`. Delete the `@media (prefers-color-scheme: dark)`
  block.
- In `layout.tsx`, the `THEME_SCRIPT` keeps applying the saved theme
  before first paint. Its toggle becomes
  `next = root.dataset.theme === "dark" ? "light" : "dark"`, with no
  `matchMedia`.
- Set the type: serif headings, sans body text, and the label style above
  replacing the monospace labels.
- Remove every `transition` and any `border-radius` in `globals.css`.
  (`border-radius: 0` resets on buttons and inputs stay: they keep native
  controls square.)
- Buttons: the primary button is filled amber with `--on-accent` text, the
  secondary button is transparent with a `--line` border and `--fg` text,
  and the danger button uses `--danger`.
- **Done when** every existing page renders correctly in both themes with
  no layout changes yet, the first visit is light, the toggle switches to
  dark and the choice survives a reload, and all harness scenarios pass
  unchanged.

### 2. Public pages: rail layout

- Add the split-page component and the rail styles.
- **Landing** (`app/page.tsx`): the rail has the eyebrow, `h1` and lede; the
  main column has the calls to action and the self-hosting card.
- **Library** (`app/recipes/page.tsx`): the rail has the title, amber rule
  and lede. The main column has a search field (underlined input in the
  serif, as in the mockup) and the recipes as **rows** instead of tiles:
  title (serif), description (muted), and minutes and kcal on the right.
  Keep the tags, smaller and under the description. The whole row stays the
  link.
- **Recipe** (`app/recipes/[slug]/page.tsx` for public recipes and
  `components/recipe-view.tsx` for personal ones; they have separate
  markup today, so restyle both or share a component): the rail has the meta line, `h1`, amber rule, ingredients
  (still tickable) and per-serving nutrition as a 2×2 grid. The main
  column has the lede and the method as large serif steps with small amber
  numbers.
- Keep these selectors, which the scenarios rely on: `article.recipe`,
  `.tag`, `.nutrition` (it must still contain the nutrition values, so put
  the class on the nutrition block), `.ingredients li`, `.quantity`, and
  the recipe title as the page's `h1`.
- **Done when** the three pages match the mockup's structure at 1280px and
  390px in both themes, public pages are still server-rendered (check that
  the HTML contains the content), and the scenarios pass.

### 3. App pages

- **Plan** (`app/app/plan/page.tsx`): restyle the week grid, meal slots,
  picker and suggestion cards with the new tokens. Move the week
  navigation into the page header. The day grid can stay a grid; it does
  not have to become the rail list, which belongs to the Kitchen home.
  Suggestion cards stay plain text and keep their "via {plugin}" credit
  (ADR-0006).
- **Shopping list, Log, Recipes, New/Edit recipe**: apply the header
  treatment and tokens. Tables (`.totals`) use `--line` rules,
  `tabular-nums` and a `--hover` row fill. Checkboxes and the selected row
  use `accent-color: var(--accent)`.
- Forms: underlined or `--line`-bordered inputs on `--surface`, with field
  errors in `--danger`.
- **Done when** every page under `/app` matches the new look in both
  themes at phone and desktop widths, and all scenarios pass, including
  `recipe-form-errors` and `edit-recipe`.

### 4. Docs and decisions

- Write an ADR, `docs/decisions/adr-0008-visual-direction.md`: Evening
  Kitchen, light by default with dark as an opt-in, self-hosted fonts via
  `next/font/local`, straight edges, no motion, and colors only through
  tokens. Add it to `mkdocs.yml` and `docs/decisions/index.md`.
- AGENTS.md, "Frontend specifics": add a "Visual design" bullet that points
  to the ADR and this page (tokens live in `globals.css`, no literal
  colors in components, no radius, no transitions, fonts are committed
  files). The repo layout gains `apps/web/src/fonts/`.
- Mark this page's status as done and link the ADR.
- In practice this step can ship with step 1, since that is where the
  decisions take effect. Either way, AGENTS.md must be current when step 1
  merges.

## Verification (every step)

- `npm run typecheck -w @mimos/web`, `npm run build -w @mimos/web` and
  `npm test -w @mimos/web`.
- `devenv shell -- harness up` (a fresh image build), then run every
  scenario in `tools/harness/scenarios/`. CI runs them too.
- Screenshots at 1280px and 390px in light and dark mode for every page
  the step touched (`harness ui`, or the Playwright MCP browser), checking
  for no horizontal scroll at 390px. Compare against the mockup.
- `mkdocs build --strict` when docs change.
- If you introduce any new color pairing, check its contrast; normal text
  needs 4.5:1.

## Out of scope

- The Kitchen home's new content (see [Kitchen home](kitchen-home.md)).
- New API endpoints or contract changes. This is a pure frontend change.
- Images or illustrations, iconography beyond what exists, and motion of
  any kind.
