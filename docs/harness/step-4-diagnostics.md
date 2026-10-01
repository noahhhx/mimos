# Step 4 — Diagnostics

**Status:** done (2026-10-01) · [Back to the plan](index.md)

## Goal

One command snapshots everything an agent would otherwise ask for one
question at a time: what is running, what it is configured with, which
routes exist and what they accept, which migrations are applied — plus
live control over log levels and read access to the database.

## Using it

```sh
devenv shell -- harness up --debug                       # the actuator endpoints need the overlay
devenv shell -- harness diag                             # snapshot → diag/ in a new run
grep ' /api/v1/recipes ' .harness/runs/latest/diag/routes.txt
devenv shell -- harness loglevel org.springframework.web DEBUG
devenv shell -- harness api POST /api/v1/recipes --body recipe.json   # now shows handler and converter selection
devenv shell -- harness loglevel --reset
devenv shell -- harness sql "select version, success from flyway_schema_history"
cat .harness/runs/latest/summary.md
```

| Command | What it does |
| --- | --- |
| `harness diag [--since <t>]` | Writes `diag/` (below) and `logs/` (the last 15 minutes by default) into a run; `summary.md` gets one line per part, then **Problems** and **Skipped**. Exit code 1 when it found a problem. |
| `harness loglevel <logger> [<level>]` | Without a level, prints the logger's configured and effective level. With one (`OFF`…`TRACE`, any case), sets it live through `/actuator/loggers` and records `before → after` in `summary.md`. |
| `harness loglevel --reset` | Restores every logger the harness changed to the level it had before the first change. |
| `harness sql "<query>" [--write] [--csv]` | Runs `psql -c` inside the postgres container. Output goes to `sql/<n>.txt` (or `.csv`) and, with the query, to `summary.md`. Read-only unless `--write`. Exit code 1 when psql fails. |
| `harness psql` | An interactive `psql` on the terminal, for humans: writes allowed, nothing recorded. |
| `harness up --debug --sql-log` | Postgres also logs every statement and its duration (`log_min_duration_statement=0`) in `logs/postgres.log`. A later `up` without the flag turns it off again. |

### What `diag/` holds

| File | From | Needs `--debug` |
| --- | --- | --- |
| `compose.json` | git SHA and dirty flag, each service's state, health, and image freshness (`harness status`'s check), container details | no |
| `actuator/health.json` | `/actuator/health` (anonymous) | no |
| `actuator/{info,mappings,env,configprops,loggers,flyway}.json`, `actuator/threaddump.txt` | the actuator, with a token for `test` | yes |
| `routes.txt` | `mappings` as one line per route: method, path, `consumes=`/`produces=` (left out when any type goes), handler | yes |
| `migrations.csv` | `flyway_schema_history` via `psql` | no |
| `plugins/<service>-manifest.json` | each plugin sidecar's `GET /manifest`, fetched with `wget` inside its container (plugins are not published to the host) | no |
| `keycloak/realm.json`, `keycloak/openid-configuration.json` | the realm's public metadata | no |

A route line reads:

```
POST    /api/v1/recipes   consumes=application/json  produces=application/json,application/problem+json  → RecipesController#createRecipe(RecipeInput)
```

**Problems:** unhealthy or missing services, stale images or containers
on an older image, API health other than `UP` (with the failing
components), failed migrations, and migrations in
`apps/api/src/main/resources/db/migration` that are not applied (the
running image predates them, or startup stopped early).

**Skipped:** every part that could not be captured, with the reason. Each
part runs on its own, so the API being down still leaves compose state,
migrations, Keycloak, and logs. Without the overlay, the actuator
endpoints other than health are listed here with a pointer to `harness
up --debug`.

## Design notes

Decisions made while building, beyond the plan:

- **The overlay shows `env`/`configprops` values; the harness redacts
  them.** The plan said to keep Spring Boot's default masking, but since
  Boot 3 that default masks *every* value, which leaves nothing to
  diagnose. The overlay sets `show-values: always` for both, and `diag`
  replaces the string value of every key that names a secret (`password`,
  `secret`, `token`, `credential`, `api key`, `private key`, in any
  spelling) before writing. A `{value, origin}` entry keeps its origin;
  numbers (`maxTokenCount`) and nested groups (`opaquetoken`) are not
  replaced whole, only the secrets inside them. The values do cross the
  wire to whoever holds a token, which is acceptable for the local-only
  overlay (the "any valid token" decision). If actuator exposure ever
  reaches a non-debug deployment, it needs a `SanitizingFunction` in the
  API and the admin role from that decision.
- **The highlights go in the run's `summary.md`**, like every other
  command's section, instead of a separate `diag/summary.md`.
- **`info` gets the `java`, `os`, and `process` contributors** in the
  overlay. Build info (`build-info` in the Spring Boot Maven plugin) would
  be a product change. The image's build time and the git state in
  `compose.json` cover it for now.
