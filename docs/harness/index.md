# Agent harness — plan

**Status:** in progress — step 1 (stack control and API driving) is built;
its page documents usage. This page is the high-level design; each step has
its own page (linked below) that gets fleshed out and updated as it is
picked up. Edit freely — the plan is meant to be revisited.

## Why

Bugs on the local stack (the first one: a `415 Unsupported Media Type` when
creating a recipe from the web app) are currently investigated by guesswork:
there is no scripted way to reproduce a browser flow, no way to tie a
browser request to an API log line, almost no diagnostics exposed, and no
debugger wiring. The harness gives an agent (or a human) one entry point to
**deploy, drive, observe, and debug** the full stack, and leaves every piece
of evidence on disk where it can be read, diffed, and attached to a fix.

## Goals

- **Local deployment** — bring the compose stack up, down, and back to a
  clean state with one command; always running freshly built images.
- **Driving the app** — at the API level (authenticated HTTP calls) and at
  the browser level (scripted Playwright scenarios, plus an MCP browser for
  exploration).
- **Logs** — every service's logs captured per run; one request traceable
  across browser → web → API → plugin by request ID.
- **Diagnostics** — a snapshot of the running system (health, route
  mappings, config, migrations, versions) and live log-level changes.
- **Debugger** — attach to the API's JVM (scripted `jdb` for agents, any
  IDE for humans) and the Next.js server's Node inspector.

## Non-goals

- Replacing the test suites. Scenarios complement `./mvnw verify` and the
  plugin tests; a bug found with the harness still gets a unit/integration
  test at the right layer.
- Production observability (tracing backends, metrics dashboards). The
  harness is a local tool; anything it adds to the product must be useful
  and safe beyond it (e.g. request IDs) or live in an opt-in overlay.
- Load or performance testing.

## Principles

1. **Evidence on disk.** Every evidence-producing command writes to a run
   folder under `.harness/runs/` (gitignored) with a `summary.md` to read
   first. Agents read files, not terminal scrollback.
2. **One entry point.** A single `harness` command with subcommands. Agents
   call it as `devenv shell -- harness <cmd>` so it works with or without
   direnv.
3. **Agent tooling comes from devenv/Nix.** Browsers, the Playwright MCP
   server, `jdb`, `jq` — all provided by `devenv.nix` from nixpkgs, pinned
   by `devenv.lock`. No `npx playwright install`, no ad-hoc downloads, no
   tools assumed on the host beyond Nix, devenv, and the Docker daemon.
4. **The product stays untouched.** `deploy/docker/compose.yml` is not
   modified for the harness; debug-only behavior (JDWP, extra actuator
   endpoints, JSON logs) lives in an opt-in overlay,
   `deploy/docker/compose.debug.yml`. Self-hosters never see it. Product
   changes the harness motivates (request IDs, error logging) must stand on
   their own.
5. **Safe by default.** Debug ports bind to `127.0.0.1` only; JDWP and heap
   dumps exist only in the overlay; tokens and `Authorization` headers are
   redacted before anything is written to a run folder.
6. **Scenarios become tests.** A scenario written to reproduce a bug is kept
   as its regression test and runs in CI.

## Architecture

```
            devenv shell (nixpkgs: node 22, jdk21/jdb, jq, playwright browsers, playwright-mcp)
                                              │
                                     harness <command>
     ┌──────────────┬───────────────┬─────────┴──────┬───────────────┬────────────────┐
  up/down/       token/api       ui <scenario>      logs           diag/           debug
  reset/status   (HTTP + OIDC    (Playwright,       (compose       loglevel/       (jdb → :5005,
  (compose)      password grant) chromium)          logs, by       psql/sql        node → :9229)
     │               │               │              request ID)    (actuator,          │
     ▼               ▼               ▼                  │          postgres)           │
  ┌──────────────────────────── compose stack (+ compose.debug.yml) ──────────────────────────┐
  │  web :3000  ──►  api :8080  ──►  postgres :5432        keycloak :8081     country-week    │
  └───────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                         .harness/runs/<timestamp>-<name>/   (summary.md first)
```

