# Kitchen home: plan

**Status:** done. Built on the [Evening Kitchen restyle](index.md)'s
tokens, fonts and rail layout, as a separate change because it adds
behavior (new data on the home page). The page is
`apps/web/src/app/app/page.tsx`; its choices (greeting, tonight, the week,
the thought, still to buy) are pure functions in `src/lib/kitchen.ts`.
Where building changed a rule below, the rule says what was built.

## Why

The signed-in home (`apps/web/src/app/app/page.tsx`, "Your kitchen") is
currently a list of quick links plus a profile card. It was the proof that
the vertical slice works, not a home. The [mockup](evening-kitchen.html)
replaces it with the answer to "what am I cooking tonight?": the day's
dinner up front, the week in the rail, what's left to buy, and a plugin
suggestion for an empty evening.

## What it shows

On the left, in the rail (top to bottom):

1. The date ("Friday, 2 October"), in the user's locale and time zone.
2. A greeting: "Good morning.", "Good afternoon." or "Good evening.",
   upright serif `h1`, no italics.
3. The amber rule.
4. **This week**: Monday to Sunday. Each day shows its dinner as a link to
   the recipe, or "Nothing yet" in muted text. Today is marked with the
   amber bar and amber day label.

In the main column:

1. **Tonight**: today's dinner in a `--surface` panel with an amber top
   rule. It shows the recipe title (large serif), its description, three
   facts (total minutes, servings, kcal per serving), a "Start cooking"
   button (to the recipe) and a "Swap meal" button (to the plan page for
   this week).
2. Two columns below it:
   - **Still to buy · N of M** (N unchecked of M items): at most five
     rows, unchecked items first and checked ones, struck through,
     filling what's left, with a link to the full list.
   - **A thought for {day}**: the first plugin suggestion card with a
     dinner entry on an open evening **from today on** (past days are
     never offered). It keeps the card's own title and blurb, names the
     proposed recipe ("{recipe} would fill the open evening."), the
     attribution "from {pluginName}" with its icon code, and an "Add to
     {day}" button that adds **that one entry**, not the whole card (the
     plan page's Suggestions panel still applies whole cards).
3. **Profile**: a small, quiet section at the bottom with name and member
   since (`dl.profile`, without the subject ID it used to show), plus
   "Sign out".

## Data: existing endpoints only

No API or contract changes. For the current week (`mondayOf(today)`):

| Need | Call | Notes |
| ---- | ---- | ----- |
| Week and tonight | `getMealPlan(startDate)` | Created empty on first access. Entries carry `date`, `mealType`, `recipeId`, `recipeTitle`, `servings`. |
| Tonight's details | `getRecipe(recipeId)` | Covers own and library recipes. Only called when tonight has a dinner. Minutes = `prepMinutes + cookMinutes` (show whichever is present). |
| Shopping | `getShoppingList(startDate)` | **404 means not generated yet.** Then show "No list yet" with a link that generates it on the shopping page. Don't generate from the home page; a GET-only home has no side effects. |
| Suggestion | `getPlanSuggestions(startDate)` | An empty list is normal (no plugins). Then hide the block, don't show an empty state. |
| Profile | `getMe()` | As today. |

Fire the independent calls in parallel. Each block loads and fails on its
own: one failing call shows an inline `role="alert"` in that block and
leaves the rest of the page usable.

Applying the suggestion reuses `addMealPlanEntry`, exactly as the plan
page's Suggestions panel does (ADR-0006: no new mutation path). Both pages
call `applySuggestionEntries` in `src/lib/suggestions.ts`.

Country of the Week fills open dinners Monday forward and knows nothing
of today (its suggestions are clock-free by design), and the seeded
library matches one or two recipes per country. So on a week whose early
evenings are open, its card proposes only past days and the home page
shows no thought; it appears once the earlier evenings are planned. A
plugin-side change (for example, sending today in the context) would be
an extension-API change under ADR-0006, so it is left for later.

## Rules and edge cases

- **"Tonight" = today's `DINNER` entry.** If today has several dinners,
  take the first. If there's no dinner but other meals are planned today,
  show the latest main meal under the label "Today": lunch, then
  breakfast, then a snack. If nothing is planned, show an empty panel:
  "Nothing planned for tonight", with a primary button "Plan tonight"
  linking to the plan page.
- **Today is the local date.** `todayIso()` in `src/lib/format.ts` uses
  `toISOString()`, which is UTC, so it is wrong in the evening west of UTC
  and in the early morning east of it. Since the home page is "tonight",
  add a local-date helper (or fix `todayIso` and check its one caller,
  `app/app/log/page.tsx`) with a unit test covering a time near midnight.
  Built: `todayIso(now?)` now returns the local date, which also fixes the
  log page's default day.
- **Greeting by local hour:** morning 05:00–11:59, afternoon 12:00–17:59,
  evening otherwise. Put it in a pure function in `src/lib/` that takes a
  `Date` (time injected, per AGENTS.md), and unit-test the boundaries with
  `node --test`.
- **Week list order is by date**, Monday first, matching `weekDays()`. Show
  only dinners in the rail; other meals stay on the plan page.
- Signed-out users still see the current "Sign in" prompt, restyled.

## Tests and scenarios

- Unit tests (`apps/web/test/`, `node --test`) for the greeting, the local
  date, and picking "tonight" from a list of entries. Keep the picking
  logic in a small module that doesn't touch Next or the browser, so it
  can be tested this way.
- **Scenarios that change.** `tools/harness/scenarios/fixtures.ts` waits for
  the heading "Your kitchen" after login, and `login.spec.ts` checks the
  "Profile" heading and `dl.profile`. The greeting replaces "Your kitchen"
  as the `h1`. Built: the login fixture waits for the "This week" heading,
  `app-pages.spec.ts` matches the greeting, and the "Profile" heading and
  `dl.profile` stay, so `login.spec.ts` holds unchanged.
- **New scenario** `kitchen-home.spec.ts`. Built as: plan every open
  evening before today through the API (so the plugin's card reaches
  today, see above), open `/app` and expect the empty Tonight panel, add
  the Country of the Week thought to tonight, then expect it under
  Tonight and on today's row of the week, also after a reload. It removes
  every entry it planned, and fails with a clear message if tonight
  already has a dinner.

## Done when

- `/app` matches the mockup's Kitchen home at 1280px and 390px in both
  themes.
- Each block's empty and error states render. You can check them by
  stopping the plugin container and using an unplanned week.
- Unit tests, typecheck, build and every harness scenario pass, including
  the new one.
- This page is marked done. If any rule above changed while building, the
  page records the final rule.
