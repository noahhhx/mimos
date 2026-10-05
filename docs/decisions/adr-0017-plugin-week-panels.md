# ADR-0017: Week panels, and plugins that remember their users

- Status: Accepted
- Date: 2026-10-05
- Amends: [ADR-0006](adr-0006-plugin-system.md) (additive, within `/v1`),
  [ADR-0008](adr-0008-visual-direction.md) (one animation)

## Context

The owner reworked Country of the Week into a wheel:

- On the plan page, a collapsed section per week. Expanding it shows a
  wheel; spinning it reveals a country.
- After a spin the user can **choose** the country (it is locked to the
  week), **skip** it (it stays on the wheel), or **remove** it from the
  wheel for good.
- The wheel holds every country. A country chosen for a week never comes
  up again, and a spin never lands on the continent of the previously
  chosen country.
- Choosing a country adds no meals. Recipe suggestions for it are a
  separate, optional step.
- A week with a country shows its flag.

ADR-0006 cannot express this. Its plugins are stateless functions of a
context that holds no identity, so a plugin cannot remember which
countries a user has used. Its only UI is a read-only suggestion card, so
there is no wheel and no button besides "add these meals". And per
AGENTS.md, a third party must be able to build this plugin without
changing core.

Three architectures were put to the owner: core stores an opaque state
document per user and plugin; the plugin keeps its own database keyed by
a pseudonym core gives it; or the feature moves into core. **The owner
chose the plugin's own database.**

## Decision

### Core gives each plugin a pseudonym per user

For every request it makes on a user's behalf, core sends a `subject`: a
random UUID, created the first time core calls that plugin for that user
and stored in `plugin_subject (profile_id, plugin_id, subject)`, owned by
`integrations/plugins`. It is unrelated to the Keycloak subject and to
the profile id, and each plugin gets a different one, so two plugins
cannot join their data on it. It does not change when the user turns the
plugin off and on again, so a plugin's memory survives a mistaken click.

`subject` is added to the plan-suggestions context (optional in the
schema, always sent by this core) and is required in the new week-panel
request. The plugin may store whatever it wants against it. That is the
plugin's data, in the plugin's storage: core neither exports it
(ADR-0011) nor deletes it.

### A second capability: `week-panel`

`POST {base}/v1/week-panel` receives `{subject, weekStartDate, action?}`
and answers with a declarative panel:

```json
{
  "summary": {"icon": "🇵🇪", "label": "Peru"},
  "blocks": [
    {"type": "highlight", "icon": "🇵🇪", "title": "Peru", "text": "South America"},
    {"type": "actions", "actions": [{"id": "change", "label": "Change country"}]}
  ]
}
```

- **Without `action`** the plugin renders the week as it stands and must
  change nothing. **With `action`** (`{id, value?}`, both taken from an
  `actions` block the plugin rendered earlier) it acts, then renders.
- **`summary`** is the line shown while the panel is collapsed. Country
  of the Week sends the chosen country and its flag, which is how a
  locked-in week shows its flag.
- **Blocks** are a closed vocabulary that core validates and web
  renders as plain text: `text`, `highlight` (icon, title, text),
  `wheel` (2–60 segments of label and icon, and an optional `landing`
  index the wheel spins to), and `actions` (buttons). Unknown block
  types and invalid blocks are dropped, so a newer plugin degrades on an
  older core. Limits are in the contract.

The plugin still never calls Mimos. Core calls it only for a user who
turned it on (ADR-0013); action ids and values pass through the browser,
so the plugin treats them as untrusted, and the only data a forged
action can reach is the caller's own.

### Core API: `/api/v1/plans/{startDate}/panels`

- `GET …/panels` renders every panel the caller turned on, in
  registration order. A plugin that fails, times out, or answers
  malformed JSON is left out, as with suggestions.
- `POST …/panels/{pluginId}/actions` sends one action and returns the
  re-rendered panel. 404 when the plugin is not available or the caller
  has not turned it on; 502 problem when the plugin fails, because the
  user pressed a button and must hear that it did nothing.

### Randomness lives in the plugin

The spin is the plugin's decision: it draws from the eligible countries
and returns a `wheel` block with `landing` set. The web app only
animates to the index it was given. The plugin's random source is
injected, so its tests are deterministic.

### Country of the Week keeps its state in SQLite

The reference plugin stores choices and removals with `node:sqlite`
(built into Node, so the plugin keeps zero npm dependencies) in a file
on a `country-week-data` volume. Its plan-suggestions card now follows
the week's chosen country: no country, no card.

### The wheel spins

The one exception to "no transitions or animations" (ADR-0008): the
wheel turns for about three seconds when it has a `landing`. Under
`prefers-reduced-motion: reduce` it lands at once.

### Flags render everywhere

Windows draws flag emoji as two letters. The web app ships a subset of
Twemoji's country flags (CC-BY 4.0) as a committed font limited to the
regional indicator range, so a flag icon from any plugin renders as a
flag. The Keycloak theme and the docs site don't need it.

## Consequences

- A plugin can now hold data about a user, under a pseudonym. The plugin
  authoring docs say so, and the Plugins page says turning a plugin on
  lets it remember things about you.
- Self-hosters running Country of the Week keep a volume for it. Losing
  the volume forgets every user's wheel, which is all it holds.
- The Kitchen home's "thought" from Country of the Week appears only for
  a week with a chosen country. The `kitchen-home` scenario and the CI
  smoke choose one first.
- Still deferred from ADR-0006: the plugin→Mimos callback direction,
  iframe UI slots, card persistence, a plugin directory.
