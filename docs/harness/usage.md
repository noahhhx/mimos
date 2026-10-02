# Using the harness

The agent harness deploys, drives, observes, and debugs the local compose
stack, and leaves every piece of evidence in a run folder. This page is
the reference: every command, what it writes, and how CI uses it. The
[plan](index.md) holds the design; each step's page has the design notes
behind its commands.

## Setup

The harness needs Nix, [devenv](https://devenv.sh), and a Docker daemon on
the host. Everything else comes from the devenv shell — Node, the JDK
(`jdb`), `jq`, Chromium, and the Playwright MCP server. Run `npm ci` once
at the repo root (for `@playwright/test`), then call every command from
the repo root as:

```sh
devenv shell -- harness <command> [options]
devenv shell -- harness --help             # the command list
devenv shell -- harness <command> --help   # one command's options
```

With direnv, `harness` is on the PATH directly. Never `npx playwright
install`: the browsers come from nixpkgs, and `@playwright/test` is
pinned to the same version
([step 2](step-2-browser.md#design-notes)).

## The investigation loop

When something fails on the running stack, agents (and humans) follow
this loop. AGENTS.md ("Debugging the running stack") is the source of
truth; the `mimos-harness` Claude Code skill in `.claude/skills/` points
there.

1. **Start** — `harness up --debug`: fresh images plus the debug overlay.
2. **Reproduce** — a scenario (`harness ui <name>`) or an API call
   (`harness api`). No reproduction, no fix.
3. **Read the evidence** — `summary.md` first, then the HAR, `harness logs
   --request-id <id>`, and `harness diag`.
4. **Hypothesize from evidence**; narrow it with `harness loglevel` or
   `harness debug break`.
5. **Fix** at the right layer, with a test at that layer.
6. **Re-run the reproduction**; it must pass.
7. **Keep the scenario** as a regression test — CI runs it.

[Step 7](step-7-recipe-415.md) is a worked example: each step's evidence
narrowing the recipe 415.

## Commands

### Stack control — [step 1](step-1-stack-and-api.md)

| Command | What it does |
| --- | --- |
| `harness up [--no-build] [--debug [--sql-log]]` | `compose build`, then `up -d --wait`. `--debug` adds `deploy/docker/compose.debug.yml`: JSON API logs, actuator diagnostics, JDWP on `127.0.0.1:5005`, and the Node inspector on `127.0.0.1:9229`. `--sql-log` makes Postgres log every statement. If the stack is not healthy, the unhealthy services' last log lines go into `summary.md`. |
| `harness down` | `compose down`. The database volume survives. |
| `harness reset` | `compose down -v --remove-orphans`, which wipes the database. |
| `harness status` | Each service's state and health, and whether its image is `fresh` or `stale` (with the changed file named). It only prints, without a run folder. |

### Driving the app — steps [1](step-1-stack-and-api.md) and [2](step-2-browser.md)

| Command | What it does |
| --- | --- |
| `harness token [--as <user>] [--decode]` | An access token for `test` (default) or `test2`, printed only and never written to disk. |
| `harness api <METHOD> <path> [--body <file>\|-] [-H Name:Value]... [--as <user>\|--anon]` | Sends exactly the recorded headers (`-H 'Name:'` removes one). Records the exchange in `api/exchanges.jsonl` and the run's logs. Exit 1 for a status of 400 or above. |
| `harness ui <scenario> [--headed] [--as <user>]` | Runs `tools/harness/scenarios/<scenario>.spec.ts` in Chromium and writes a trace, HAR, console log, and screenshots (plus a video on failure) under `browser/<scenario>/`. `summary.md` gets the failing step, every API call with its request ID, and each failed request in full with its log lines. Exit 1 when a test failed. |

Scenarios: `login` (sign-in smoke), `create-recipe` (writes a recipe
through the form; the recipe 415's regression test), `edit-recipe`
(repeated tags, partial nutrition, an unmeasured ingredient, cancelling
and saving an edit), `recipe-form-errors` (browser-blocked input, per-row
form errors, Enter submits), `kitchen-home` (Tonight, the week rail, and
adding the plugin's thought; it plans the week's earlier evenings and
removes what it planned), `app-pages` (every signed-in page loads
without errors), and `app-nav` (the app nav has no vertical overflow,
which would show a scrollbar beside its links). See [writing a scenario](step-2-browser.md#writing-a-scenario).
For exploration before a scenario exists, `.mcp.json` registers a
Playwright MCP browser ([step 2](step-2-browser.md#exploring-with-the-mcp-browser)).

### Observing — steps [3](step-3-logs.md) and [4](step-4-diagnostics.md)

| Command | What it does |
| --- | --- |
| `harness logs [--service <s>]... [--since <t>] [--request-id <id>]` | Writes `logs/<service>.log`. `--request-id` also writes `logs/request-<id>.log` with every line that request produced, across services (exit 1 if none match). |
| `harness diag [--since <t>]` | Snapshots `diag/`: compose state and image freshness, actuator health/info/mappings/env/configprops/loggers/flyway/threaddump, `routes.txt` (each route's consumed and produced media types), migrations applied vs. in the repo, plugin manifests, and Keycloak metadata. Secrets are redacted. Exit 1 when it found a problem. Everything but health needs `--debug`. |
| `harness loglevel <logger> [<level>]` | Shows a logger's level, or sets it live through the actuator (needs `--debug`). |
| `harness loglevel --reset` | Restores every logger the harness changed. |
| `harness sql "<query>" [--write] [--csv]` | One query via `psql` in the postgres container, read-only unless `--write`. Writes `sql/<n>.txt`. |
| `harness psql` | Interactive `psql` for humans. Nothing is recorded. |

### Debugging — [step 5](step-5-debugger.md)

| Command | What it does |
| --- | --- |
| `harness debug break <Class:line\|Class.method> [--then "<api\|ui …>"] [--print <expr>]... [--hits <n>] [--timeout <s>]` | Attaches `jdb` to the API (needs `--debug`), sets the breakpoint, and runs the `--then` trigger. Each hit records the stack, locals, `this`, and `--print` values into `debug/<n>/hits.md`, then the thread resumes. Exit 0 when hit, 1 when not hit. A "not hit" is evidence too. |

For attaching IntelliJ, VS Code, or Chrome DevTools instead, see
[attaching a human debugger](step-5-debugger.md#attaching-a-human-debugger).

### Run folders

Every evidence-producing command creates `.harness/runs/<UTC
timestamp>-<command>/`, and `.harness/runs/latest` links to the most
recent one. `--run <name|path|latest>` appends to an existing run instead,
so a multi-command investigation stays in one folder. `summary.md` is the
first thing to read. `command.json` records each invocation's argv, exit
code, git SHA, and image IDs. Tokens, cookies, and passwords are redacted
before anything is written. The full layout is in the
[plan](index.md#run-folder).

## In CI

The `compose` job in `.github/workflows/ci.yml` boots the stack and runs
its devenv-free smoke tests, which are the self-host parity check. Then
it installs Nix and devenv, runs `devenv shell -- npm ci`, and runs every
scenario in `tools/harness/scenarios/` with `devenv shell -- harness ui
<name>`, all into one run folder. That run's `summary.md` is appended to
the job summary. When the job fails, `.harness/runs/` is uploaded as the
`harness-runs` artifact.

A scenario that reproduces an open bug goes in the step's `KNOWN_FAILING`
list. It still runs, and it must still fail. When it passes, the job fails
until the scenario is removed from the list, so a fix also turns its
reproduction into a regression test. The list is empty: `create-recipe`
left it when step 7 fixed the recipe 415.

To reproduce a CI failure locally: `harness up` (CI's stack runs without
the debug overlay), then `harness ui <name>`. Or download the artifact
and start from its `summary.md`.
