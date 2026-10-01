# Step 5 — Debugger

**Status:** planned · [Back to the plan](index.md)

## Goal

An agent can stop the API at a line, capture the stack and local
variables, and resume — non-interactively, with the result in the run
folder. A human can attach an IDE to the same port. The Next.js server is
inspectable too.

## Prerequisites

Steps 1–4 (debug overlay, run folder, scenarios to trigger requests).

## Design

### JVM (API)

- The overlay sets
  `JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`
  and publishes `127.0.0.1:5005:5005`. No image change: the JVM reads
  `JAVA_TOOL_OPTIONS` on its own.
- Humans: attach IntelliJ/VS Code ("Remote JVM Debug", localhost:5005).
- Agents: `jdb` ships with the JDK that devenv already provides
  (`pkgs.jdk21`) — no new tooling.

### `harness debug break`

```
harness debug break <Class:line | Class.method> \
  [--then "<harness subcommand>"] [--print <expr>]... \
  [--hits <n>] [--timeout <s>]
```

1. Spawns `jdb -attach localhost:5005` with source path set to the module
   source roots.
2. Sets the breakpoint with `stop thread at|in` — suspending only the
   hitting thread, so health checks and other requests keep working.
3. Runs the `--then` trigger (e.g. `ui create-recipe` or
   `api POST /api/v1/recipes --body …`) concurrently.
4. On each hit: `where`, `locals`, `dump this`, and each `--print`
   expression; then `cont`.
5. After `--hits` hits (default 1) or `--timeout`, clears the breakpoint
   and detaches cleanly (never leaves the JVM suspended).
6. Writes `debug/hits.md` (and the raw jdb transcript). **"Breakpoint not
   hit"** is reported explicitly — it is evidence too (e.g. a request
   rejected before reaching the controller).

To check first: `locals` needs classes compiled with variable debug info.
Maven's compiler plugin defaults to full `-g`; confirm nothing in the
build turns it off.

### Node (Next.js server)

- The overlay sets `NODE_OPTIONS=--inspect=0.0.0.0:9229` on `web` and
  publishes `127.0.0.1:9229:9229`. Humans attach Chrome DevTools or VS
  Code; this covers server-rendered public pages and route handlers.
- Agent-driven Node breakpoints (a small CDP client mirroring
  `debug break`) are **deferred** until a bug needs them.
- Browser-side code is covered by step 2's trace, console, and HAR.
  Production browser source maps are a possible later addition (a web
  build-time change, so it needs its own decision).

### Safety

JDWP and the Node inspector allow arbitrary code execution. They exist only
in `compose.debug.yml`, bind to `127.0.0.1`, and the overlay's header
comment says so. The default stack never exposes them.

## Changes

- `deploy/docker/compose.debug.yml` — JDWP, Node inspector, ports.
- `tools/harness` — `debug break` (+ unit tests for the jdb transcript
  parser against recorded transcripts).
- Docs: usage notes, IDE attach instructions; AGENTS.md.

## Done when

- `harness debug break MeController.me --then "api GET /api/v1/me"`
  writes a hit with stack and locals, and the API keeps serving afterwards.
- A breakpoint that is never hit times out with an explicit "not hit"
  result and leaves the JVM running.
- An IDE attaches to `localhost:5005`; Chrome DevTools to
  `localhost:9229`.
