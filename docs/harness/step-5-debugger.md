# Step 5 — Debugger

**Status:** done (2026-10-01) · [Back to the plan](index.md)

## Goal

An agent can stop the API at a line, capture the stack and local
variables, and resume — non-interactively, with the result in the run
folder. A human can attach an IDE to the same port. The Next.js server is
inspectable too.

## Using it

```sh
devenv shell -- harness up --debug        # JDWP (5005) and the Node inspector (9229) need the overlay
devenv shell -- harness debug break MeController:27 --then "api GET /api/v1/me" --print 'profile.subjectId()'
cat .harness/runs/latest/debug/1/hits.md
devenv shell -- harness debug break RecipesController.createRecipe --then "ui create-recipe"   # "not hit" is evidence too
```

```
harness debug break <Class:line | Class.method[(argument types)]> [--then "<harness command>"]
                    [--print <expr>]... [--hits <n>] [--timeout <s>]
```

| Option | Meaning |
| --- | --- |
| `<Class>` | A simple name from the API's sources (`MeController`), resolved through `apps/api`, `core/*`, and `integrations/*` (generated OpenAPI sources too); `Outer$Inner` for nested classes; a fully qualified name for anything else (library classes). An ambiguous or unknown simple name is a usage error. |
| `Class:line` / `Class.method` | `stop thread at` / `stop thread in`. Overloads take jdb's argument list: `Foo.bar(int, java.lang.String)`. |
| `--then "<command>"` | A harness command that sends a request — `api` or `ui` — run once the breakpoint is set. It records into the same run (its own section in `summary.md`), so the request's exchange, HAR, and logs sit next to the hits. |
| `--print <expr>` | Evaluated in the hitting frame at each hit (repeatable). Method calls work (`profile.subjectId()`); `--print profile` gives the object's `toString()`, where `locals` shows only `instance of …(id=…)`. |
| `--hits <n>` | Hits to capture before detaching (default 1). Further hits are resumed without capture and counted. |
| `--timeout <s>` | Give up after this long (default 60). With `--then`, the session also ends 2 s after the trigger exits, so "not hit" arrives as soon as the request has finished. |

Exit code 0 when the breakpoint was hit, 1 when it was not hit or could not
be set (no such method, no code on that line — jdb's reason is in the
summary). The trigger's own exit code is reported, not propagated: a 415
the breakpoint helps explain is not a failed debug session.

### What a session writes

`debug/<n>/` per session in the run (numbered, like `sql/`):

| File | Holds |
| --- | --- |
| `hits.md` | The breakpoint as set (or deferred), the trigger and its exit code, the result, API health after detaching, then per hit: the source line, arguments and locals, `dump this`, each `--print`, the project's own frames (`io.github.noahhhx.mimos.*`), and the top 25 frames. |
| `jdb.log` | jdb's raw output — full stacks, thread lists — for anything `hits.md` trims. |
| `trigger.log` | The trigger's stdout and stderr. |

`summary.md` gets the breakpoint, trigger, result, API health, and the
first hit's locals. Bearer tokens and JWTs are redacted from all of it;
other values are written as the JVM holds them, so a breakpoint in code
that holds a password would record it (the stack is local, the run folder
gitignored).

### Attaching a human debugger

- **JVM:** IntelliJ "Remote JVM Debug" or VS Code's Java `attach`
  configuration on `localhost:5005` (override with `API_DEBUG_PORT`). JDWP
  takes one debugger at a time: detach the IDE before `harness debug
  break`, which otherwise fails with a hint saying so.
- **Next.js server:** Chrome → `chrome://inspect` → Configure →
  `localhost:9229` (override with `WEB_INSPECT_PORT`), or VS Code's Node
  `attach` on port 9229 with `remoteRoot` `/app`. This covers
  server-rendered public pages and route handlers. Browser-side code is
  covered by step 2's trace, console, and HAR.

## Design

### JVM (API)

- The overlay sets
  `JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`
  and publishes `127.0.0.1:5005:5005`. No image change: the JVM reads
  `JAVA_TOOL_OPTIONS` on its own.
- Agents use `jdb` from the JDK devenv already provides (`pkgs.jdk21`) —
  no new tooling. Classes keep variable debug info: the Maven compiler
  plugin's default is full `-g`, and nothing in the build turns it off
  (`locals` works).

### How `debug break` drives jdb

1. `jdb -attach localhost:5005 -sourcepath <the source roots>`, then
   `ignore uncaught java.lang.Throwable` — jdb otherwise catches uncaught
   exceptions with a suspend-all policy.
2. `stop thread at|in <location>` — suspends only the hitting thread, so
   health checks and other requests keep being served.
3. Starts the `--then` trigger as `harness <command> --run <this run>`.
4. On hits: `threads` (the event names the thread; `thread` and `resume`
   take its ID), then per hit `thread <id>`, `where`, `locals`,
   `dump this`; then each `--print`; then `resume <id>`.
5. `clear <location>`, resumes any hit beyond `--hits`, and `quit`, which
   disposes the connection: the JVM drops the breakpoint and resumes
   every thread. If the harness dies instead, the socket closes with the
   same effect — a crash during development left three blocked requests,
   and they completed the moment jdb went away.
6. Checks `/actuator/health` and records it, so "the API keeps serving" is
   part of the evidence.

## Design notes

Decisions made while building, beyond the plan:

