# AGENTS.md

Read `NORTHSTAR.md` before making product decisions; read this before making
technical ones. This file is the source of truth for how Mimos is built.
**When a decision changes or a new one is made, update this file in the same
change** — an out-of-date AGENTS.md is worse than none.

## Prime directives (non-negotiable)

1. **Self-hosted parity.** `deploy/docker` must bring up the entire product
   (backend, frontend, auth, database) with no code changes. Any change that
   only works on AWS is rejected. CI runs the self-hosted path on every PR.
2. **Cloud-ready, not cloud-locked.** AWS is the scale target, so prefer
   choices that are portable: containerized services, Postgres, S3-compatible
   object storage, standard OIDC. AWS-native services are allowed when the
   underlying interface is portable (RDS) or hidden behind an internal
   abstraction; never when they leak into domain code.
3. **Plugins are a product surface.** Every significant design decision gets
   judged against: "could a third party build the country-picker plugin
   without modifying core?" (see below).
4. **Modular monolith by default.** We are open to microservices when a
   concrete need appears (independent scaling, plugin isolation, team
   boundaries). "It feels more scalable" is not a concrete need. Start with
   one deployable, strong internal module boundaries, and honest seams so
   extraction is possible later.
5. **Small, reviewable changes.** Agents work in increments: one feature or
   decision per change, tests included, docs updated. Ask before introducing
   any new framework, language, or top-level dependency.

## Decided — do not relitigate without asking the owner

| Area       | Decision                                                        |
| ---------- | --------------------------------------------------------------- |
| Backend    | Java 21 LTS, Spring Boot 4.x                                    |
| Frontend   | Next.js (App Router) with TypeScript. See "Frontend specifics". |
| Auth       | Keycloak via OIDC. Never build local/session auth. All users, roles, and tokens come from Keycloak. |
| Database   | PostgreSQL (latest stable). Schema changes via forward-only migrations (Flyway). |
| Repo       | Monorepo — all apps, plugins, libraries, and deployment in one repo. |
| Build      | Maven multi-module for the Java side.                           |
| Backend layout | Root parent POM + domain modules under `core/` + `apps/api` deployable. Base package `io.github.noahhhx.mimos`. See ADR-0001. |
| API style  | HTTP JSON APIs, contract-first: the OpenAPI spec in `contracts/api/openapi.yaml` is the source of truth; server stubs generate at build time and the TypeScript client in `libraries/api-client` is committed. See ADR-0003. |
| Config     | 12-factor: environment variables + Spring profiles. `local` profile is the default and must always work. |
| Runtime    | Docker. Every deployable (API, web, plugins) ships a Dockerfile that is built in CI; no bare-metal assumptions in app code. |
| Docs       | MkDocs; `mkdocs.yml` at repo root, source in `docs/`. ADRs live in `docs/decisions/`. |
| Deploy     | Local/self-host via `deploy/docker` (compose); AWS via IaC in `deploy/aws`. No click-ops. |
| Sync       | intervals.icu is the activity data source (future). Design for it, don't build it yet. |
| Library content | Seed file `core/core-recipes/src/main/resources/library/library-seed.json`, loaded by an idempotent startup seeder (`mimos.library.seed-enabled`, default on). Content fixes ship with a restart; never via Flyway migrations. See ADR-0005. |
| Public API | Unauthenticated read-only access to the curated library under `/api/v1/public/**` (SEO pages). Personal recipes never appear there; everything else requires a bearer token. |
| Plugins    | HTTP sidecar plugins, pull-only in v1: config registry (`mimos.plugins.*`), manifest at `GET /manifest`, one capability (`plan-suggestions`), declarative cards, library-only context. See ADR-0006. |
| Agent harness | `tools/harness` (`@mimos/harness`, TypeScript on Node type stripping) behind a devenv `harness` script; all agent tooling from devenv/nixpkgs; Playwright pinned to nixpkgs' `playwright-driver`; `.mcp.json` committed; debug-only behavior in `deploy/docker/compose.debug.yml`; scenarios run in CI via devenv. Plan and decisions in `docs/harness/`. |

