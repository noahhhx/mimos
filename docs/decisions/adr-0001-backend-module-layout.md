# ADR-0001: Backend module layout

- Status: accepted
- Date: 2026-08-24

## Context

Mimos is a modular monolith (AGENTS.md, prime directive #4). The backend
needs Maven module boundaries from the first commit so the seams
(`core-recipes`, `core-planning`, later `integrations/*` and the plugin
extension API) are enforced by the build rather than by convention. The
decisions that constrain this one: Java 21 LTS, Spring Boot 4.x, Maven
multi-module, monorepo, `apps/api` is the single deployable.

We also needed a base Java package. The project does not own a domain; the
repo lives at `github.com/noahhhx/mimos`.

## Decision

- The root `pom.xml` aggregates and parents all backend modules; its own
  parent is `spring-boot-starter-parent` (currently 4.1.x).
- Domain modules live under `core/`:
  - `core/core-recipes` — recipes, ingredients, nutrition. Depends on no
    other Mimos module.
  - `core/core-planning` — meal plans, shopping lists, logging. Depends only
    on `core-recipes`.
- `apps/api` is the only deployable Spring Boot application and depends on
  the core modules.
- Base package: `io.github.noahhhx.mimos`, with one child package per Maven
  module (`...mimos.recipes`, `...mimos.planning`, `...mimos.api`). This
  lets the application component-scan the whole tree while module ownership
  stays visible in every import.
- Versions: `0.1.0-SNAPSHOT` until first release.

## Consequences

- Adding a module means a new directory plus an entry in the root
  `<modules>` — cheap, and `mvn verify` catches dependency-direction
  mistakes at build time (once ArchUnit tests are scaffolded, they become
  assertions rather than reviews).
- The `core/` prefix keeps the root readable next to `apps/`, `libraries/`,
  `plugins/`, `deploy/`; the AGENTS.md layout is updated accordingly.
- The `io.github.noahhhx` groupId is fine for an open-source project. If a
  domain is acquired later, a new package prefix would be a large,
  mechanical rename — accepted as unlikely and low-urgency.
- `integrations/` modules, when they appear, follow the same pattern
  (`core/` for pure domain, integration modules never inside `core/`).
