# Writing a Mimos plugin

A Mimos plugin is an HTTP sidecar service the instance owner runs next to
`mimos-api`. Mimos calls out; the plugin never calls back, holds no
tokens, and cannot write anything. The wire format is specified as
OpenAPI in
[`contracts/plugins/v1/`](https://github.com/noahhhx/mimos/tree/main/contracts/plugins/v1),
and TypeScript plugins can compile against the generated types in
[`@mimos/plugin-sdk`](https://github.com/noahhhx/mimos/tree/main/libraries/plugin-sdk).
The design behind it, with the reasoning, is
[ADR-0006](https://github.com/noahhhx/mimos/blob/main/docs/decisions/adr-0006-plugin-system.md).

Any language that can serve JSON over HTTP works. The reference plugin,
[Country of the Week](https://github.com/noahhhx/mimos/tree/main/plugins/country-week),
is plain TypeScript on `node:http` with zero runtime dependencies — it
runs straight from source on Node's native type stripping.
[Country of the Week](country-week.md) walks through it, and is the
quickest way to start your own.

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

Mimos calls your plugin only for users who turned it on in the app's
Plugins page (see [Plugins](../guide/plugins.md)); every plugin starts off
for every user. For those users, the context is
the entire data surface in v1:

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

The owner runs your plugin next to Mimos and registers it with
environment variables on the API (see
[Adding a plugin](../self-hosting.md#adding-a-plugin)), or the equivalent
Spring configuration:

```yaml
mimos:
  plugins:
    - id: my-plugin        # optional; must match the manifest when set
      url: http://my-plugin:8080
      shared-secret: ...   # optional, for plugins off the local network
      timeout: 2s
```

With a shared secret set, Mimos sends it as a bearer token on every call,
so your plugin can refuse anyone else.

Registering makes the plugin available; each user then turns it on in
the Plugins page, which lists it by its manifest name and links to its
`homepageUrl` (an absolute http(s) URL; anything else is dropped).
Changes to the registration take effect on restart. A misconfigured
plugin (a duplicate id, an id that does not match the manifest) fails
startup loudly; an unreachable or slow plugin only logs and contributes
nothing — it can never take the instance down.

## Not yet available

These are designed but not built until a plugin needs them: calling back
into Mimos (service accounts), iframe UI slots, persisted or dismissible
cards, and a plugin directory. If your
plugin needs one of these, open an issue — that is the signal to build it.
