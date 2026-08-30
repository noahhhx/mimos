# ADR-0005: Core product domain — recipes, planning, lists, logging, and the public API surface

- Status: Accepted
- Date: 2026-08-30
- Supersedes: none

## Context

Phase 2 of the roadmap (steps 7–11) builds the actual product: recipes,
meal planning, shopping lists, calorie/macro logging, and a seeded recipe
library. Several decisions in that work affect the shape of the system
beyond any single feature, so they are recorded here: where persistence
lives, how the library is seeded and exposed, and how the domain modules
share data.

## Decision

### Modules own their tables' data access

The `core/*` modules own the JDBC repositories for their tables
(`core-recipes` → `recipe*`, `core-planning` → `meal_plan*`,
`shopping_list*`, `meal_log`). HTTP controllers stay in `apps/api` as thin
contract adapters. Flyway migrations stay in `apps/api` (the single
deployable owns the schema). This extends ADR-0001's "modular monolith"
seam: a future extraction takes the module's tables with it.

### Cross-module data access goes through public interfaces only

`core-planning` depends on `core-recipes` (the one allowed dependency) and
reads recipe data — titles, ingredients, per-serving nutrition — only via
`RecipeService`'s public interface. The schema uses FKs across module
tables for integrity (`meal_plan_entry.recipe_id`, `meal_log.recipe_id`),
which is a data constraint, not an access path: no code outside a module
queries that module's tables.

### Library recipes are owned by the instance, not a user

A `recipe` row with a null `owner_profile_id` is a curated library recipe:
read-only for everyone, publicly readable via its slug, and exposed on the
unauthenticated surface `/api/v1/public/**` (for SEO pages). A non-null
owner marks a personal recipe, visible only to its owner (indistinguishable
from nonexistent to others). Library mutations are a 403; other users'
recipes are a 404.

### The seed file is the library's content pipeline, not a migration

Curated content lives in `core-recipes/src/main/resources/library/
library-seed.json` and is loaded at startup by an idempotent seeder
(insert by slug, refresh existing library rows, skip slugs claimed by
personal recipes). Content fixes therefore ship with a restart — no
forward-only migration churn. Self-hosters can disable it
(`mimos.library.seed-enabled=false`) to start with an empty library.

### Plans are keyed by Monday; shopping lists aggregate; logs are historical

- Meal plans are one row per profile per week (`start_date` = Monday),
  entries = (date, meal slot, recipe, servings). The week range and
  Monday-ness are enforced by the domain service, not SQL.
- Shopping lists are generated artifacts: ingredient quantities aggregated
  by (normalized name, unit) across the week's recipes, scaled by planned
  servings, grouped into aisle categories by a best-effort keyword map in
  code (`Aisles`) with "Other" as the honest fallback. Regeneration
  replaces the list but preserves checked-off lines (same name + unit).
- Meal logs copy nutrition at logging time (recipe per-serving values ×
  servings, or manual totals for ad-hoc entries). Later recipe edits — or
  deletion, which clears the link via `ON DELETE SET NULL` — never rewrite
  history.

## Consequences

- The public SEO pages render server-side against `/api/v1/public/**`;
  the web container reaches the API over the internal network for those
  fetches (`API_SERVER_URL`, runtime env — not a build arg).
- `NoSuchElementException` (404), `IllegalArgumentException` (400), and
  `ReadOnlyRecipeException` (403) are the domain-to-HTTP error vocabulary,
  mapped once in `ApiExceptionHandler`.
- Deleting a recipe removes its planned meals (`ON DELETE CASCADE`) but
  keeps logged meals — planning is current state, logging is history.
- The aisle mapping is knowingly imperfect; it is a display concern in
  code so every instance behaves identically without data setup. A future
  refinement (per-instance overrides) would be a plugin-shaped feature.
