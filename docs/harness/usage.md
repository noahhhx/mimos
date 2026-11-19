# Using the harness

The agent harness deploys, drives, observes, and debugs the local compose
stack, and leaves every piece of evidence in a run folder. This page is
the reference: every command, what it writes, and how CI uses it. The
[plan](index.md) holds the design; each step's page has the design notes
behind its commands.

## Setup

The harness needs Nix, [devenv](https://devenv.sh), and a Docker daemon on
the host. Everything else comes from the devenv shell — Node, the JDK
(`jdb`), `jq`, Chromium, the Playwright MCP server, and for `harness
mobile` the Android SDK (emulator, `adb`), Maestro, and a pinned browser
APK. `mobile` also needs `/dev/kvm`. Run `npm ci` once
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
| `harness up [--no-build] [--debug [--sql-log]]` | `compose build`, then `up -d --wait`, always with the [fakes](#fakes-of-third-party-services) in `deploy/docker/compose.fakes.yml`. `--debug` adds `deploy/docker/compose.debug.yml`: JSON API logs, actuator diagnostics, JDWP on `127.0.0.1:5005`, and the Node inspector on `127.0.0.1:9229`. `--sql-log` makes Postgres log every statement. If the stack is not healthy, the unhealthy services' last log lines go into `summary.md`. |
| `harness down` | `compose down`. The database volume survives. |
| `harness reset` | `compose down -v --remove-orphans`, which wipes the database. |
| `harness status` | Each service's state and health, and whether its image is `fresh` or `stale` (with the changed file named). It only prints, without a run folder. |

### Driving the app — steps [1](step-1-stack-and-api.md) and [2](step-2-browser.md)

| Command | What it does |
| --- | --- |
| `harness token [--as <user>] [--decode]` | An access token for `test` (default), `test2`, or an account `harness user create` made, printed only and never written to disk. |
| `harness api <METHOD> <path> [--body <file>\|-] [-H Name:Value]... [--as <user>\|--anon]` | Sends exactly the recorded headers (`-H 'Name:'` removes one). Records the exchange in `api/exchanges.jsonl` and the run's logs. Exit 1 for a status of 400 or above. |
| `harness ui <scenario> [--headed] [--as <user>]` | Runs `tools/harness/scenarios/<scenario>.spec.ts` in Chromium and writes a trace, HAR, console log, and screenshots (plus a video on failure) under `browser/<scenario>/`. `summary.md` gets the failing step, every API call with its request ID, and each failed request in full with its log lines. Exit 1 when a test failed. |

Scenarios: `login` (sign-in smoke), `create-recipe` (writes a recipe
through the form; the recipe 415's regression test), `edit-recipe`
(repeated tags, partial nutrition, an unmeasured ingredient, cancelling
and saving an edit), `calculated-nutrition` (lines link to
ingredients by name or by searching, a missing one is added as the
user's own, nutrition updates as you type, and the saved recipe shows
the same figures, ADR-0015 and ADR-0016), `recipe-form-errors` (browser-blocked input, per-row
form errors, Enter submits), `kitchen-home` (Tonight, the week rail, and
adding the plugin's thought; it turns the plugin on, chooses Italy for
the week, plans the week's earlier evenings, and puts all three back as
it found them), `plugin-opt-in` (a fresh account sees the plugin's week
panel only after turning Country of the Week on from the Plugins page,
ADR-0013), `country-wheel` (a fresh account spins, removes, chooses, and
changes a week's country, and the next week's wheel leaves both out,
ADR-0017), `app-pages` (every signed-in page loads
without errors), `app-nav` (the app nav has no vertical overflow,
which would show a scrollbar beside its links), `keycloak-theme`
(Keycloak's sign-in, error, and register pages wear the Mimos theme
and follow the app's light or dark choice, ADR-0021; screenshots at
phone and desktop width), `fuel-profile` (a fresh account saves its fuel
profile from the Fuel page, protein follows the goal, and each value the
API refuses gets its own error, ADR-0022), `recipe-pictures` (a new
recipe gets two pictures, which load in a row above its description,
re-encoded as JPEG and shrunk to 1600px; at phone width the row scrolls
and the page does not; cancelling a removal keeps both, saving one
leaves one, ADR-0023), `log-barcode` (logs 15 g of sugar by ingredient
search, then products from the scan dialog by typed barcode against the
fake Open Food Facts: an unknown barcode, an outage, a serving size, and
30 g of Nutella, while the camera is refused; then, with the camera
allowed, Chromium's fake camera films Nutella's barcode
(`src/fake-camera.ts` draws it as a Y4M) and the zxing polyfill reads it,
with its wasm from the web origin and no request to any other host, and
a stubbed native `BarcodeDetector` reads it with none of zxing loaded,
ADR-0025; this scenario runs the full Chromium, since the headless shell
has no camera), and `account-data` (registers
two fresh accounts: one writes a recipe and downloads its export, which
it cannot import over its own data; the other imports it, ADR-0011). See [writing a scenario](step-2-browser.md#writing-a-scenario).
For exploration before a scenario exists, `.mcp.json` registers a
Playwright MCP browser ([step 2](step-2-browser.md#exploring-with-the-mcp-browser)).

### The emulator — [step 8](step-8-emulator.md)

| Command | What it does |
| --- | --- |
| `harness mobile up [--no-build]` | Creates the AVD `mimos-pixel` under `.harness/android/` if missing, boots it headless (KVM, software GPU), installs the pinned browser (Cromite, `MIMOS_ANDROID_BROWSER_APK`) once, forwards 8080, 8081, and 3000 with `adb reverse`, proves from the device that the web app, the API, and Keycloak each answer an HTTP request (exit 1 if one does not), builds the debug APK (`./gradlew :androidApp:assembleDebug`), installs it, launches `io.github.noahhhx.mimos.debug`, and screenshots it. Running it again converges. `--no-build` reuses the last APK. |
| `harness mobile shot [name]` | Saves a screenshot to `mobile/<name>.png` (`shot.png`, `shot-2.png`, … without a name). |
| `harness mobile flow <name> [--run <run>]` | Runs `apps/mobile/flows/<name>.yaml` with Maestro on the installed app (`connect-and-sign-in`, `stay-signed-in`, `cook-a-recipe`, `create-recipe`, `recipe-picture`, `plan-a-week`, `shared-week`, `household-invite`, `theme-toggle`, `export-import`, `country-week`, `shopping-list`, `log-a-meal`, `log-planned-share`, `scan-barcode`, `plan-scanned-food`). It builds and installs nothing (that is `up`): it checks the emulator and the installed app, re-makes the reverse rules and probes the stack, clears the app's and Cromite's data, creates a fresh account as `harness user create` does, and passes `MIMOS_USER`, `MIMOS_PASSWORD`, and `MIMOS_WEB_ADDRESS` to the flow. A flow whose config has `tags: [household]` gets a second fresh account too, whose household the first joins (`harness household join`); its username, which the app shows as its name, is `MIMOS_PARTNER`. A flow whose config has `tags: [invitee]` gets a second fresh account on its own, `MIMOS_INVITEE`, with the same password, for a flow that signs out and joins as someone else. A flow whose config has `tags: [country-week]` has Country of the Week choose Italy for the account's next week through the API (turn the plugin on, `choose` with `IT` for the Monday after today in this machine's time zone, turn it off again; the step `Country of the Week` records the week), since Italy is the one cuisine the seed library has and a wheel's landing is random. Turning a plugin off keeps the household's pseudonym, so the plugin still has the choice once the flow turns it on. Maestro's output is captured rather than streamed, because it echoes what a flow types. The run gets `mobile/flows/<name>/` (`-2`, `-3` for a repeat in the same run): Maestro's JUnit `report.xml`, its screenshots and debug logs, `output.log`, `probe.txt`, and `app-requests.log`, with the account's password replaced by value. `summary.md` leads with the failed prerequisite or Maestro assertion, then the steps, then every request the app sent (ID, method, path) for `harness logs --request-id <id>`. A flow that passes with no request IDs in `app-requests.log` fails. Exit 1 on any failure. |
| `harness mobile logs` | Writes logcat for the app's process to `mobile/logcat.txt` (redacted); `summary.md` counts the lines and shows the last errors. With the app not running, the lines that name it. |
| `harness mobile down` | `adb emu kill`. Fine when nothing is running. |
| `harness user create` | A fresh Keycloak user through the admin API. Prints `MIMOS_USER=user-…` and `MIMOS_PASSWORD=mimos-test`. |
| `harness household join <owner> <member>` | The owner invites and the member joins, through the API. Prints `MIMOS_OWNER`, `MIMOS_OWNER_PASSWORD`, `MIMOS_MEMBER`, and `MIMOS_MEMBER_PASSWORD`. |

The device's `localhost:8080`, `:8081`, and `:3000` are the host's, so
the app and its browser use the same URLs as a desktop browser and tokens
carry the issuer the API accepts ([why](step-8-emulator.md#reaching-the-stack-adb-reverse)).
The reverse rules belong to the adb server that made them: after an
`adb kill-server` or a restart by another adb client, the device refuses
connections to localhost until `harness mobile up` or `harness mobile flow` runs again
(`flow` re-makes the rules itself). Accounts
from `user create` work with `--as` in `token`, `api`, and `ui`
(`harness ui login --as user-1a2b3c4d`). To start the AVD over, stop it and
delete `.harness/android/`. A browser is one command away for
exploration: `adb shell am start -a android.intent.action.VIEW -d
http://localhost:3000/ org.cromite.cromite`, then `harness mobile shot`.

The debug app logs each API request it sends (request ID, method, path; no
headers, query, or body) as a `MimosRequest` line in logcat; `flow` reads those
lines into the summary. Release builds do not log them.

`node tools/harness/src/api-parity.ts` lists the API operations the web's code
calls and the ones the app's code calls (a call to the generated client, not an
import), as web only, app only, both, and neither. Run it before and after a
feature lands on either side ([plan item 17](../plans/android-app/17-parity-and-docs.md)).

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

### Fakes of third-party services

Mimos talks to some outside services (intervals.icu, ADR-0022; Open Food
Facts, ADR-0025). The stack the harness runs replaces each with a fake: a
zero-dependency `node:http` service in `tools/harness/fakes/<service>/`, run by
`deploy/docker/compose.fakes.yml`, never by `compose.yml`. The overlay
points the API at the fake and publishes the fake on `127.0.0.1` only, so
a scenario drives its control routes over HTTP as it drives the API. Each
fake keeps its state in memory; a restart or `POST /_fake/reset` puts its
fixtures back. Its unit tests run with the harness's own (`npm test -w
@mimos/harness`).

**intervals.icu** (`intervals-icu-fake`, `http://127.0.0.1:8090`, or
`INTERVALS_ICU_FAKE_PORT`). It serves the four reads the client in
`integrations/intervals-icu` makes, for athlete `i1` and the key
`fake-intervals-icu-key` (both exported from `src/fixtures.ts`): the
athlete record, planned workouts (a ride with `joules` today, one without
in two days), activities (a ride yesterday), and wellness (body fat on two
of the last three days). A wrong or missing key is a 401, another athlete
a 403, and a list read without `oldest` and `newest` a 400.

| Control route | What it does |
| --- | --- |
| `PUT /_fake/events/{id}` | Adds or replaces a planned workout (`start_date_local` required; `category` defaults to `WORKOUT`). |
| `DELETE /_fake/events/{id}` | Removes one. |
| `PUT /_fake/activities/{id}` | Adds or replaces a completed activity (`start_date_local` required). |
| `DELETE /_fake/activities/{id}` | Removes one. |
| `PUT /_fake/wellness/{date}` | Merges fields (`bodyFat`, `weight`) into that day's record. |
| `PUT /_fake/fault` | `{"status": 429, "retryAfter": 30, "times": 1}` or `{"status": 401}`: every read gets that answer, `times` reads in a row or until cleared. |
| `DELETE /_fake/fault` | Clears it. |
| `GET /_fake/requests` | Every read it answered: path, query, status, and whether the key was `good`, `wrong`, or `missing` (never the key). |
| `POST /_fake/reset` | Fixtures back, fault and log cleared. |
| `GET /_fake/health` | The compose healthcheck. |

**Open Food Facts** (`open-food-facts-fake`, `http://127.0.0.1:8091`, or
`OPEN_FOOD_FACTS_FAKE_PORT`). It serves the one read the client in
`integrations/open-food-facts` makes, `GET /api/v2/product/{barcode}`,
cut to the `fields` it asks for, with three products recorded from the
live API (barcodes exported from `src/fixtures.ts`): Nutella
`3017620422003` (per 100 g, no serving size), Coca-Cola `5449000000996`
(a drink: its serving is 330 ml, though Open Food Facts says `100g`), and
Marmite `50184453` (no fat value). Any other barcode is a 404 with
`status: 0`, as the live API answers it.

| Control route | What it does |
| --- | --- |
| `PUT /_fake/products/{barcode}` | Adds or replaces a product (the body is its `product` object). |
| `DELETE /_fake/products/{barcode}` | Removes one. |
| `PUT /_fake/fault` | `{"status": 503, "times": 1}`: every read gets that status (429, 500, or 503), `times` reads in a row or until cleared. |
| `DELETE /_fake/fault` | Clears it. |
| `GET /_fake/requests` | Every read it answered: barcode, `fields`, `User-Agent`, and status. |
| `POST /_fake/reset` | Fixtures back, fault and log cleared. |
| `GET /_fake/health` | The compose healthcheck. |

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
its devenv-free smoke tests, which are the self-host parity check, on
`compose.yml` alone. Then it adds `compose.fakes.yml`, installs Nix and
devenv, runs `devenv shell -- npm ci`, and runs every
scenario in `tools/harness/scenarios/` with `devenv shell -- harness ui
<name>`, all into one run folder. That run's `summary.md` is appended to
the job summary. When the job fails, `.harness/runs/` is uploaded as the
`harness-runs` artifact.

A scenario that reproduces an open bug goes in the step's `KNOWN_FAILING`
list. It still runs, and it must still fail. When it passes, the job fails
until the scenario is removed from the list, so a fix also turns its
reproduction into a regression test. The list is empty: `create-recipe`
left it when step 7 fixed the recipe 415.

To reproduce a CI failure locally: `harness up` (CI's stack runs the
fakes but not the debug overlay), then `harness ui <name>`. Or download the artifact
and start from its `summary.md`.
