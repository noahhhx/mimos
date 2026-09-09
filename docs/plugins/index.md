# Writing a Mimos plugin

A Mimos plugin is an HTTP sidecar service the instance owner runs next to
`mimos-api`. Mimos calls out; the plugin never calls back, holds no
tokens, and cannot write anything. The design contract is
[ADR-0006](../decisions/adr-0006-plugin-system.md); the wire format is
specified as OpenAPI in `contracts/plugins/plan-suggestions/v1/` and the
TypeScript types plugins compile against are generated into
`libraries/plugin-sdk` (`@mimos/plugin-sdk`).

Any language that can serve JSON over HTTP works. The reference plugin,
[Country of the Week](https://github.com/noahhhx/mimos/tree/main/plugins/country-week),
is plain TypeScript on `node:http` with zero runtime dependencies — it
runs straight from source on Node's native type stripping.

## What your plugin serves

Two endpoints:

- `GET /manifest` — identity and capabilities. The `schema` is
  `mimos.plugin.manifest/v1`; declare the `plan-suggestions` capability
  and API version `1`. Mimos validates the manifest at startup.
- `POST /v1/plan-suggestions` — Mimos sends a context snapshot and you
  respond with suggestion cards. Respond within the instance's timeout
  (default 2s); a failure means you contribute nothing for that request —
  never an error for the user.

## What data your plugin receives

The context is the entire data surface in v1:

- `weekStartDate` — the Monday of the week being planned.
- `plannedSlots` — meals already in the plan: date, meal type, servings.
  A `recipeId` appears only where the planned recipe is a curated
  **library** recipe — personal recipes contribute their shape, never
  their identity or content.
- `libraryRecipes` — the curated library catalog: id, title, tags,
  servings. This is the only recipe data a plugin ever sees. Profiles,
  identities, personal recipes, and logs never cross the plugin boundary.

## What a card may contain

Cards are declarative and validated by the server before a user sees
them: title (required, ≤ 80 characters), blurb (≤ 200), icon (≤ 8, a
short glyph), and up to 7 entries per card, 5 cards per response. Each
entry must fall inside the plan week, use a valid meal type, reference a
library recipe from the context, and carry servings in (0, 100]. Invalid
entries are dropped; a card left with no entries is dropped.

Cards are advisory only. Applying one is the user acting through the
existing plan-entry endpoint — your plugin has no write path of any
kind, and its cards are always attributed with your manifest name.

## How an instance enables your plugin

The owner registers it in configuration (compose env or Spring config):

```yaml
mimos:
  plugins:
    - id: my-plugin        # optional; must match the manifest when set
      url: http://my-plugin:8080
      shared-secret: ...   # optional, for plugins off the local network
      timeout: 2s
```

Enabled means registered; changes take effect on restart. A misconfigured
plugin (a duplicate id, an id that does not match the manifest) fails
startup loudly; an unreachable or slow plugin only logs and contributes
nothing — it can never take the instance down.

## Not yet available (designed in ADR-0006, built when a plugin needs it)

Calling back into Mimos (service accounts), iframe UI slots, per-user
consent, persisted or dismissible cards, a plugin directory. If your
plugin needs one of these, open an issue — that is the signal to build it.