## Open decisions — resolve with the owner before building against them

| Area                  | Status | Notes / decision criteria                                                                                                                              |
| --------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| AWS compute (ECS vs EKS vs other) | Deferred | Decide when we deploy. Do not let app code depend on the answer. |
| IaC tool (CDK vs Terraform) | Deferred | Decide with the AWS deploy work. |
| Object storage        | Deferred | When needed (recipe images): S3-compatible API only, MinIO in local compose. Treat as decided-in-principle, decided-in-detail-later. |
| Mobile                | Out of scope | Web must be responsive and mobile-usable so this stays cheap later. |

## Architecture guidance

### Module boundaries (the seams that make #4 possible)

Inside the backend, treat modules as if they were services with a
wire-format contract:

- `core-recipes` — recipes, ingredients, nutrition. Pure domain; no
  knowledge of integrations, plugins, or HTTP clients. Owns its tables'
  JDBC persistence; the curated library is seeded read-only content
  (ADR-0005).
- `core-planning` — meal plans, shopping lists, logging. Depends on
  recipes (through `RecipeService`'s public interface only — never its
  tables), nothing else. Owns its tables' JDBC persistence.
- `integrations/plugins` — the plugin runtime: registry, outbound
  extension-API client, suggestion-card validation (ADR-0006). Depends on
  the core modules' public interfaces only; external API types never cross
  this boundary. Optional at runtime — no plugins registered, no behavior.
- `integrations/intervals-icu` — future. External API types never cross
  this boundary; translate to domain types at the edge. Feature-gated and
  optional at runtime (self-hosters must not need it).
- `plugins` — see below.
- Modules communicate through their public interfaces only; no reaching into
  another module's internals or tables. Cross-module FKs (e.g.
  `meal_plan_entry.recipe_id`) are integrity constraints, not access paths.
  Flyway migrations live in `apps/api` (the deployable owns the schema).
  Enforce via Maven's explicit inter-module dependencies plus ArchUnit
  tests once scaffolded.

### Plugin system

The reference use case: **"Country of the Week" — suggests a country, and the
week's meal plan leans into that cuisine.** The design is decided; ADR-0006
is the contract:

- Plugins are HTTP sidecar services (containers) registered via instance
  config (`mimos.plugins.*`): enabled means registered, changes take effect
  on restart. Static misconfiguration fails startup; an unreachable plugin
  never does.
- v1 is pull-only: Mimos calls `POST {base}/v1/plan-suggestions` with a
  context snapshot (week, planned slots, library catalog); plugins never
  call Mimos and cannot write anything. Applying a suggestion reuses the
  existing plan-entry endpoint — no new mutation path.
- Plugins see curated library recipes only. Personal recipes, profiles,
  identities, and logs never cross the plugin boundary.
- UI is declarative suggestion cards (title/blurb/icon/entries), rendered
  by web as plain text and attributed to the plugin. No plugin code in the
  browser, no iframes in v1.
- The extension API is versioned from day one (`mimos.plugin.manifest/v1`,
  `/v1/plan-suggestions`), specified as OpenAPI under `contracts/plugins/`,
  and treated as a public contract: additive changes only, deprecation with
  notice.
- Plugins never get raw database access; the runtime lives in
  `integrations/plugins` and talks to core through public interfaces only.
- The reference plugin `plugins/country-week` is a zero-dependency
  TypeScript service on `node:http`, run straight from source via Node's
  native type stripping (engines node >= 22.18); plugin packages live in
  the `plugins/*` npm workspace, and its tests run on `node --test`.
- Deferred (designed in ADR-0006, built only when a plugin needs them): the
  plugin→Mimos callback direction with service accounts, iframe UI slots,
  per-user consent/opt-out, card persistence, a plugin directory.

