# ADR-0013: Plugins are opt-in per user

- Status: Accepted
- Date: 2026-10-05
- Amends: [ADR-0006](adr-0006-plugin-system.md) (builds its deferred
  "per-user plugin consent and opt-out")

## Context

ADR-0006 made registration the only switch: a plugin the instance owner
registers is called for every user, and its cards appear on everyone's
Kitchen home and plan page. Two things are wrong with that once an
instance has more than one person on it:

- **Consent.** Each call sends the user's plan context to the plugin:
  the library, and the shape of their week (which slots are planned,
  servings, and the ids of planned library recipes). The owner's trust
  in a plugin is not the user's consent to share their week with it.
- **Control.** Suggestions a user did not ask for show up on their
  screens, and they have no way to turn them off.

ADR-0006 already designed per-user consent and deferred it. The owner
decided to build it now, as opt-in from a Plugins page linked from the
profile menu.

## Decision

### Registration makes a plugin available; each user turns it on

The owner's configuration (`mimos.plugins.*`, unchanged) decides which
plugins are *available* on the instance. Every plugin starts **off** for
every user, including the ones already using the instance when this
ships. A user turns a plugin on, or back off, on the Plugins page
(`/app/plugins`), linked from the profile menu in the site header.

A plugin a user has not turned on is never called with that user's
context; the check is in `SuggestionService`, before the context is
assembled, so no caller of the runtime can skip it. A user with nothing
turned on gets the empty suggestions list ADR-0006 already defines, and
the plugin sees no request at all.

### Storage: one row per plugin a user turned on

`plugin_opt_in (profile_id, plugin_id, enabled_at)`, owned by
`integrations/plugins` (`PluginOptInService`). A row means on; no row
means off. Plugin ids come from instance configuration, not a table, so
they carry no foreign key: a row may outlive its plugin's registration,
and is ignored until a plugin with that id is registered again (it is
then on again for that user, which is what they last chose).

### API: `/api/v1/me/plugins`

- `GET /api/v1/me/plugins` lists the plugins a user can turn on (resolved
  manifest, current extension API version, at least one capability this
  Mimos calls), in registration order, with `id`, `name`, `homepageUrl`,
  and the caller's `enabled`. An unreachable plugin is left out until it
  answers again.
- `PUT /api/v1/me/plugins/{pluginId}` with `{"enabled": true|false}`
  answers 204. Turning on needs the plugin to be available (404
  otherwise); turning off always succeeds, so a choice can be withdrawn
  while the plugin is down or after it was removed.

The plugin extension API (`contracts/plugins/`) does not change: plugins
cannot tell opt-in exists, except that fewer users' requests reach them.

### Web: a Plugins page, linked from the profile menu

The profile menu links to `/app/plugins`, a page of its own rather than
toggles in the menu, so it holds any number of plugins and has room to
explain them. It says what plugins are in general terms (add-ons the
instance owner installed), not what today's one capability does, since
plugins will do more than suggest meals; then it lists the available
plugins as checkboxes, each with a link to its homepage when the manifest
has one. Each change saves at
once. The plan page's Suggestions panel links back to it.

A manifest's `homepageUrl` is plugin-supplied and becomes a link, so the
API drops any value that is not an absolute http(s) URL when it parses
the manifest (dropped, not fatal: it is cosmetic), and the web app checks
again before rendering it.

### Not in the account export

Opt-ins are left out of the account export (ADR-0011). They are consent
given to plugins on one instance; another instance runs other plugins,
or the same ids from someone else, so an import must not grant it.

## Consequences

- Existing users on an instance with plugins stop seeing suggestions
  until they turn a plugin on in the Plugins page. That is the intended
  default, not a regression.
- The CI compose smoke and the `kitchen-home` harness scenario turn
  Country of the Week on before asking for suggestions; the
  `plugin-opt-in` scenario covers the Plugins page with a fresh account.
- The plugin authoring docs say a plugin only hears about users who
  turned it on.
- Still deferred from ADR-0006: the plugin→Mimos callback direction,
  iframe UI slots, card dismissal and persistence, a plugin directory.
  An owner-side "on by default" switch is not built; add it as instance
  config if an owner needs it, keeping off as the default.
