# ADR-0006: Plugin system — HTTP sidecar plugins, pull-only in v1

- Status: Accepted
- Date: 2026-09-09
- Supersedes: none

## Context

Phase 3 (roadmap step 12) designs the plugin surface before any of it is
built, judged against the reference use case: **Country of the Week** —
a plugin that suggests a country and leans the week's meal plan into
that cuisine. The constraints were fixed when the architecture was
decided (AGENTS.md): plugins extend Mimos without modifying or
rebuilding core; they are installable per-instance by the instance owner
(a self-host parity feature); they are containers; their API is
versioned from day one; and plugins never get database access.

Four forks in the design were put to the owner and decided:

1. **UI:** declarative suggestion cards rendered by core/web — plugin
   code never runs in the browser (no iframes in v1).
2. **Direction:** pull-only in v1 — Mimos calls the plugin with
   everything it needs; plugins do not call Mimos.
3. **Privacy:** plugins see curated library recipes only; personal
   recipe data never leaves core via a plugin.
4. **Registration:** the config registry — plugins are declared in
   instance configuration, not installed through an admin API.

## Decision

### Plugins are HTTP sidecar services

A plugin is an HTTP service the instance owner runs — a container next
to `mimos-api`. Any language that can serve JSON over HTTP can host a
plugin; a plugin container is trivially self-hostable and naturally
microservice-shaped if we ever split. The plugin runtime (registry,
outbound client, card validation) lives in a new `integrations/plugins`
Maven module that depends on the core modules' public interfaces only —
the same seam `integrations/intervals-icu` will use. HTTP controllers
stay in `apps/api` as contract adapters (ADR-0005's rule).

### The manifest declares identity and capabilities

Each plugin serves `GET {base}/manifest`:

```json
{
  "schema": "mimos.plugin.manifest/v1",
  "id": "country-week",
  "name": "Country of the Week",
  "version": "1.2.0",
  "apiVersions": ["1"],
  "capabilities": ["plan-suggestions"],
  "homepageUrl": "https://example.org/mimos/country-week"
}
```

The API validates manifests at startup. Unknown capabilities are ignored
with a warning — a newer plugin on an older core degrades rather than
fails — while duplicate plugin ids across the registry are a
configuration error that fails startup: static misconfiguration should
be caught loudly, at boot. A plugin *unreachable* at startup is never
fatal; it is marked unavailable, logged, and re-fetched lazily when a
fan-out fails, so a plugin that starts after the API is still picked up.

### Registration is instance configuration

```yaml
mimos:
  plugins:
    - id: country-week            # optional; must match the manifest when set
      url: http://country-week:8080
      shared-secret: ${COUNTRY_WEEK_SECRET:}   # optional
      timeout: 2s                 # optional per-plugin override
```

Enabled means registered; changes take effect on restart (12-factor
config, no hot reload in v1). Plugin URLs must be http(s) and come from
owner configuration, never user input — that is the SSRF boundary. An
optional static bearer secret covers plugins hosted off the local
network; local-network trust is the default.

### One capability in v1: plan-suggestions

`GET /api/v1/plans/{startDate}/suggestions` (authenticated, the plan
owner) is the only core endpoint addition. It triggers a fan-out.

**1. Context assembly (core → plugin).** Each plugin receives:

```json
{
  "weekStartDate": "2026-09-07",
  "plannedSlots": [
    {"date": "2026-09-07", "mealType": "DINNER", "servings": 2, "recipeId": "…"}
  ],
  "libraryRecipes": [
    {"id": "…", "title": "Spaghetti al Pomodoro", "tags": ["italian", "vegetarian"], "servings": 4}
  ]
}
```

`plannedSlots` carries a `recipeId` only where the planned recipe is
itself a library recipe — personal recipes contribute their shape
(slot, servings), never their identity.

**2. Fan-out.** `POST {base}/v1/plan-suggestions` to every registered
plugin declaring the capability, with the per-plugin timeout (default
2s) and optional bearer secret. A timeout, non-2xx response, or
malformed body means that plugin contributes nothing this request; the
failure is logged and never user-visible. One plugin's failure never
affects another's.

**3. Validation (core).** Plugin cards are untrusted input: title
required, ≤ 80 chars; blurb ≤ 200; icon ≤ 8 (a short glyph — a flag
emoji, an initialism); ≤ 5 cards per plugin, ≤ 7 entries per card.
Every entry's date must fall in the plan week, mealType must be valid,
servings must be in (0, 100] — the same bound `MealPlanService`
enforces — and recipeId must be a library recipe from the catalog that
was sent. Invalid entries are dropped; a card left with no entries is
dropped.

**4. Response (core → web).** Cards merged in registration order, each
attributed to its plugin, entries hydrated with core-owned recipe
titles (so per-entry text in the UI is never plugin-supplied):

```json
{
  "suggestions": [
    {
      "pluginId": "country-week",
      "pluginName": "Country of the Week",
      "title": "Italy week",
      "blurb": "Two classics and a weeknight traybake.",
      "icon": "IT",
      "entries": [
        {"date": "2026-09-07", "mealType": "DINNER", "recipeId": "…", "recipeTitle": "Spaghetti al Pomodoro", "servings": 2}
      ]
    }
  ]
}
```

The endpoint returns 200 with an empty list when no plugins are
registered or all fail — suggestions are an enhancement, never a
dependency.

### Cards are advisory; acceptance reuses existing APIs

Suggestion cards are ephemeral (recomputed per request, never stored)
and carry no write power. Applying a card is the web app calling the
existing `POST /api/v1/plans/{startDate}/entries` per entry — the same
authorization (owner-only), the same validation, no new mutation path.
Core never calls a plugin on any write path, so in v1 a plugin cannot
mutate anything; the only actor with write power is the user.

### Plugins see the library, never personal data

The context contains the curated catalog (id, title, tags, servings)
and the shape of the planned week. Personal recipe content, profiles,
identities, and logs never cross the plugin boundary. The context is
the whole library, which is compact at today's scale; a filtered or
capped variant would be an additive future change if the library grows.

### The extension API is a versioned, contract-first surface

The manifest schema (`mimos.plugin.manifest/v1`) and the capability
endpoint (`/v1/plan-suggestions`) are path-versioned public contracts:
additive changes only, breaking changes get a new version, deprecations
come with notice. The contract is specified as OpenAPI under
`contracts/plugins/` — the ADR-0003 pipeline applied to the plugin
surface — so core and plugins generate from one source. The plugin SDK
promised in the repo layout starts as those generated TypeScript types;
a friendlier SDK is extracted when a second plugin exists to justify it.

## The phase-2 API surface, judged against the country-picker

Step 12's gate: can the reference plugin be built without core changes
beyond the additions above?

- **Read recipe metadata/tags** — yes, core-side. The context is
  assembled from `RecipeService`'s public interface, and tags have been
  required fields in `RecipeSummary` since phase 2 — exactly the
  cuisine signal the country-picker needs. Pull-only means the plugin
  itself needs no read access to the Mimos API at all.
- **Propose a plan suggestion** — the shape already exists: a proposed
  entry is the `MealPlanEntryInput` tuple (date, mealType, recipeId,
  servings), and acceptance reuses the existing entry endpoint, so
  week/servings/visibility rules and authorization stay single-sourced
  in `MealPlanService`.
- **Surface a small UI affordance** — the one addition:
  `GET /api/v1/plans/{startDate}/suggestions`. Additive, authenticated
  (it sits next to personal plan data), using the existing `PlanWeek`
  parameter and `Problem` error vocabulary.

Verdict: the phase-2 surface holds — one additive endpoint, no breaking
changes, no plugin access to existing endpoints.

## Consequences

- Step 13 implements: the `contracts/api` addition (0.3.0); the
  `integrations/plugins` module with fan-out tested against a real
  local HTTP server; the web suggestions panel (card fields rendered as
  plain text — plugin strings are untrusted, attributed, and
  length-bounded); the `plugins/country-week` service with its own
  Dockerfile and compose service, running in the default stack and
  removable by deleting its service and env lines; a compose smoke
  asserting a suggestion appears end to end.
- With no plugins registered the product is unchanged: the endpoint
  returns an empty list and the panel is hidden — self-hosters lose
  nothing by ignoring plugins entirely.
- The instance owner's plugin choice is a trust decision the product
  makes legible: cards are attributed by plugin name, and the plugin
  authoring docs must state plainly what data plugins receive.
- Deferred (designed here, built only when a plugin needs it): the
  plugin→Mimos callback direction with Keycloak service accounts and
  read-only scopes; iframe UI slots; per-user plugin consent and
  opt-out; card dismissal/persistence; a plugin directory.
