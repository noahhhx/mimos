# Step 1 — Stack control and API driving

**Status:** done (2026-10-01) · [Back to the plan](index.md)

## Goal

One command brings the full stack up on freshly built images, and an agent
can make authenticated API calls whose requests and responses are recorded
in a run folder alongside the services' logs.

## Using it

From the repo root (any directory works — the harness finds the repo from
its own location):

```sh
devenv shell -- harness reset        # stop the stack and wipe its volumes
devenv shell -- harness up           # build images, compose up --wait
devenv shell -- harness status       # state, health, stale images
devenv shell -- harness api GET /api/v1/me
devenv shell -- harness api POST /api/v1/recipes --body recipe.json --run latest
cat .harness/runs/latest/summary.md
```

| Command | What it does |
| --- | --- |
| `harness up [--no-build] [--debug]` | `compose build`, then `up -d --wait`. Output goes to `compose/build.log` and `compose/up.log`; service states to `compose/ps.json` and a table in `summary.md`. If the stack is not healthy, every service's logs are captured and the last lines of each unhealthy one are put in `summary.md`. `--debug` needs `compose.debug.yml` (step 3). |
| `harness down` | `compose down` — the database volume survives. |
| `harness reset` | `compose down -v --remove-orphans`, after printing the volumes it removes. |
| `harness status` | Per-service state and health, when each built image was made, and whether it is `fresh`, `stale` (a build input changed after the build — the file is named), `not built`, or running in a container on an older image. Prints only; no run folder. |
| `harness token [--as <user>] [--decode]` | An access token via the password grant, printed to stdout and never written to disk. `--decode` prints the JWT header and claims (no signature check). |
| `harness api <METHOD> <path> [--body <file>\|-] [-H Name:Value]... [--as <user>\|--anon]` | Sends the request, prints status and body, and records it. Exit code 0 below 400, 1 otherwise. |
| `harness logs [--service <s>]... [--since <t>]` | Writes `logs/<service>.log`. |

Every command takes `--help`. Users are the realm's test users `test`
(default) and `test2`.

### `harness api` details

- The headers sent are exactly the ones recorded: `Host`, `User-Agent:
  mimos-harness`, `Authorization` (unless `--anon`), and with a body
  `Content-Type: application/json` and `Content-Length`. `-H` adds or
  replaces a header (names match case-insensitively); `-H 'Name:'` removes
  one — e.g. `-H 'Content-Type:'` to send a body with no content type.
- The exchange (method, URL, request headers/body, status, response
  headers/body, duration, user) is appended to `api/exchanges.jsonl`.
- The logs of every service for the run's window (run start − 1 s to now)
  are written to `logs/`, so the run folder is self-contained.
- In `summary.md`, a failed request gets its response body (labelled
  "Problem details" for `application/problem+json`) and the API log lines
  written since it was sent. Step 3 narrows those by request ID.

### Run folder

- `.harness/runs/<UTC timestamp>-<command>/`, with a `-2`, `-3`, …
  suffix when two runs start in the same second; `.harness/runs/latest` is
  a relative symlink to the run used most recently.
- `--run <name|path|latest>` (on `up`, `api`, `logs`) appends to an
  existing run: `summary.md` gains a section and `command.json` an entry.
  `logs/` always holds the most recent capture.
- `command.json` is an array with one entry per invocation: argv, start
  and finish times, exit code, git SHA and dirty flag, and the image ID
  each running container uses.
- The run path is printed on stderr (`run: …`).

## Design notes

Decisions made while building, beyond the plan:

- **Staleness is mtime-based.** An image is stale when the newest file
  under its service's build inputs (`BUILD_INPUTS` in
  `tools/harness/src/config.ts`) is newer than the image. Commit times
  would flag every image built from the working tree and committed
  afterwards; checkouts, pulls, and stash pops rewrite files, so they count.
  Files come from `git ls-files` (so ignored build outputs don't count). A
  deleted file counts at its nearest surviving parent directory's mtime.
  The inputs are narrower than the Dockerfiles' `COPY` sources where those
  over-copy (the API image copies all of `apps/`); a test checks every input
  really is copied.
- **An image's build time is the later of `Created` and `LastTagTime`.** A
  build whose output matches an existing image (say, only a build stage
  changed) reuses that image and its old `Created`, but re-tags it. Using
  `Created` alone left such images "stale" forever.
- **Compose runs without `SOURCE_DATE_EPOCH`.** Nix shells (devenv
  included) export it as 1980-01-01 for reproducible builds, and BuildKit
  then stamps images with that date. The harness unsets it for compose, and
  `status` reports images dated before 2000 as `undated`, not stale (for
  example, ones built by `devenv shell -- docker compose build`).
- **`node:http`, not `fetch`.** `fetch` adds headers of its own (`Accept`,
  `Accept-Language`, `Sec-Fetch-Mode`, …), so the recorded request would
  not be the sent one. A header bug has to be reproducible exactly.
- **Redaction** covers the `Authorization`, `Proxy-Authorization`,
  `Cookie`, and `Set-Cookie` headers (the auth scheme is kept: `Bearer
  [REDACTED]`), the JSON or form fields `access_token`, `refresh_token`,
  `id_token`, `password`, and `client_secret` at any depth, and any
  JWT-shaped or `Bearer` string in free text, logs included. A JSON body
  with nothing to redact is stored byte-for-byte.
- **The devenv banner goes to stderr**, so `TOKEN=$(devenv shell --
  harness token)` captures only the token.
- **Docker images.** The web and country-week Dockerfiles `npm ci` the
  whole workspace from its manifests, so they copy
  `tools/harness/package.json` like every other workspace manifest. The
  harness's sources are not in any image.

## Changes

- `tools/harness/` — `@mimos/harness` (private, no runtime dependencies;
  `typescript` and `@types/node` as devDependencies): `src/cli.ts`
  (dispatcher), `src/commands/` (`stack`, `status`, `token`, `api`, `logs`),
  run folder, redaction, compose and HTTP helpers, staleness check;
  `test/` (`node --test`).
- Root `package.json` — `tools/*` workspace.
- `devenv.nix` — `scripts.harness`, `jq`, `harness --version` in
  `enterTest`, banner on stderr.
- `.gitignore`, `.dockerignore` — `.harness/`.
- `apps/web/Dockerfile`, `plugins/country-week/Dockerfile` — copy the new
  workspace manifest.
- CI `clients` job — harness typecheck and tests.
- AGENTS.md — repo layout, Dev environment and Agent harness bullets,
  verification commands.

## Tests

`npm test -w @mimos/harness`: argument parsing and request-header
assembly, run-folder naming, collisions, `latest` linking and `--run`
reuse, evidence files, redaction, staleness (with an in-memory working
tree), compose output parsing and log-window selection, and a check that
`BUILD_INPUTS` matches the Dockerfiles. Compose-dependent behavior was
verified by hand against the stack (below); CI covers it in step 6.

## Verification (2026-10-01)

- `harness reset && harness up` reached a healthy stack from a clean state
  (volumes removed, all images rebuilt).
- `harness api GET /api/v1/me` → 200 with the test user's profile; the run
  folder held the redacted exchange and the service logs. No JWT anywhere
  under `.harness/`.
- `harness api POST /api/v1/recipes --body recipe.json` → **201**: the API
  accepts a well-formed JSON recipe. Recorded in
  [step 7](step-7-recipe-415.md).