- **`stop thread` leaves jdb without a current thread.** jdb only selects
  a thread for suspend-all events, so `where`/`locals` after a `stop
  thread` hit answer "No current thread". The session looks the thread up
  by name in `threads` and selects it with `thread <id>`.
- **Commands are sent one at a time, each waiting for its answer.** jdb
  runs `print`/`dump` on a separate thread; sent back to back, their
  outputs interleaved and one `print` answered with another expression's
  value. A command is done when its expected answer has arrived, jdb shows
  a prompt, and the output has been quiet for 150 ms.
- **Hits suspended together are snapshotted before any `--print`.** jdb
  invokes methods with JDI's default, which resumes every suspended
  thread until the call returns. With three concurrent hits, the first
  hit's `--print` let the other two run past the breakpoint. So stack,
  locals, and `this` (which run no code) are captured for every pending
  hit first; a later hit's expressions are evaluated only if its thread
  is still at the same frame, and otherwise say why they were not.
- **Simple class names are resolved from the sources.** jdb needs
  fully-qualified names; agents know `MeController`. Resolving from the
  source roots also turns a typo into a usage error instead of a
  "deferred" breakpoint that never fires. A class that is genuinely not
  loaded yet is deferred by jdb, said so in the summary, and a "not hit"
  then points at the class name.
- **`--then` accepts `api` and `ui` only**, the commands that send a
  request, and must not pass `--run`. It is split like a shell would
  (quotes, backslashes, no expansion), since the plan's
  `--then "<harness cmd>"` is one string.
- **Exit codes:** hit → 0, not hit or not set → 1. The trigger's exit code
  is reported only.
- **Node:** `NODE_OPTIONS=--inspect=0.0.0.0:9229` on `web`, published on
  `127.0.0.1:9229`. Next's standalone server is one process, so the one
  inspector covers it (checked: a DevTools-protocol client evaluated
  `process.argv[1]` → `/app/apps/web/server.js`). Agent-driven Node
  breakpoints (a small CDP client mirroring `debug break`) stay
  **deferred** until a bug needs them.

### Safety

JDWP and the Node inspector allow arbitrary code execution. They exist only
in `compose.debug.yml`, bind to `127.0.0.1`, and the overlay's header
comment says so. The default stack never exposes them.

## Changes

- `deploy/docker/compose.debug.yml` — JDWP on `api`, the Node inspector on
  `web`, both on loopback ports (`API_DEBUG_PORT`, `WEB_INSPECT_PORT`).
- `tools/harness` — `src/jdb.ts` (locations, source roots and class
  resolution, the jdb session, transcript parsers); the `debug break`
  command (`src/commands/debug.ts`); `debugPorts` in `src/config.ts`;
  tests.
- Docs: this page, the plan, step 7's evidence log, AGENTS.md.

## Tests

`npm test -w @mimos/harness`, against transcripts recorded from the
running API:

- locations (`Class:line`, `Class.method`, signatures, `<init>`, nested
  classes, rejects); `stop thread` commands; class resolution (nested,
  qualified, ambiguous, unknown) on a temporary tree and the repo's real
  roots.
- prompts (none, a thread's, a name with spaces); `stop` set, deferred,
  and refused (no such method, no code at a line).
- hit events (three concurrent, source line optional, grouped digits);
  `threads` (names with spaces, the one at the breakpoint); `where`
  (missing line info, `1,000`); `locals` (arguments, empty sections,
  no debug info); `print`/`dump` (values, multi-line objects, failures,
  when an answer is complete).
- `--then` word splitting; argument parsing and usage errors; a hit's
  rendering in `hits.md`.

## Verification (2026-10-01)

- `harness up --debug` → `api` publishes `127.0.0.1:5005`, `web`
  `127.0.0.1:9229`; nothing else listens on them.
- `harness debug break MeController.getMe --then "api GET /api/v1/me"` →
  hit at line 26 with stack (145 frames; Mimos frames `MeController.getMe`
  and `RequestLoggingFilter`), `this` (`currentUser`), in 3.5 s; the
  trigger's 200 in the same run; health `UP` afterwards. At line 27,
  `locals` lists `profile` and `--print 'profile.subjectId()'` its value.
  (The plan's `MeController.me` names no method — the session reports
  `No method me in …MeController` and exits 1.)
- `harness debug break MeController:27 --then "api GET /api/v1/public/recipes --anon"`
  → not hit, 2 s after the trigger, exit 1; health `UP`.
- Three concurrent `GET /api/v1/me` with `--hits 1` → one captured, two
  released, all three 200 within 1.7 s. With `--hits 3` → three hits on
  three threads with their own locals; the first's `--print` evaluated,
  the others explained.
- A DevTools-protocol client on `localhost:9229` evaluated in the Next.js
  server; `jdb` attached to `localhost:5005` as an IDE would (same JDWP
  socket). Attaching a GUI IDE is left to the first human who needs it.
- On the plain stack (`harness up`), neither port is published, and
  `harness debug break` fails in under a second with exit 1: jdb's
  `Connection refused` and a pointer to `harness up --debug`. It leaves
  no empty run behind.
- On the recipe 415 (step 7): `RecipesController.createRecipe` is not hit
  by `ui create-recipe`, and is hit by `api POST /api/v1/recipes` with a
  JSON body (`recipeInput.getTitle()` = the posted title).