Exploratory browsing goes through the Playwright MCP server (`playwright-mcp`
from nixpkgs, registered in `.mcp.json`), which uses the same devenv-provided
browsers.

## Command surface (target)

| Command | Purpose | Step |
| --- | --- | --- |
| `harness up [--debug] [--no-build]` | Build images, `compose up --wait`, optionally with the debug overlay (`--debug` from step 3) | 1 |
| `harness down` / `harness reset` | Stop the stack / stop and wipe volumes | 1 |
| `harness status` | Service state, health, image build time vs. latest source change | 1 |
| `harness token [--as <user>] [--decode]` | Access token via password grant | 1 |
| `harness api <METHOD> <path> [--body <file>\|-] [-H K:V]... [--as <user>\|--anon]` | Recorded HTTP call to the API | 1 |
| `harness logs [--service <s>] [--since <t>] [--request-id <id>]` | Capture/filter compose logs | 1, 3 |
| `harness ui <scenario> [--headed] [--as <user>]` | Run a Playwright scenario with trace, HAR, console, screenshots | 2 |
| `harness diag` | Diagnostics snapshot | 4 |
| `harness loglevel <logger> <level>` | Change a log level live via actuator | 4 |
| `harness psql` / `harness sql "<query>"` | Interactive / recorded database access | 4 |
| `harness debug break <location> --then "<harness cmd>"` | Scripted `jdb` breakpoint capture | 5 |

## Run folder

```
.harness/runs/2026-10-01T15-04-12Z-create-recipe/
├── summary.md            # read first: what ran, outcome, failing requests, pointers
├── command.json          # one entry per invocation: argv, git SHA, dirty flag, image IDs
├── compose/              # build/up output and service states (harness up)
├── api/exchanges.jsonl   # recorded HTTP request/response pairs (redacted)
├── browser/              # trace.zip, network.har, console.jsonl, screenshots/
├── logs/                 # <service>.log for each compose service
├── diag/                 # diagnostics snapshot
└── debug/                # breakpoint hits: stack, locals, expressions
```

`.harness/runs/latest` points at the most recent run.

## Steps

Ordered by dependency. Each is one reviewable change (or a few), with tests
and docs, per AGENTS.md.

| # | Step | Status |
| --- | --- | --- |
| 1 | [Stack control and API driving](step-1-stack-and-api.md) | Done |
| 2 | [Browser driving](step-2-browser.md) | Planned |
| 3 | [Logs and request correlation](step-3-logs.md) | Planned |
| 4 | [Diagnostics](step-4-diagnostics.md) | Planned |
| 5 | [Debugger](step-5-debugger.md) | Planned |
| 6 | [Agent skill, docs, and CI](step-6-skill-docs-ci.md) | Planned |
| 7 | [First use: the recipe 415](step-7-recipe-415.md) | Planned |

## Decisions

All made by the owner (2026-10-01). Changing one means editing this table
and the affected step pages in the same change.

| Decision | Outcome | Applies from |
| --- | --- | --- |
| Where the plan lives | In the repo under `docs/harness/`: this page plus one page per step. | — |
| Agent tooling | Comes from devenv/Nix (principle 3). **Product** paths (build, tests, compose, the existing CI jobs) still must not depend on devenv; the harness may and does. | Step 1 |
| Harness location and language | `tools/harness/` as the `@mimos/harness` npm workspace, TypeScript run via Node's native type stripping (like `plugins/country-week`), exposed as a devenv `scripts.harness` entry. Adds a top-level `tools/` directory. | Step 1 |
| Playwright as a dependency | `@playwright/test` as a devDependency of `@mimos/harness` only, pinned to the exact `playwright-driver` version in nixpkgs. | Step 2 |
| `.mcp.json` | Committed, so every agent session gets the browser MCP server. | Step 2 |
| Actuator authorization in the debug overlay | Any valid token in v1; revisit with an admin realm role if actuator exposure ever reaches non-debug deployments. | Step 4 |
| Scenarios in CI | Run in the compose job after the stack is healthy; the run folder is uploaded as a CI artifact on failure. CI gets browsers **through devenv** (the harness needs the devenv shell anyway) — one source of tooling for agents and CI. | Step 6 |