### Frontend specifics

- `apps/web` is Next.js (App Router) with TypeScript, sources under `src/`.
  Public recipe pages render server-side (SSR + ISR revalidation) for SEO;
  the logged-in product is client-heavy.
- The frontend talks to `mimos-api` through the OpenAPI contract; the
  generated client lives in `libraries/` (`@mimos/api-client`), never
  hand-written per feature (ADR-0003).
- Authentication happens in the browser via `oidc-client-ts` as the public
  `mimos-web` client (Authorization Code + PKCE); the app calls the API
  directly with bearer tokens and the API allows CORS for the web origin
  (ADR-0004). No server-side sessions, no Next-auth.
- Public URLs (API, Keycloak, app) are `NEXT_PUBLIC_*` build args — see
  `apps/web/Dockerfile` and the compose `web` service. Changing them means
  rebuilding the web image.
- Runs as a standalone Node container in compose (non-root). No
  Vercel-specific features that break self-hosting; anything platform-tied
  is rejected on the same grounds as prime directive #2.
- Server-side fetches (public pages) reach the API through `API_SERVER_URL`
  (runtime env, default `http://api:8080` in compose) — distinct from
  `NEXT_PUBLIC_API_URL` so the browser-facing and container-facing URLs
  can differ without rebuilds.
- Logic a future mobile app would share (API clients, types, validation
  schemas) lives in `libraries/`, not in app code.
- Unit tests live in `apps/web/test/` and run on `node --test` (native type
  stripping, no test framework). A module they import must not touch the
  browser or Next, and imports its siblings with a `.ts` extension (Node
  resolves no others); keep such logic in small modules like
  `src/lib/api-fetch.ts`. Browser flows are tested by harness scenarios.

### Auth specifics

- Keycloak runs as a container in local compose; realm/client config is
  exported and checked into `deploy/keycloak` so environments are
  reproducible.
- Spring Security with OIDC resource-server on the API. Authorization roles
  are realm/client roles from Keycloak; no parallel user tables in the app
  beyond a lightweight profile keyed by subject ID.
- The API validates the browser-facing issuer (`http://localhost:8081/realms/mimos`)
  but fetches JWKS through the internal compose host
  (`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`) — tokens carry the
  external issuer, so both must be configured when the network position
  differs.
- Security failures (401/403) are RFC 9457 problem-details, as are API errors
  generally. Domain errors map once in `ApiExceptionHandler`:
  `IllegalArgumentException` → 400, `NoSuchElementException` → 404,
  `ReadOnlyRecipeException` → 403. It extends Spring's
  `ResponseEntityExceptionHandler`, so Spring MVC's own errors (415, 405,
  unknown-path 404, 406, malformed input) are problem-details too; anything
  unmapped is a 500 problem, logged with its stack trace, its message never
  sent. Adding method security means leaving its exceptions to the security
  chain, not this catch-all.
- `/actuator/health` and `/api/v1/public/**` are the only unauthenticated
  endpoints (compose probes and SEO pages).
- API integration tests use Testcontainers Postgres **and** Keycloak (the
  realm export from `deploy/keycloak` is on the test classpath); the Keycloak
  container module is `com.github.dasniko:testcontainers-keycloak` (the
  upstream module left the core Testcontainers BOM).

## Repo layout (target)

