# Step 1 — Stack control and API driving

**Status:** planned · [Back to the plan](index.md)

## Goal

One command brings the full stack up on freshly built images, and an agent
can make authenticated API calls whose requests and responses are recorded
in a run folder alongside the services' logs.

## Prerequisites

- Decided ([decisions](index.md#decisions)): `tools/harness/`,
  TypeScript, Node native type stripping, devenv `scripts.harness`.

## Scope

In:

- The `@mimos/harness` workspace skeleton, the CLI dispatcher, and the run
  folder (`summary.md`, `command.json`, `latest` link, redaction).
- `up`, `down`, `reset`, `status`, `token`, `api`, `logs` (plain capture —
  request-ID filtering comes in step 3).
- devenv wiring: `scripts.harness`, `jq` in `packages`.

Out: browser driving (step 2), the debug overlay (step 3 onward).

## Design

### Environment

- `devenv.nix` gains `scripts.harness.exec` running
  `node tools/harness/src/cli.ts "$@"`, so `harness` is on the PATH in the
  shell; agents invoke `devenv shell -- harness <cmd>` (≈0.3 s when the
  shell is cached).
- `jq` joins `packages` for ad-hoc inspection of JSON evidence.
- `enterTest` gains `harness --version` so `devenv test` catches a broken
  entry point.

### Stack control

- `harness up` runs `docker compose -f deploy/docker/compose.yml build`
  then `up -d --wait` (AGENTS.md notes that `up` alone does not rebuild —
  the harness removes that trap). `--no-build` skips the build; `--debug`
  adds `compose.debug.yml` once it exists (step 3).
- On a failed `--wait`, `up` captures `compose ps` and the logs of every
  non-healthy service into the run folder and exits non-zero, so a broken
  boot is diagnosable without a second command.
- `harness down` = `compose down`; `harness reset` = `compose down -v
  --remove-orphans` (wipes the database — prints what it is about to remove).
- `harness status` reports per-service state and health, and flags stale
  images: image creation time vs. the latest commit/working-tree change
  under that service's build inputs.
- Ports and URLs come from the same env vars compose uses (`API_PORT`,
  `KEYCLOAK_PORT`, `WEB_PORT`, …) with the same defaults.

### API driving

- `harness token [--as test|test2] [--decode]` — password grant against the
  `mimos-web` client (direct access grants are already enabled in
  `deploy/keycloak/mimos-realm.json`; CI uses the same flow). Users and
  passwords default to the realm's test users. `--decode` prints the JWT
  claims (no signature check — it is a debugging aid).
- `harness api <METHOD> <path> [--body <file>|-] [--header K:V]...
  [--as <user>|--anon]` — sends the request (JSON `Content-Type` by default
  when a body is given; overridable with `--header` so content-type bugs
  can be reproduced exactly), prints status and body, and appends the full
  exchange (method, URL, request headers/body, status, response
  headers/body, duration) to `api/exchanges.jsonl`.
- Problem-details responses are pretty-printed in `summary.md`.

### Logs

- `harness logs [--service <s>] [--since <t>]` writes each service's
  `compose logs` output to `logs/<service>.log` in the run folder.
- Every `api` and (later) `ui` run also captures the logs for its time
  window automatically, so a run folder is self-contained.

### Run folder

- New run per evidence-producing command, named
  `<UTC timestamp>-<command or scenario>`; `--run <dir>` appends to an
  existing run so a multi-command investigation stays together.
- `command.json` records argv, git SHA, dirty flag, and the image IDs in
  use.
- Redaction: `Authorization` headers, `access_token`/`refresh_token`/
  `id_token` fields, and `password` fields are replaced before writing.
- `.harness/` is added to `.gitignore` and `.dockerignore`.

## Changes

- `tools/harness/` — `package.json` (`@mimos/harness`, private, no runtime
  deps), `src/cli.ts`, command modules, `test/` (`node --test`).
- Root `package.json` — add `tools/*` to workspaces.
- `devenv.nix` — `scripts.harness`, `jq`, `enterTest` check.
- `.gitignore`, `.dockerignore` — `.harness/`.
- AGENTS.md — repo layout (`tools/harness/`), verification commands,
  update the Dev environment bullet.

## Tests

`node --test` unit tests for argument parsing, run-folder naming and
`latest` linking, redaction, and the stale-image check (with injected
clock and filesystem/git readers). Compose-dependent behavior is verified
manually in this step and by CI in step 6.

## Done when

- `devenv shell -- harness reset && devenv shell -- harness up` reaches a
  healthy stack from a clean state.
- `harness api GET /api/v1/me` returns the test user's profile and the run
  folder holds the redacted exchange and the service logs.
- `harness api POST /api/v1/recipes --body <file>` either reproduces the
  415 or succeeds — either outcome is recorded and narrows the cause (see
  [step 7](step-7-recipe-415.md)).
- `npm test -w @mimos/harness` passes; docs and AGENTS.md updated.