- **Exposure is read from `/actuator`'s links**, not inferred from the
  compose files, so `diag` reports what the running API actually serves.
- **Pending migrations are computed against the repo**, not taken from
  the actuator's `flyway` endpoint. The API applies everything it knows
  at startup, so `flyway` cannot show a migration the image predates. The
  repo comparison works without the overlay too.
- **The read-only guard is `PGOPTIONS=-c default_transaction_read_only=on`**
  for the session, not a `BEGIN READ ONLY` wrapper, which a `COMMIT` in
  the query would end. It guards against accidents, not malice: a query
  can still `SET default_transaction_read_only = off`.
- **`loglevel` keeps the originals in `.harness/loglevels.json`**
  (gitignored with the rest of `.harness/`) rather than in a run folder,
  so `--reset` works from any run. Only the first change to a logger is
  remembered, so a second change still resets to the true original. A
  restarted API has reverted everything anyway, and resetting to the
  originals is then a no-op.
- **`--sql-log` is an environment variable in the overlay**
  (`POSTGRES_LOG_MIN_DURATION_STATEMENT`, default `-1`), set by `harness
  up` only with the flag. An exported value is ignored, so a plain `up
  --debug` always turns statement logging off again.
- **`psql` runs inside the postgres container**, over its local socket
  (trusted for its own user). The host needs no client and no password.

## Changes

- `deploy/docker/compose.debug.yml` — actuator exposure, `env`/
  `configprops` values, `info` contributors; Postgres statement logging
  behind `POSTGRES_LOG_MIN_DURATION_STATEMENT`.
- `tools/harness` — `src/actuator.ts` (client, exposure, route table,
  health, loggers), `src/postgres.ts` (psql arguments, CSV, migration
  report); `diag`, `loglevel`, `sql`, `psql` commands; `up --sql-log`;
  `redactConfig` in `src/redact.ts`; `status`'s freshness check reusable
  as `serviceReports`; `execInteractive`/`composeInteractive`; tests.
- Docs: this page, the plan, step 7's evidence log, AGENTS.md.

## Tests

`npm test -w @mimos/harness`:

- routes from a trimmed real `mappings` payload: one per pattern; media
  types (negated ones as `!type`), params and header conditions; short
  handler names; resource handlers; sorting; the text layout.
- exposure from the discovery links (templates and `self` excluded);
  unhealthy health components, nested ones by path.
- psql arguments: read-only `PGOPTIONS` by default and none with
  `--write`, `ON_ERROR_STOP`, `--csv`, compose's database user.
- CSV parsing (quoted commas, quotes, line breaks, CRLF); the migration
  report (failed rows, repo scripts not applied); the repo's scripts in
  version order.
- `redactConfig`: env entries keep their origin, secret spellings
  (`SPRING_DATASOURCE_PASSWORD`, `client-secret`, `MIMOS_API_KEY`),
  numbers and nested groups, bearer tokens in other values.
- loglevel remembers only the first original; argument parsing for
  `diag`, `loglevel` (logger names are checked before they reach a URL),
  `sql`, and `up --sql-log` (needs `--debug`; an exported value does not
  leak into a plain `--debug`).

## Verification (2026-10-01)

- `harness up --debug`, then `harness diag` → every part captured;
  `routes.txt` has 42 routes, among them `POST /api/v1/recipes
  consumes=application/json produces=application/json,application/problem+json`.
  `env.json`'s `SPRING_DATASOURCE_PASSWORD` and `configprops.json`'s
  datasource `password` read `[REDACTED]`; non-secret values are intact.
  It reported one real problem: the `country-week` container ran an older
  image than the latest build.
- `harness loglevel org.springframework.web DEBUG` → `inherited
  (effective INFO) → DEBUG (effective DEBUG)`. The next `harness api GET
  /api/v1/me` produced `DispatcherServlet`, `RequestMappingHandlerMapping`
  (`Mapped to MeController#getMe()`), and `HttpEntityMethodProcessor`
  lines under its request ID, with no restart. After `harness loglevel
  --reset`, the same call produced only the access line, and a second
  reset reported nothing to reset.
- `harness sql "select version, success from flyway_schema_history"` →
  versions 1–6, all `t`, in `sql/1.txt`. A `delete` without `--write`
  failed with `cannot execute DELETE in a read-only transaction` (exit
  1). With `--write`, a temp table's create, insert, and select ran.
- `harness up --debug --sql-log` → Postgres ran with
  `log_min_duration_statement=0`, and a `harness sql` query appeared in
  its log with a duration. `harness up --sql-log` without `--debug` is a
  usage error.
- On the plain stack (`harness up`), `harness diag` captured health,
  compose state, migrations, plugins, Keycloak, and logs, and listed the
  other actuator endpoints as skipped. `harness loglevel ROOT` explained
  that the overlay is needed.
