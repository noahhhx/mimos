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
  knowledge of integrations, plugins, or HTTP clients.
- `core-planning` — meal plans, shopping lists, logging. Depends on
  recipes, nothing else.
- `integrations/intervals-icu` — future. External API types never cross
  this boundary; translate to domain types at the edge. Feature-gated and
  optional at runtime (self-hosters must not need it).
- `plugins` — see below.
- Modules communicate through their public interfaces only; no reaching into
  another module's internals or tables. Enforce via Maven's explicit
  inter-module dependencies plus ArchUnit tests once scaffolded.

### Plugin system

The reference use case: **"Country of the Week" — suggests a country, and the
week's meal plan leans into that cuisine.** Design constraints:

- Plugins extend Mimos without modifying or rebuilding core.
- A plugin must be installable/enablable per-instance by the instance owner —
  this is a self-host parity feature.
- Start with an HTTP extension API (Mimos calls out to plugin services, and
  plugins can call a versioned Mimos API). Plugin services are just
  containers — trivially self-hostable and naturally microservice-shaped if
  we ever split.
- The plugin API is versioned from day one and treated as a public contract:
  additive changes only, deprecation with notice.
- Plugins never get raw database access; they get what the extension API
  exposes. Design the API surface so the country-picker needs: read recipe
  metadata/tags, propose a plan suggestion, surface a small UI affordance.
- The exact mechanism (extension points, registration, manifest format) is an
  open design task — propose a design before implementing.

### Frontend specifics

- `apps/web` is Next.js (App Router) with TypeScript. Public recipe pages
  render via SSG/SSR for SEO; the logged-in product is client-heavy.
- The frontend talks to `mimos-api` through the OpenAPI contract; the
  generated client lives in `libraries/`, never hand-written per feature.
- Runs as a standalone Node container in compose. No Vercel-specific
  features that break self-hosting; anything platform-tied is rejected on
  the same grounds as prime directive #2.
- Logic a future mobile app would share (API clients, types, validation
  schemas) lives in `libraries/`, not in app code.

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
  generally.
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
├── docs/                 # MkDocs source: guides, plugin authoring, decisions/ADRs
├── apps/
│   ├── api/              # Spring Boot modular monolith (the only deployable backend)
│   └── web/              # Next.js frontend
├── contracts/
│   └── api/              # OpenAPI spec — the source of truth for the HTTP API (ADR-0003)
├── core/
│   ├── core-recipes/     # recipe domain module
│   └── core-planning/    # planning domain module
├── libraries/            # shared contracts: OpenAPI-generated clients, plugin SDK
│   └── api-client/       # @mimos/api-client — generated TypeScript client (committed)
├── plugins/
│   └── country-week/     # reference plugin (build early to prove the API)
└── deploy/
    ├── docker/           # compose: postgres, keycloak, api, (web, minio)
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
  outside Docker" does not count.
- **Docs:** MkDocs. Doc changes ship with the code change they describe;
  `mkdocs build --strict` is the check once `mkdocs.yml` exists. Keep the
  nav in `mkdocs.yml` accurate; ADRs are pages under `docs/decisions/`.
- **Migrations:** Flyway, forward-only. Never edit an applied migration;
  add a new one. Migrations run automatically on startup so self-hosters
  upgrade by pulling and restarting.
- **Error handling:** RFC 9457 problem-details responses from the API.
- **Time/money-free:** no wall-clock dependence in domain logic; clocks and
  randomness are injected.
- **Commit style:** conventional commits, present tense, scoped when it
  helps (`feat(planning): ...`).
- **Naming:** product name is **Mimos**; the repo/backend service is
  lowercase `mimos-api`; database `mimos`.

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
- `npm ci && npm run generate -w @mimos/api-client` — regenerate the
  TypeScript API client from `contracts/api/openapi.yaml`; commit the result.
  Regenerating must produce no diff when the spec is unchanged.
- `docker compose -f deploy/docker/compose.yml up -d --wait` — boots the
  full self-hosted stack (Postgres, Keycloak, API); healthy when `--wait`
  returns 0. Smoke: API at `http://localhost:8080/actuator/health`, Keycloak
  realm at `http://localhost:8081/realms/mimos`.
- CI (`.github/workflows/ci.yml`) runs all of the above on every PR; the
  compose job is the self-host parity check.

## Decision log

Significant decisions beyond the table above get a short ADR in
`docs/decisions/` (context, decision, consequences). When in doubt whether
something is significant: if a future agent could plausibly choose
differently, it is.
