---
name: mimos-harness
description: Run, reproduce, observe, and debug the Mimos stack locally with the agent harness (`devenv shell -- harness …`). Use when asked to run, start, or launch the app; to reproduce a bug or failing flow; to find out why something fails locally (a 4xx/5xx, a broken page, a request the API rejects); to read logs for a request; to inspect routes, config, or migrations of the running stack; or to set a breakpoint in the API.
---

# Mimos harness

The harness (`tools/harness`, `@mimos/harness`) is the one entry point for
deploying, driving, observing, and debugging the local compose stack. Every
command writes evidence to `.harness/runs/<run>/`; read
`.harness/runs/latest/summary.md` first.

The source of truth is **AGENTS.md → "Debugging the running stack"** (the
investigation loop) and **`docs/harness/usage.md`** (every command, its
options, and what it writes). Read both before investigating; this skill
only points at them.

## Rules

- Run it as `devenv shell -- harness <command>` from the repo root. Never
  `npx playwright install`, host-installed tools, or a bare `docker compose
  up` (it does not rebuild images).
- Follow the loop: `harness up --debug` → **reproduce** (`harness ui
  <scenario>` or `harness api …`; no reproduction, no fix) → read the
  evidence (`summary.md`, then HAR, `harness logs --request-id`, `harness
  diag`) → hypothesize from evidence, narrow with `harness loglevel` or
  `harness debug break` → fix at the right layer with a test there →
  re-run the reproduction until it passes → keep the scenario.
- Hypotheses come from the run folder, not from guessing at code. Quote the
  evidence (request ID, log line, route, hit) when explaining a cause.
- `harness loglevel --reset` after changing log levels.

## Launching the app (for the `run` skill)

```sh
devenv shell -- harness up            # build images, start, wait until healthy
devenv shell -- harness status        # state, health, stale images
```

Web at `http://localhost:3000` (sign in as `test` / `mimos-test`; `test2`
for a second user), API at `http://localhost:8080`, Keycloak at
`http://localhost:8081`. To see a change working: `devenv shell -- harness
ui login` (or the scenario for the flow), then read the summary and the
screenshots under `browser/<scenario>/results/`. For ad-hoc browsing, the
`playwright` MCP server from `.mcp.json` drives the same Chromium.