```
mimos/
├── NORTHSTAR.md          # product vision
├── ROADMAP.md            # ordered build plan; steps get fleshed out as picked up
├── AGENTS.md             # this file
├── pom.xml               # Maven parent/aggregator for the backend
├── mkdocs.yml            # documentation config
├── .mcp.json             # MCP servers for agent sessions (Playwright browser, via devenv)
├── .claude/skills/       # Claude Code skills — thin pointers into AGENTS.md and docs (mimos-harness)
├── docs/                 # MkDocs source: guides, plugin authoring, harness plan, decisions/ADRs
├── apps/
│   ├── api/              # Spring Boot modular monolith (the only deployable backend)
│   └── web/              # Next.js frontend
├── contracts/
│   ├── api/              # OpenAPI spec — the source of truth for the HTTP API (ADR-0003)
│   └── plugins/          # versioned plugin extension-API specs (ADR-0006)
├── core/
│   ├── core-recipes/     # recipe domain module
│   └── core-planning/    # planning domain module
├── integrations/
│   └── plugins/          # plugin runtime: registry, extension-API client, card validation (ADR-0006)
├── libraries/            # shared contracts: OpenAPI-generated clients, plugin SDK
│   ├── api-client/       # @mimos/api-client — generated TypeScript client (committed)
│   └── plugin-sdk/       # @mimos/plugin-sdk — generated TypeScript types for the plugin API (committed)
├── plugins/
│   └── country-week/     # reference plugin (build early to prove the API)
├── tools/
│   └── harness/          # agent harness: deploy, drive, observe, debug the stack (docs/harness)
└── deploy/
    ├── docker/           # compose: postgres, keycloak, api, web, plugin, (minio); compose.debug.yml overlay (harness only)
    ├── keycloak/         # realm export / config
    └── aws/              # IaC (tool TBD)
```

## Conventions and defaults

- **Testing:** JUnit 5. Integration tests use Testcontainers (Postgres,
  Keycloak) — never mocked repositories or a hand-rolled test DB. New code
  ships with tests; a change isn't done until they pass.
- **Formatting:** Spotless formats Java (palantir-java-format: 4-space
  indent, 120 columns) and every `pom.xml` (sortPom, 4-space) — it applies
  automatically on every build; CI gates with `spotless:check`. Run
  `./mvnw spotless:apply` to fix violations.
- **Null safety:** NullAway (JSpecify mode) via Error Prone; nullness
  violations in `@NullMarked` code fail the build. Every package needs a
  `@NullMarked` `package-info.java` (the build fails otherwise); annotate
  anything that may be `null` with `org.jspecify.annotations.Nullable`
  (type-use placement). `@NullUnmarked` is the escape hatch for code that
  cannot be checked yet; never `@SuppressWarnings("NullAway")` without a
  comment explaining the false positive. `.mvn/jvm.config` holds the javac
  `--add-exports`/`--add-opens` Error Prone needs — keep it in sync with the
  Error Prone install docs when bumping `error-prone.version`. Only
  nullness gates the build; other Error Prone checks stay disabled.
- **Containers:** Docker is the only supported runtime — tests (via
  Testcontainers), local dev (via compose), and CI all require it. A new
  deployable ships its Dockerfile and its compose service in the same
  change; images build in CI and run as non-root. "Works on my machine,
  outside Docker" does not count. The web and plugin images `npm ci` the
  whole npm workspace from its manifests, so a new workspace's
  `package.json` is copied into those Dockerfiles too.
- **Dev environment:** `devenv.nix` (+ `devenv.yaml`, `devenv.lock`,
  `.envrc`) provides the toolchains for Nix users, pinned to match CI (JDK
  21, Node 22, Python 3.13 + `docs/requirements.txt`). Keep it in sync when
  CI versions change. Product paths (build, tests, compose, CI) must not
  depend on it, and the Docker daemon remains a host concern. **Agent
  tooling is the exception:** anything an agent needs to drive the repo
  (the debug harness, browsers, MCP servers, `jdb`, `jq`) comes from
  `devenv.nix` / nixpkgs — never ad-hoc downloads or host installs — and
  agents invoke it as `devenv shell -- <cmd>`. The shell's banner goes to
  stderr so that command's stdout stays clean.
