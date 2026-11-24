# AGENTS.md

Read `NORTHSTAR.md` before product decisions. Decisions live as ADRs in
`docs/decisions/` (index there); this file names the ones not to relitigate
and the rules nothing in the repo enforces yet. Prefer encoding a rule as a
build check, test, or `tools/harness/src/repo-rules.ts` entry over adding
prose here.

## Prime directives

1. **Self-hosted parity.** `deploy/docker` brings up the whole product with
   no code changes; CI's compose job proves it on every PR.
2. **No cloud provider.** Only containers, Postgres, an S3-compatible API,
   and standard OIDC. Nothing may call a provider's own API (ADR-0031).
3. **Plugins are a product surface.** Judge designs by: could a third party
   build the Country of the Week plugin without touching core? (ADR-0006)
4. **Modular monolith.** Split a service out only for a concrete need, not
   because it "feels more scalable".
5. **Small changes.** One feature or decision per change, with tests and
   docs. Ask the owner before adding a framework, language, or top-level
   dependency.

## Decided, do not relitigate without the owner

| Area | Decision |
| --- | --- |
| Backend | Java 21, Spring Boot 4, Maven multi-module, `core/` domain modules + `apps/api` (ADR-0001) |
| Frontend | Next.js App Router, TypeScript, browser OIDC via `oidc-client-ts`, no server sessions (ADR-0004, ADR-0024) |
| Mobile | Native Android, Kotlin Multiplatform + Compose, no Play Services, no Material (ADR-0029, ADR-0030) |
| Auth | Keycloak over OIDC only; never local auth (ADR-0004, ADR-0020) |
| Data | Postgres, Flyway forward-only migrations owned by `apps/api` |
| API | Contract-first: `contracts/api/openapi.yaml` is the source of truth (ADR-0003); every `/api/v1` op is an MCP tool (ADR-0014) |
| Plugins | HTTP sidecars, pull-only, opt-in per household, library recipes only (ADR-0006, ADR-0013, ADR-0017) |
| Households | All shared data is keyed by household; meal logs stay per person (ADR-0019) |
| Deploy | Published images on ghcr carry no deployment config; servers run `deploy/selfhost` (ADR-0012, ADR-0031) |
| Visual design | Evening Kitchen: tokens in `apps/web/src/app/globals.css`, straight edges, no motion except the plugin wheel (ADR-0008) |
| Docs | MkDocs Material; the site is the user guide only |

## Rules the repo does not enforce

**Module seams.** `ArchitectureTest` in `apps/api` checks the Java side of
the seams and the injected clocks and ids; it cannot read SQL. A module
queries only its own tables. Core takes an opaque `ownerId` that `apps/api`
sets to a household id, and the schema `apps/api` owns may name that
column `household_id`. Plugins never see personal recipes, profiles, or
logs. External API types never leave their `integrations/*` module.

**API contract.** From the first app release `/api/v1` is additive only.
CI's contract check fails a name newly added to the `required` list of a
schema the last release had, and a parameter or request body that became
required; keep such a field optional and say in its description that older
servers or clients leave it out. It misses a `$ref` swapped to another
schema, a new parameter added by `$ref`, and required names inside a schema
or property the release lacked; reviewers watch for those. An
operation's `summary` and `description` are all an agent reads, so state
the rules a caller would otherwise guess. Use `x-mcp: false` only when an
agent must not call it.

**Data.** Never edit an applied migration. Library content ships through
the seed files and a restart, never Flyway (ADR-0005). Any change to what
the account export holds bumps its version with an `ExportUpgrader` step
and a frozen `export/v<N>.json` fixture (ADR-0011).

**Domain code.** Domain errors map to problem details once, in
`ApiExceptionHandler`. An error raised in the security chain must reach
`RequestLoggingFilter.recordFailure`, or its log line cannot name it.

**Web config.** Public URLs and the sign-up policy are runtime env, read in
`src/lib/config.ts` and `/runtime-config.js`. Prerendered pages carry the
build's defaults, so a component that renders by that config reads it with
`useSyncExternalStore` and a neutral server snapshot
(`CreateAccountButton`), or hydration disagrees.

**Change together.** The design tokens and fonts are mirrored in the
Keycloak theme (`deploy/keycloak`), the docs CSS (`docs/assets/`), and the
app's type and spacing scales (`apps/mobile/shared/.../ui/`). The app's
colours are generated from `globals.css` and fail the build on drift; the
rest do not.

**Agent tooling.** Everything an agent runs comes from `devenv.nix`, invoked
as `devenv shell -- <cmd>`; never ad-hoc downloads or host installs. Product
paths (build, tests, compose, CI) must not depend on devenv.
`@playwright/test` is pinned to nixpkgs' `playwright-driver`; bump both
together. Debug-only behavior goes in `deploy/docker/compose.debug.yml` and
fakes of third-party services in `compose.fakes.yml`, never in
`compose.yml` or `deploy/selfhost`.

**Debugging.** Reproduce with the harness before fixing, and keep the
reproduction as a scenario. The loop and every command are in
`docs/harness/usage.md`.

**Docs.** A change to what users see updates its page in `docs/guide/` and
its screenshot in `docs/assets/screenshots/` (Playwright MCP browser,
1280 px, light theme). Contributor material stays in the repo-only
directories listed in `exclude_docs`.

**Commits.** Conventional commits, present tense, scoped when it helps.

## Verification

- `./mvnw -B verify` runs the backend build, formatting, nullness, and
  Testcontainers tests (needs Docker).
- `npm test -w <workspace>` and `npm run typecheck -w <workspace>` for
  `@mimos/web`, `@mimos/harness`, and `@mimos/country-week`.
- `npm run generate -w @mimos/api-client` and `-w @mimos/plugin-sdk` after a
  spec change; commit the result.
- `npm run lint` checks TypeScript lint and formatting (Biome); `npm run
  format` fixes formatting.
- `node tools/harness/src/repo-rules.ts` checks copy, colours, and config
  rules across tracked files.
- `devenv shell -- bash -c 'cd apps/mobile && ./gradlew check assembleDebug'`
  for the Android app.
- `mkdocs build --strict` for the docs.
- `devenv shell -- harness up` boots the stack; sign in as `test` /
  `mimos-test` (`test2` for a second user).

A change is done when it builds, its tests pass, the stack still boots,
the OpenAPI contract and docs match it, and no secret is committed.
