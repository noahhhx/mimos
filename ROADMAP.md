# Mimos — Roadmap

The ordered list of steps from empty repo to a running product. Each step
gets fleshed out in detail when we pick it up — this file is the spine, not
the muscle. Steps are ordered by dependency, not calendar.

Rules for using this file:

- One step at a time; a step isn't done until its "done when" holds.
- When a step reveals a decision, record it in `AGENTS.md` (and an ADR if
  significant) in the same change — don't leave decisions trapped here.
- Re-order or add steps freely as we learn; keep the reasoning honest.

## Phase 0 — Groundwork (make the repo a place where work can happen)

1. **Build skeleton.** Maven multi-module scaffold (parent, `apps/api`,
   stubs for `core-recipes`/`core-planning`) with a Spring Boot app that
   boots and serves a health endpoint. Register verification commands in
   AGENTS.md. MkDocs scaffold (`mkdocs.yml`, `docs/`) so doc changes have
   somewhere to live from day one.
   *Done when: `mvn verify` passes, `mkdocs build --strict` passes, and
   both run in minimal CI.* **Done.**

2. **Walking skeleton in compose.** `deploy/docker` brings up Postgres,
   Keycloak (realm export checked into `deploy/keycloak`), and the API
   container; Flyway runs on startup; the API connects to both. This is
   prime directive #1 proven early, not retrofitted.
   *Done when: `docker compose up` from a clean machine reaches a healthy
   stack with migrations applied.* **Done.**

3. **CI hardening.** Extend CI to build all images and boot the compose
   stack on every PR — the self-host parity check becomes automated.
   *Done when: a PR that breaks the compose path cannot merge.* **Done.**

## Phase 1 — The vertical slice (all the boring infrastructure, once)

4. **Auth on the API.** Spring Security as an OIDC resource server against
   Keycloak; roles from realm/client roles; lightweight user profile keyed
   by subject ID, created on first authenticated request.
   *Done when: a token from compose Keycloak reaches a protected endpoint,
   and unauthenticated requests are rejected with problem-details.*
   **Done.**

5. **API contract pipeline.** OpenAPI spec as the source of truth: spec
   first, server stubs verified against it, generated TypeScript client
   published to `libraries/`. Contract drift fails CI.
   *Done when: changing the spec regenerates client and server sides, and
   editing code without the spec fails the build.*
   **Done.** See ADR-0003 — `contracts/api/openapi.yaml`, generated server
   interfaces in `apps/api`, `@mimos/api-client` in `libraries/`, drift
   gates in CI.

6. **Frontend skeleton.** `apps/web` in Next.js: a public page (SSG) and an
   authenticated app shell, login/logout through Keycloak, talking to the
   API only via the generated client. Runs as a Node container in compose.
   *Done when: a user can log in on the compose stack and see an authed
   page backed by a real API call.*
   **Done.** See ADR-0004 — verified end to end (browser login → token →
   `/api/v1/me` → profile rendered).

## Phase 2 — Core product (each step: schema, API, UI, tests)

7. **Recipes.** Recipe, ingredient, and nutrition model; curated library
   recipes and personal recipes with the same richness; browse/search;
   public recipe pages for the library (SEO).
   *Done when: a personal recipe can be created, edited, cooked from, and
   a library recipe renders publicly.* **Done.** See ADR-0005 — recipe
   schema (V3), personal CRUD `/api/v1/recipes`, unauthenticated library
   read `/api/v1/public/recipes`, cook view with tickable steps in the app,
   public `/recipes/[slug]` pages server-rendered for SEO.

8. **Meal planning.** Plan meals for the week from library and personal
   recipes.
   *Done when: a week can be planned and persists across devices/logins.*
   **Done.** Plans keyed by owner + Monday (V4); week grid UI with a
   recipe picker over library + personal recipes; per-user isolation
   verified end to end (compose login → plan → same plan from another
   device, cross-user 404s tested).

9. **Shopping lists.** Plans generate shopping lists: quantities aggregated
   across recipes, sensible grouping, check-off in the store (mobile-usable
   web).
   *Done when: planning a week produces a complete, usable shopping list.*
   **Done.** Generation aggregates by (normalized name, unit) scaled by
   planned servings, groups by aisle keyword map, preserves check-off
   across regeneration (V5); mobile-friendly list UI.

10. **Calorie and macro logging.** Planned meals become logged meals;
    ad-hoc logging; daily and weekly calorie/macro totals.
    *Done when: a planned week shows accurate per-day totals, and an ad-hoc
    meal can be logged.* **Done.** “Log” button on planned meals (nutrition
    derived from recipe × servings), ad-hoc logging with manual macros,
    per-day/week summaries (V6); week table + day view in the UI.

11. **Seed the library.** The free recipe content itself: sourcing/writing
    the launch set, plus the pipeline for adding more over time.
    *Done when: a new user's first session shows a real, cookable library.*
    **Done.** 13 cookable recipes in `library-seed.json` loaded by an
    idempotent startup seeder (flag-gated); CI compose smoke asserts the
    library is seeded and the public page renders.

## Phase 3 — Extensibility (prove the plugin surface)

12. **Plugin system design (ADR).** Extension points, manifest format,
    registration, versioning, and the security model — designed against
    the country-picker use case and proposed before implementation.
    *Done when: the ADR is accepted and the recipe/planning APIs are judged
    against it.* **Done.** ADR-0006 — plugins are HTTP sidecar services,
    pull-only in v1 (the `plan-suggestions` capability: context snapshot
    in, declarative cards out), registered via instance config with a
    manifest at `GET /manifest`, library-only context, acceptance reusing
    the existing plan-entry endpoint. The phase-2 APIs were judged against
    it: one additive endpoint (`GET /api/v1/plans/{startDate}/suggestions`),
    no breaking changes, no plugin access to existing endpoints.

13. **Country of the Week.** The reference plugin, built exactly as a third
    party would: no core changes, installable per-instance by the owner.
    *Done when: the plugin runs in compose, suggests a country, and leans
    the week's plan into that cuisine.*
    **Done.** The extension surface from ADR-0006 was implemented first
    (the `integrations/plugins` runtime, the additive
    `GET /api/v1/plans/{startDate}/suggestions` endpoint, the web
    Suggestions panel); the plugin itself (`plugins/country-week`) was then
    built against it without touching the domain modules — a
    zero-dependency TypeScript service on `node:http` that picks a country
    deterministically from the week and fills the week's unplanned dinners
    with matching library recipes. It runs in the default compose stack
    (API → plugin → validated, attributed card), is registered purely via
    `mimos.plugins` env config, and the CI compose smoke asserts a
    suggestion appears end to end. Plugin authoring docs:
    `docs/plugins/`.

## Phase 4 — Scale and sync

14. **Intervals.icu integration.** OAuth, sync, translating activity data
    into domain types at the edge; training-aware meal plan suggestions
    against calorie/macro goals. Feature-gated and optional at runtime.
    *Done when: a self-hoster without intervals.icu loses nothing, and a
    connected user's plan suggestions account for training load.*

15. **AWS deployment.** Resolve the deferred decisions (compute, IaC tool),
    stand up `deploy/aws`, with the self-hosted path still the CI-checked
    default.
    *Done when: the stack runs on AWS from IaC alone, no click-ops, and the
    compose path is untouched.*

16. **Self-host release readiness.** Versioned images, documented upgrade
    path (pull and restart), backups story, and the plugin authoring docs.
    *Done when: a stranger can self-host Mimos from the docs alone.*
    Per-user export/import is done (ADR-0011, the web app's "Your data"
    page); the instance-level backup story (database and Keycloak) is
    still open.
