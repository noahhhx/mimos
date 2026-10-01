# Step 4 — Diagnostics

**Status:** planned · [Back to the plan](index.md)

## Goal

One command snapshots everything an agent would otherwise ask for one
question at a time: what is running, what it is configured with, which
routes exist and what they accept, which migrations are applied — plus
live control over log levels and read access to the database.

## Prerequisites

- Step 3 (debug overlay).
- Decided ([decisions](index.md#decisions)): debug-overlay actuator
  endpoints accept any valid token in v1.

## Design

### Actuator in the debug overlay

Only `health` is exposed today, and every other actuator endpoint already
requires a bearer token (`SecurityConfig`). The overlay adds:

```yaml
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: health,info,mappings,env,configprops,loggers,metrics,threaddump,flyway
```

- `env`/`configprops` keep Spring Boot's default value masking — no
  secrets in the snapshot.
- `heapdump` is deliberately **not** exposed (contains secrets, large);
  add it per-investigation if ever needed.
- The harness calls these with a token from `harness token`.

### `harness diag`

Writes `diag/` in the run folder:

- `compose ps` with health, image IDs and build times, git SHA/dirty flag;
- actuator `health`, `info`, `mappings` (shows each handler's `consumes`/
  `produces` — directly relevant to media-type bugs), `env`, `configprops`,
  `loggers`, `flyway`, `threaddump`;
- country-week `GET /manifest` (via `compose exec`, it is not exposed to
  the host);
- Keycloak realm metadata (`/realms/mimos`, OIDC discovery);
- recent logs of all services;
- a `diag/summary.md` with the highlights (unhealthy services, pending or
  failed migrations, stale images).

Without the debug overlay, `diag` degrades gracefully to what is reachable
(health, compose state, logs, database) and says what it skipped.

### Live log levels

`harness loglevel <logger> <level>` posts to `/actuator/loggers/<logger>`
and records the change in the run; `harness loglevel --reset` restores
the levels it changed. Typical use: `org.springframework.web DEBUG` to see
handler and message-converter selection without a restart.

### Database

- `harness psql` — interactive `psql` via `compose exec postgres` (for
  humans).
- `harness sql "<query>"` — non-interactive, output recorded in the run
  (for agents). Read-only by default (`SET TRANSACTION READ ONLY`);
  `--write` opts out.
- Optional, per-investigation: `harness up --debug --sql-log` sets
  `log_min_duration_statement=0` on Postgres through the overlay.

## Changes

- `deploy/docker/compose.debug.yml` — actuator exposure, optional SQL log.
- `tools/harness` — `diag`, `loglevel`, `psql`, `sql` (+ unit tests for
  output assembly and the read-only guard).
- Docs: usage notes; AGENTS.md verification commands.

## Done when

- `harness diag` on the debug stack produces a bundle whose `mappings`
  shows the `consumes` media types of `POST /api/v1/recipes`.
- `harness loglevel org.springframework.web DEBUG` changes the output of
  the next request without a restart, and `--reset` reverts it.
- `harness sql "select version, success from flyway_schema_history"`
  returns the applied migrations into the run folder.