- **Agent harness:** `tools/harness` (`@mimos/harness`), on the PATH in the
  devenv shell as `harness`; how to use it is in "Debugging the running
  stack" below. Browser flows are Playwright scenarios in
  `tools/harness/scenarios/`, and every scenario runs in CI's compose job
  (a known failure is listed in the job's `KNOWN_FAILING` until fixed).
  `.mcp.json` gives agent sessions a Playwright MCP browser for
  exploration. `@playwright/test` is pinned to nixpkgs' `playwright-driver`
  version — bump both together. Debug-only behavior goes in
  `deploy/docker/compose.debug.yml` (`harness up --debug`), never in
  `compose.yml`, and debug ports bind to loopback only. The plan and one
  page per step are in `docs/harness/`, the command reference in
  `docs/harness/usage.md`; read them before building harness pieces, and
  keep them current as the harness changes.
- **Docs:** MkDocs. Doc changes ship with the code change they describe;
  `mkdocs build --strict` is the check once `mkdocs.yml` exists. Keep the
  nav in `mkdocs.yml` accurate; ADRs are pages under `docs/decisions/`.
- **Migrations:** Flyway, forward-only. Never edit an applied migration;
  add a new one. Migrations run automatically on startup so self-hosters
  upgrade by pulling and restarting.
- **Error handling:** RFC 9457 problem-details responses from the API.
- **Logging and request IDs:** every API request gets an `X-Request-Id`
  (a well-formed client one is kept, otherwise generated), echoed on the
  response, in the MDC as `requestId`, and on every log line the request
  produces. `RequestLoggingFilter` writes one access line per request, and
  a 4xx/5xx line names the exception that caused it. Errors raised in the
  security chain must reach `RequestLoggingFilter.recordFailure` (as
  `ProblemDetailSecurityHandlers` does), or the line cannot name them.
  Clients send their own ID per call (`apps/web/src/lib/request-id.ts`),
  except cached server-side fetches: Next's fetch cache keys on headers.
  Text logs by default; `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` for JSON.
- **Time/money-free:** no wall-clock dependence in domain logic; clocks and
  randomness are injected.
- **Commit style:** conventional commits, present tense, scoped when it
  helps (`feat(planning): ...`).
- **Naming:** product name is **Mimos**; the repo/backend service is
  lowercase `mimos-api`; database `mimos`.

## Debugging the running stack

When something fails on the running stack — a 4xx/5xx, a broken page, a
flow that does not work — investigate with the agent harness, not by
guessing. Run it as `devenv shell -- harness <command>`; every command
writes evidence to `.harness/runs/<run>/` (gitignored), and
`.harness/runs/latest/summary.md` is the first thing to read.
`docs/harness/usage.md` documents every command.

1. **Start** — `harness up --debug`: fresh images plus the debug overlay
   (JSON API logs, actuator diagnostics, JDWP on `127.0.0.1:5005`, the
   Node inspector on `127.0.0.1:9229`). Prefer it over a bare `docker
   compose up`, which does not rebuild images.
2. **Reproduce** — a browser scenario (`harness ui <scenario>`; write one in
   `tools/harness/scenarios/` if none covers the flow) or an API call
   (`harness api <METHOD> <path> [--body <file>]`). No reproduction, no fix.
3. **Read the evidence** — `summary.md` first (failing step, every API
   call with its request ID, failed requests in full), then the HAR,
   `harness logs --request-id <id>` (one request's lines across services),
   and `harness diag` (state, actuator, `routes.txt` with each route's
   media types, migrations vs. the repo, plugin manifests).
4. **Hypothesize from evidence, and narrow it** — `harness loglevel <logger>
   <level>` changes API log levels live (`--reset` restores them);
   `harness sql "<query>"` reads the database; `harness debug break
   <Class:line|Class.method> --then "<api|ui …>"` stops the API at a
   breakpoint and records stack, locals, and `--print` expressions — or an
   explicit "not hit", which is evidence too.
5. **Fix** at the layer that is wrong, with a test at that layer.
6. **Re-run the reproduction**; it must pass.
7. **Keep the scenario** — it is the regression test, and CI runs it.

## Definition of done (for any change)

1. Builds clean; existing tests pass; new behavior is tested.
2. `deploy/docker` still boots the full stack (if the change touches
   anything it runs).
3. Any new deployable has a Dockerfile and a compose service, and all
   affected images still build.
4. Migrations (if any) are forward-only and safe on existing data.
5. OpenAPI contract updated if API shape changed.
6. AGENTS.md updated if a decision, convention, or layout element changed.
7. No secrets in code or compose files; config comes from env with sane
   local defaults.

## Verification commands

- `./mvnw -B verify` — full backend build + tests. Requires Docker:
  integration tests use Testcontainers (Postgres). JDK 21+ (compiled with
  `--release 21`). Formatting applies automatically during the build;
  `.mvn/jvm.config` (javac `--add-exports`/`--add-opens` for Error Prone)
  is picked up automatically by `mvnw`.
- `./mvnw -B spotless:check` — formatting gate (Java + POMs) without
  writing.
- `mkdocs build --strict` — docs build. Requires `pip install -r
  docs/requirements.txt`.
- `npm ci && npm run generate -w @mimos/api-client` and
  `npm run generate -w @mimos/plugin-sdk` — regenerate the TypeScript API
  client and plugin SDK from `contracts/`; commit the results.
  Regenerating must produce no diff when the specs are unchanged.
- `npm run typecheck -w @mimos/web` and `npm run build -w @mimos/web` —
  frontend types and production build.
- `npm test -w @mimos/country-week` — the reference plugin's tests
  (`node --test`; Node 22.18+ for native type stripping).
- `npm test -w @mimos/web` — the web app's unit tests (`node --test`, as
  above).
- `npm run typecheck -w @mimos/harness` and `npm test -w @mimos/harness` —
  the agent harness's types and unit tests (no Docker or browsers needed).
- `devenv shell -- harness diag` — diagnostics snapshot of a running
  stack (full with `harness up --debug`); exit 1 means it found a problem
  (listed in `.harness/runs/latest/summary.md`).
- `devenv shell -- harness debug break MeController:27 --then "api GET
  /api/v1/me"` — debugger smoke against a stack started with `harness up
  --debug`: exit 0 and a hit with locals in
  `.harness/runs/latest/debug/1/hits.md`.
- `devenv shell -- harness ui login` — browser smoke against a running
  stack (Chromium from devenv); `harness ui create-recipe` drives a write
  through the form.
- `docker compose -f deploy/docker/compose.yml up -d --wait` — boots the
  full self-hosted stack (Postgres, Keycloak, API, web, and the reference
  plugin `country-week`); healthy when `--wait` returns 0. Note: `up` does
  not rebuild images — run `docker compose build` first when code changed
  (`devenv shell -- harness up` does both and records the outcome).
  Smoke: API at `http://localhost:8080/actuator/health`, Keycloak realm at
  `http://localhost:8081/realms/mimos`, web at `http://localhost:3000`
  (log in with `test` / `mimos-test`; the realm also ships `test2`, same
  password, for cross-user isolation), seeded library at
  `http://localhost:8080/api/v1/public/recipes` and
  `http://localhost:3000/recipes`, and a plugin suggestion card (after
  login) from `http://localhost:8080/api/v1/plans/{monday}/suggestions`
  or the plan page's Suggestions panel.
- CI (`.github/workflows/ci.yml`) runs all of the above on every PR; the
  compose job is the self-host parity check. Its last steps run every
  harness scenario through devenv against the booted stack and upload
  `.harness/runs/` as the `harness-runs` artifact when one fails.

## Decision log

Significant decisions beyond the table above get a short ADR in
`docs/decisions/` (context, decision, consequences). When in doubt whether
something is significant: if a future agent could plausibly choose
differently, it is.
