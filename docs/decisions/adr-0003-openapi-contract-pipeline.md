# ADR-0003: OpenAPI contract pipeline

- Status: accepted
- Date: 2026-08-24

## Context

The roadmap (step 5) calls for an API contract pipeline where the OpenAPI
spec is the source of truth: the server is verified against it and a
generated TypeScript client is shared with the frontend. Roadmap step 13
(plugin system) will expose a versioned public API later — the pipeline built
now must be able to serve that too.

Two failure modes had to be impossible:

- **Client drift**: the frontend calling an API shape the server no longer
  has (or never had), discovered at runtime.
- **Spec theater**: a checked-in spec that documents an API that does not
  exist, because nothing derives from it or verifies against it.

## Decision

- The contract lives in **`contracts/api/openapi.yaml`** (repo-level, next to
  the code that fulfils it). It is the only place API shape is decided.
  Paths are written out in full (`/api/v1/...`) — no `servers` base URL —
  because the server generator ignores it while mapping paths and the
  client sets its base URL at runtime anyway.
- **Server side**: `openapi-generator-maven-plugin` (Spring generator,
  `interfaceOnly`) generates Java interfaces + models into `apps/api` at
  `generate-sources` (package `org.openapitools.*`). Controllers *implement*
  the generated interfaces, so changing code without the spec fails
  **compilation** — the strongest possible drift gate. Generated code is
  never hand-edited; the packages are `@NullUnmarked` (JSpecify) because
  generated code cannot carry nullness annotations.
- **Client side**: `libraries/api-client` (`@mimos/api-client`) is generated
  by **`@hey-api/openapi-ts`** with the fetch client + SDK plugins. Output is
  committed (self-hosters never need to regenerate), generated via
  `npm run generate` in the workspace. The app consumes the TypeScript
  source directly (Next.js `transpilePackages`), so there is no build step
  of the library to keep in sync.
- **CI**: a dedicated job regenerates the client and fails on
  `git diff --exit-code -- libraries` — a spec change cannot ship without
  its regenerated client.

### Alternatives rejected

- *Spec generated from server annotations (springdoc-first)*: makes the code
  the source of truth; the "spec" is an output, not a contract, and cannot
  gate drift.
- *Contract tests against a running app*: verifies conformance, but only at
  runtime in CI, and does not produce the TypeScript client.
- *openapi-generator for the TS client too*: heavier runtime deps and less
  ergonomic output than hey-api's fetch client; openapi-generator stays the
  choice on the Java side where its Spring interface generation is the
  compile-time gate.

## Consequences

- Adding an endpoint means: edit the spec → build (interfaces regenerate,
  controller fails to compile until implemented) → `npm run generate` →
  commit the regenerated client.
- The generated models are plain mutable POJOs (openapi-generator style);
  domain types stay hand-written in the core modules and are mapped at the
  controller edge — generated types are transport types, not domain types.
- `@hey-api/openapi-ts` is pre-1.0 and moves fast; the version is pinned
  exactly in `package.json`, and regeneration is a deliberate committed
  step, so upgrades are explicit.
- When the plugin API (step 13) is designed, it gets its own spec under
  `contracts/` and the same pipeline, with versioning as a first-class
  concern in the spec.
