# ADR-0019: Households

- Status: Proposed
- Date: 2026-10-06
- Changes: [ADR-0005](adr-0005-core-product-domain.md) (who owns
  personal data), [ADR-0011](adr-0011-account-export-import.md) (export
  format version 4), [ADR-0013](adr-0013-per-user-plugin-opt-in.md) and
  [ADR-0017](adr-0017-plugin-week-panels.md) (plugin opt-ins and
  pseudonyms per household)

## Context

A family cooks together but eats differently. Each person logs their own
calories and plans their own breakfasts and lunches; dinners, the
shopping list, the Country of the Week, and the recipe collection are
shared. Today every row belongs to one profile: recipes, personal
ingredients, plans, shopping lists, logs, plugin opt-ins, and plugin
pseudonyms. A family can only share by sharing one login, which merges
their calorie logs.

A plan entry has a recipe and a servings count but no notion of who eats
it, and logging a planned meal copies the entry's servings. On a dinner
cooked for four, each person would log all four servings.

AGENTS.md allows no user tables "beyond a lightweight profile keyed by
subject ID". This ADR adds a household table and membership, so it
changes that rule.

## Decision

### Everyone belongs to one household

A household owns everything that is shared. Every profile belongs to
exactly one; a user on their own is a household of one, created with
their profile. There is no "not in a household" state and no separate
code path for it.

Membership is domain data in Mimos's database, like a plan, not a
Keycloak group or organization. Keycloak still owns identity, roles,
and tokens; a household grants no permission beyond reading and editing
the household's own data, so it is not an authorization concept and
needs no realm change. Self-hosters get it with no Keycloak
configuration.

### What is shared and what is not

| Data                         | Owner     |
| ---------------------------- | --------- |
| Recipes                      | Household |
| Personal ingredients         | Household |
| Meal plans and their entries | Household |
| Shopping lists               | Household |
| Plugin opt-ins               | Household |
| Plugin pseudonyms (`subject`)| Household |
| Meal logs                    | Profile   |

"Personal" recipes and ingredients become the household's: every member
can read, edit, and delete them, and they record who created them. The
library and the shared ingredient catalog are unchanged.

The core modules already take an opaque owner id. The API resolves the
caller's household and passes its id where it passes the profile id
today; logging keeps the profile id. `core-recipes` and `core-planning`
do not learn about households.

### Plan entries have diners

An entry names its diners: one or more members of the household
(`meal_plan_entry_diner`). This is how a lunch belongs to one person and
a dinner to everyone. When a request leaves diners out, a new dinner
gets every member and a new breakfast, lunch, or snack gets only the
caller. An entry with no diners is refused (a 400), and so is a diner
who is not a member.

`servings` stays the amount cooked, so the shopping list is unchanged.
Logging a planned meal records the caller's share, servings divided by
the number of diners, which the log dialog lets them change before
saving. Portions per diner are not stored.

The plan page shows each entry's diners and toggles between the caller's
meals (entries they eat) and everyone's.

### Joining and leaving

Every member is equal. Any member creates an invite: a single-use link
that expires after seven days. Anyone may leave; nobody removes anyone
else.

Joining from a household of one brings its recipes and personal
ingredients into the new household. Its plans, shopping lists, plugin
opt-ins, and plugin pseudonyms are deleted, and so is the old
household. The join page says so before the user confirms. Joining from
a shared household means leaving it first, which brings nothing.

Leaving takes nothing but the leaver's own logs. The household keeps its
recipes, ingredients, plans, and lists. The leaver is removed as a diner
everywhere, entries left with no diners are deleted, and the leaver
starts a new household of one. Links from their logs to the old
household's recipes are cleared, since they can no longer read those
recipes; the logs keep their nutrition, which was copied when logged.
Users who want their recipes export before they leave.

Join and leave are `x-mcp: false`: membership is a consent the user
gives in person, not one an agent gives on their behalf. Reading the
household and creating an invite stay tools.

### Plugins see a household

Plugin opt-ins and pseudonyms move to the household. Any member turns a
plugin on or off for everyone, and the Plugins page says so. A plugin
sees one `subject` per household, so the Country of the Week, its wheel,
and its suggestions are shared without a contract change. Plugins still
see planned slots, not diners: who eats what is not plugin context.

### Export format version 4

The export stays one person's: the household's recipes and personal
ingredients, the plan entries the exporter eats (with the exporter as
the only diner), the household's shopping lists, and the exporter's
logs. Import still requires an empty household of one and restores every
entry with the importer as its diner. The upgrade from version 3 changes
no data: every version-3 entry was the exporter's. Frozen fixture:
`export/v4.json`.

### Migration

One forward-only migration creates a household for each existing
profile, moves every owned row to it, and makes each entry's owner its
only diner. Existing pseudonyms keep their UUIDs, so a plugin's stored
data about a user stays reachable as data about their household of one.
Nothing changes for an existing user until they join someone.

## Consequences

- AGENTS.md's auth rule becomes "no user tables beyond a lightweight
  profile keyed by subject ID and its household membership". The
  module boundaries gain the household package in `apps/api`, the owner
  of membership, invites, and the join and leave rules.
- The MCP server instructions and the user guide stop saying personal
  recipes are visible only to their owner.
- Two members can edit the same week at once. Writes are per entry and
  per item, so the last write wins and nothing corrupts; the web app
  shows changes on the next load, with no live updates in v1.
- A household could later get a name, roles, or removal of members.
  None is built until a household needs it.

## Delivery

Each step leaves the product working:

1. The migration and owner-id plumbing: every user gets a household of
   one, with no visible change.
2. Invites, joining, leaving, and the Household page.
3. Diners on entries, logging a share, and the plan page's toggle.
4. Plugin page copy, export version 4, the user guide, and a harness
   scenario in which `test` and `test2` share a dinner.
