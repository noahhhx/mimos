# ADR-0011: Account export and import

- Status: Accepted
- Date: 2026-10-02
- Supersedes: none

## Context

Mimos is about to be self-hosted for a family to test and use. Before
anyone trusts it with months of recipes and logs, a user needs to be able to
take their data out, keep it somewhere safe, and put it back, whether into
a fresh instance or a fresh account on the same one.

Three facts shape the design:

- **Identity lives in Keycloak.** A fresh instance means fresh Keycloak
  users with new subject IDs, so restoring by subject or by profile ID is
  impossible. The unit of portability is "the signed-in user's data",
  imported into whichever account is signed in.
- **The schema will keep moving.** Flyway migrations land with most
  features, and an export taken today must still import after any number
  of them. A table dump would tie every backup to the schema version that
  produced it.
- **Library recipes are instance content.** They are seeded per instance
  with random IDs (ADR-0005), so a plan entry that points at a library
  recipe by ID means nothing on another instance.

## Decision

### One JSON document per account, domain-shaped

`GET /api/v1/account/export` returns the caller's data as one JSON
document. `POST /api/v1/account/import` takes such a document and writes it
into the caller's account. The document holds:

| Section         | Contents                                                              |
| --------------- | --------------------------------------------------------------------- |
| `recipes`       | Personal recipes in full: content, nutrition, ingredients, steps, tags, timestamps |
| `mealPlans`     | Weeks (by Monday) with their planned meals                            |
| `shoppingLists` | Generated lists with their items and check-off state                  |
| `mealLogs`      | Logged meals with the nutrition copied at logging time                 |

The profile is not exported: its only content, the display name, comes
from Keycloak. Library recipes are not exported either, since they belong
to the instance.

The format describes the **domain**, not the tables. The exporter reads
through the core modules' public services and the importer writes through
them, so neither knows any table or column. A migration that does not
change what the domain means (renaming a column, splitting a table, adding
an index) therefore needs no change to the format or to old exports.

### References: document IDs for personal recipes, slugs for the library

Each exported recipe keeps its source `id`, but only as a key that other
sections of the same document refer to. A plan entry or meal log points at
its recipe with exactly one of:

- `recipeId`, a recipe in the document's `recipes`, or
- `librarySlug`, a library recipe, by the slug that is its stable public
  identity.

Import mints new IDs for everything, so importing never collides with
existing rows, even on the source instance.

When a `librarySlug` is not in the target library (the library changed, or
seeding is off), the import goes ahead and says so in its report: planned
meals of that recipe are skipped, and logged meals keep their copied
nutrition but lose the link. Logs are history; plans are current state
(ADR-0005), so the cheaper loss is the plan.

### Versioned, with upgrades on import

Every document starts with `"format": "mimos.export"` and an integer
`"version"`. The API exports the current version and imports any version
from 1 up to it:

1. The importer reads the body as a raw JSON tree, not as the current
   model, so an old document is never silently misread.
2. It applies **upgrade steps** in order: version 1 to 2, 2 to 3, and so
   on. Each step is a small JSON-to-JSON transform kept forever in
   `ExportUpgrader`.
3. Only then is the document bound to the current model and validated.

Any change to the document's shape bumps the version, even an additive
one (a step can be a no-op, or it can fill a new field with its default).
Then a newer instance always knows what it is reading, and an older one
can refuse cleanly. A document whose version is newer than the instance
supports is a 400 that says to upgrade Mimos. There is no forward
compatibility.

### Frozen fixtures

For every version ever released, `apps/api/src/test/resources/export/`
holds a frozen sample document, `v<N>.json`. An integration test imports
each one into a fresh account on the current schema and checks what
arrives, and another test fails when a version has no fixture. Fixtures
are never edited. This turns "old exports still import" from a promise
into a build gate: a migration, a domain rule, or a format change that
would break an old backup fails CI.

### Import is a restore into an empty account

- **Empty account only.** Import is refused with a 409 when the account
  already has personal recipes, planned meals, logged meals, or shopping
  list items. Empty rows a page view creates (an empty week, an empty
  generated list) do not count. Importing the same file twice cannot
  duplicate anything, and a bad file can never overwrite real data. To
  merge, import into a fresh account.
- **Atomic.** The whole import is one transaction, with the caller's
  profile row locked so two concurrent imports cannot both pass the empty
  check. Any invalid item rolls everything back, and the 400 names the item
  (`recipes[3]: title is required`).
- **Current rules apply.** Every item goes through the same domain
  validation as the API. Importing a document never bypasses a rule; if a
  rule tightens, the upgrade step that comes with it must repair old data,
  and the fixtures prove it does.
- **History is kept.** Recipe timestamps, `loggedAt`, and `generatedAt`
  are restored as exported. Logs keep their exported nutrition and are
  not recomputed from today's recipes.

### Where it lives

The orchestration (`apps/api` package `account`) is an adapter over the
core modules' public interfaces, like the controllers. Each module gains
what it needs to list everything an owner has and to restore an item with
its history: `RecipeService.restore`, `MealPlanService.plansWithEntries`,
`ShoppingListService.findAll` and `restore`, `MealLogService.findAll` and
`restore`, plus a "has data" query per module. Planned meals are imported
through the existing `MealPlanService.addEntry`. The format
is part of the OpenAPI contract (`AccountExport` and its parts), so the
web app and any third-party tool get typed access to it. The import body
is declared as a free-form object on purpose, because older versions do
not match the current schema.

## Consequences

- Users can back up and move their own data from the web app's "Your
  data" page (linked from the Kitchen's profile section). On a fresh instance, a user signs in (which creates their
  account) and imports their file.
- A format change is more work: a new version, an upgrade step, a new
  frozen fixture, and the contract update. That cost is the point.
- This is not an instance backup. Keycloak users and the database as a
  whole still need `pg_dump`/volume backups for disaster recovery, which
  is part of roadmap step 16. Account export is for one person's data,
  moving between accounts and instances.
- Exports are plain, unencrypted JSON holding the user's own data. Where
  they are kept is the user's choice.
- Merge import, selective import, and scheduled exports are not built. The
  format supports them later without a version change.
