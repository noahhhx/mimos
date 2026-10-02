# ADR-0009: Tell plugins which day it is

- Status: Proposed, for the owner to review
- Date: 2026-10-02
- Amends: [ADR-0006](adr-0006-plugin-system.md) (additive, within `/v1`)

## Context

The Kitchen home ([Visual design](../design/index.md#kitchen-home)) shows
"A thought for {day}": the first plugin card that proposes a dinner on an
open evening **from today on**. Proposals for evenings that have passed are
useless, so the page skips them.

In practice the block rarely appears mid-week. The plugin context
(ADR-0006) carries the week, its planned slots and the library, but not
the date. So Country of the Week fills open dinners from Monday onward, as
it must. The seeded library matches one or two recipes per country, so on
a Friday with an empty week its card proposes Monday and Tuesday and
nothing else. The home page has nothing to show, and the plan page's
Suggestions panel offers to fill two past evenings. The thought appears
only once every earlier evening is planned, which is also why the
`kitchen-home` scenario plans those evenings first.

This is not a bug in the plugin. Plugins are clock-free on purpose:
suggestions are pure functions of the context, so a week always suggests
the same thing and plugins are trivial to test. The plugin also could not
know the user's date, because its clock and time zone are the server's,
not the user's. Only the browser knows the user's local date.

## Options

1. **Send the user's date in the context (recommended).** The web app
   passes its local date when it asks for suggestions, and core forwards
   it to plugins as an optional `today`. Plugins that understand it treat
   evenings before it as closed. Plugins that don't keep working as they
   do now.
2. **Core marks past evenings as taken.** Core adds made-up `plannedSlots`
   for every slot before today, so plugins skip them without any contract
   change. This hides the date from plugins, but the context then
   misstates the plan. A plugin that counts planned meals or reasons
   about variety would be misled. It also still needs the date from the
   web app.
3. **Core drops past entries during validation.** With the date from the
   web app, core removes entries before today from every card. That stops
   past proposals from showing, but it doesn't give plugins the chance to
   use today's evening instead. Country of the Week would still spend its
   one or two recipes on Monday and Tuesday and then lose them. On its
   own this makes the block disappear rather than appear.
4. **Grow the library.** With seven or more recipes per country, the card
   reaches every open evening. That helps, but it is a content workaround
   for a contract gap, and every other instance's library has the same
   problem.
5. **Do nothing.** The block appears for people who plan their week
   ahead, which is what the planner encourages anyway.

## Proposed decision

Option 1, with option 3's filtering as a safety net:

- **Product API** (`contracts/api`, minor version bump): add an optional
  `today` query parameter (`YYYY-MM-DD`) to `GET
  /api/v1/plans/{startDate}/suggestions`. If it is malformed, the API
  answers 400. If it is missing, behavior is unchanged.
- **Plugin API** (`contracts/plugins/plan-suggestions/v1`, 1.0.0 → 1.1.0):
  add optional `today` to `SuggestionContext`. The field is optional, so
  under ADR-0006's rules it stays inside `/v1`. Its documentation says
  what it means: "the user's local date; slots before it have passed".
- **Core:** forward `today` when it was given. Also drop card entries
  dated before it during validation, so a plugin that ignores the field
  never puts a past evening in front of the user.
- **Web:** both the home page and the plan page send `todayIso()`.
- **Country of the Week:** treat dinners before `today` as taken. It stays
  clock-free, because the date arrives as input and the plugin never
  reads a clock.
- **Docs:** the plugin authoring guide lists `today` among the data
  plugins receive.

The date is injected the way AGENTS.md asks (no wall clock in domain
logic). The server never guesses a time zone, and the date's source is
the person who is looking.

## Consequences

- On an empty Friday, Country of the Week proposes Friday's dinner, and
  the Kitchen home shows "A thought for Friday".
- Plugins learn the user's current date. That is new information crossing
  the plugin boundary, though no personal data does: no identity, no
  recipes, no logs. The plugin authoring docs must say so, as ADR-0006
  requires.
- The same week can now get different suggestions on different days,
  which is the point. Plugins remain deterministic for a given context.
- The `kitchen-home` scenario no longer has to plan the earlier evenings
  first. It can expect the thought for tonight on an empty week.
- Older plugins and older cores keep working together, because the field
  is optional in both directions.

## Questions for the owner

1. Is the user's local date acceptable to send to plugins? It's the only
   new data, and it's coarse, but ADR-0006 treats the context as a trust
   boundary.
2. Should core also drop past entries (the safety net), or leave plugins
   fully in charge of their cards?
3. Should the plan page send `today` too? For the current week it would
   stop offering evenings that have passed. For a future week, such as
   planning next week on a Sunday, it changes nothing, because every
   evening is still ahead.
