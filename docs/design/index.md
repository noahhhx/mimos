# Visual design

The web app's look is **Evening Kitchen**: warm paper, cream and espresso,
one amber accent, a book serif for headings, large readable type, and a
left rail that keeps context in view. The decisions and their reasons are
in [ADR-0008](../decisions/adr-0008-visual-direction.md); this page is the
working reference for building pages in that style. The code is the
source of truth: tokens in `apps/web/src/app/globals.css`, layout in
`apps/web/src/components/`.

The original mockup, [`evening-kitchen.html`](evening-kitchen.html), is
kept as the record ADR-0008 cites. It predates the build (it loads fonts
from Google and uses older token names), so where it and the app differ,
the app wins.

## Rules

- **Light by default.** Light values live in `:root`, dark ones in
  `:root[data-theme="dark"]`, applied only when the user picks dark with
  the header toggle. The OS setting is not followed.
- **Colors only through tokens.** No literal colors in components. A new
  color is a new token with light and dark values and a contrast check
  (4.5:1 for normal text).
- **Errors use `--danger`**, never `--accent`, so an error never looks like
  a highlight.
- **Straight edges and no motion.** No `border-radius`, no soft shadows, no
  transitions or animations. Hover changes color only.
- **No italics** anywhere.
- **Fonts are committed files** in `apps/web/src/fonts/`, loaded with
  `next/font/local`. Never a font host or a font npm package. Its README
  says how to rebuild them.

## Tokens

Contrast is measured against `--bg`.

| Token | Light | Dark | Role |
| ----- | ----- | ---- | ---- |
| `--bg` | `#f4eee5` | `#191512` | Page ground. |
| `--surface` | `#fbf7f1` | `#221c18` | Panels (the Tonight panel, cards), inputs. |
| `--hover` | `#efe6da` | `#2b241f` | Raised and hover fills, selected table rows. |
| `--fg` | `#2b221c` | `#f1e7d9` | Text. 13.5:1 / 14.8:1. |
| `--muted` | `#76685c` | `#a8998b` | Secondary text and labels. 4.7:1 / 6.6:1. |
| `--line` | `#ddd2c3` | `#3a312a` | Every rule and border. Low contrast by design. |
| `--accent` | `#94560f` | `#e9aa50` | Amber, the one accent: rules, the active nav item, primary buttons, link hover, the focus ring. 5.1:1 / 8.9:1. |
| `--on-accent` | `#fbf7f1` | `#191512` | Text on amber buttons. 5.5:1 / 8.9:1. |
| `--danger` | `#a84a25` | `#d97a4f` | Errors and destructive buttons. 5.0:1 / 5.9:1. |

## Type

- `--serif` is **Newsreader** (upright, 400–500): `h1`, recipe and meal
  titles, ledes, method steps, big numbers. `h1` is weight 400 at
  `clamp(40px, 7vw, 68px)`.
- `--sans` is **Public Sans** (400–600): body text, UI and labels. Body
  text is 17px with `line-height: 1.6`.
- Section labels (`h2`, `.eyebrow`, `dt`, form labels) are 13px Public
  Sans, weight 600, uppercase, `letter-spacing: 0.12em`, in `--muted`.
- `--mono` is only for `pre` and `code`.
- Method steps are serif at about 23px, readable from the counter.

## Layout and components

- **`PageHeader`** (`page-header.tsx`): an optional eyebrow, the serif
  `h1` and the short amber rule (64×2px), with actions such as week
  navigation beside the title on wide screens and under it on phones.
  Every page starts with it or with `SplitPage`.
- **`SplitPage`** (`split-page.tsx`): the rail layout. A rail of about
  300px holds the page's identity and context; the main column holds the
  work. Below 880px the rail stacks above the main column. The main column
  is a CSS container, so rows inside it respond to its width, not the
  viewport's. Pages that don't suit a rail (forms, the plan grid) use a
  single column with `PageHeader`.
- **`RecipeList`** (`recipe-list.tsx`): recipes as rows (serif title,
  muted description, small tags, minutes and kcal on the right), the
  whole row being the link.
- **Buttons**: `.button` is filled amber, `.button.secondary` is
  transparent with a `--line` border, `.button.danger` uses `--danger`.
- **Notices**: `.card.error` (with `role="alert"`) and `.card.ok` (with
  `role="status"`) have a 3px left border in `--danger` or `--accent`.
- Pages must work at 390px with no horizontal scroll, in both themes.

## Kitchen home

The signed-in home (`app/app/page.tsx`) answers "what am I cooking
tonight?". Its choices are pure functions in `src/lib/kitchen.ts`, unit
tested in `test/kitchen.test.ts`; the `kitchen-home` harness scenario
covers the page end to end.

- **Rail:** the date in the user's locale, a greeting by local hour
  ("Good morning." 05:00–11:59, "Good afternoon." 12:00–17:59, "Good
  evening." otherwise) as the `h1`, then **This week**: Monday to Sunday,
  each day's first dinner as a link or "Nothing yet", today marked in
  amber.
- **Tonight:** today's first dinner, with its description, total minutes,
  planned servings and kcal per serving, "Start cooking" and "Swap meal".
  With no dinner today it shows the latest main meal under "Today" (lunch,
  then breakfast, then a snack); with nothing planned, "Nothing planned
  for tonight" and "Plan tonight". "Today" is the local date
  (`todayIso()`), never the UTC one.
- **Still to buy · N of M:** at most five rows of the week's shopping list,
  unchecked items first. A list that was never generated shows "No list
  yet" and a link to the shopping page; the home page never generates
  one.
- **A thought for {day}:** the first plugin card with a dinner on an open
  evening from today on, showing that card's title, blurb, proposed recipe
  and plugin attribution. "Add to {day}" adds that one entry through the
  plan-entry endpoint (`applySuggestionEntries`, shared with the plan
  page; ADR-0006). No such card, or no plugins, hides the block. With the
  current plugin contract this block rarely appears mid-week;
  [ADR-0009](../decisions/adr-0009-plugin-context-today.md) proposes a
  fix.
- **Profile:** name, member since, and "Sign out".

The page only reads data. Each block loads and fails on its own, with an
inline alert in the block that failed.
