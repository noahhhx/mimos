# Step 2 — Browser driving

**Status:** done (2026-10-01) · [Back to the plan](index.md)

## Goal

An agent can reproduce any web flow with one command and get a trace,
network log, console log, and screenshots — plus an interactive browser for
exploration before a scenario exists.

## Using it

```sh
devenv shell -- harness up                    # the stack must be up
devenv shell -- harness ui login              # smoke: sign in, see the profile
devenv shell -- harness ui create-recipe      # fails with the recipe 415 until step 7
devenv shell -- harness ui create-recipe --as test2 --headed
cat .harness/runs/latest/summary.md
```

| Command | What it does |
| --- | --- |
| `harness ui <scenario> [--headed] [--as <user>] [--run <r>]` | Runs `tools/harness/scenarios/<scenario>.spec.ts` in Chromium against the web app, signed in as `test` (default) or `test2`. Exit code 0 when every test passed, 1 otherwise. `harness ui` with no scenario lists them. |

What lands in the run folder, under `browser/<scenario>/` (`-2`, `-3`, …
when `--run` repeats a scenario in the same run):

```
browser/create-recipe/
├── output.log                 # the Playwright runner's output
├── report.json                # its JSON report
└── results/<test>/            # one folder per test
    ├── trace.zip              # devenv shell -- npx playwright show-trace <path>
    ├── network.har            # every request, bodies embedded
    ├── console.jsonl          # console messages and uncaught page errors
    ├── test-finished-1.png    # final screenshot (test-failed-1.png on failure)
    ├── video.webm             # on failure only
    └── error-context.md       # on failure: the page's accessibility tree
```

plus `logs/` for the run's time window, as for `harness api`. Everything is
redacted before it is written (see below).

`summary.md` gets, per test: the outcome, the failing `test.step`, the
error and its location, links to the evidence, **a table of every request
the page made to the API** (method, path, status, the `Content-Type` sent,
the one received), and **every failed request in full** — request headers,
request body, and response body (labelled "Problem details" for
`application/problem+json`). On failure, the API's log lines since the
scenario started follow.

### Writing a scenario

- One file per scenario, `tools/harness/scenarios/<name>.spec.ts`; the
  file name is the scenario name. Keep it to one test so the evidence stays
  in one folder.
- Import `test` and `expect` from `./fixtures.ts`, not from
  `@playwright/test`: that is what records the HAR and console log.
- Start from `loggedInPage` for anything behind sign-in — a page on `/app`,
  signed in through the real Keycloak form. `user` is the realm user.
- Wrap phases in `test.step(...)`; the summary names the step that failed.
- Use unique, timestamped names for anything a scenario creates, so reruns
  never collide; `harness reset` gives a clean database.
- Assert on what the user sees and fail with the page's own error text
  where there is one (see `create-recipe`); the HAR then says why.
- Scope `role=alert` queries to `main`: Next.js's route announcer is an
  alert too.

### Exploring with the MCP browser

`.mcp.json` at the repo root registers the nixpkgs Playwright MCP server
through devenv, so an agent session started in the repo gets a `playwright`
MCP server (after approving it once):

```json
{
  "mcpServers": {
    "playwright": {
      "command": "devenv",
      "args": ["shell", "--", "playwright-mcp", "--headless", "--isolated", "--output-dir", ".harness/mcp"]
    }
  }
}
```

Page snapshots and console logs it saves land in `.harness/mcp/`. The MCP
browser is for finding a flow; exploration is not evidence until it is
written down as a scenario.

## Design notes

Decisions made while building, beyond the plan:

- **Browsers and the version pin.** `devenv.nix` points
  `PLAYWRIGHT_BROWSERS_PATH` at `pkgs.playwright-driver.browsers` (the same
  set `playwright-mcp`'s wrapper uses, so one closure serves both).
  `@playwright/test` is pinned to exactly `pkgs.playwright-driver.version`;
  `enterTest` fails `devenv test` when they differ, and `harness ui` checks
  before running that the installed Playwright's Chromium revisions exist in
  that folder — with a fix in the message, instead of Playwright's advice to
  download browsers.
- **Evidence goes through a staging folder.** Playwright writes into a
  temporary folder; the harness copies it into the run folder redacted by
  file kind, rewriting the staging paths in the runner's output and report,
  then deletes the staging folder. Nothing unredacted is ever written under
  `.harness/runs/`.
- **Redaction.** HARs and the network records inside `trace.zip` are
  redacted structurally (`redactHar`): secret headers and form fields by
  name, every cookie value, and bodies by media type — so the Keycloak
  token response loses its tokens and the login post its password. Other
  text in the trace (snapshots, captured resources) loses JWTs and bearer
  credentials; images and video are copied as they are. `trace.zip` is
  rewritten with a small built-in zip reader/writer (`src/zip.ts`) rather
  than a dependency. Known gap: the trace's `fill` action for the password
  field still shows the test user's password — local, committed in
  `src/config.ts` and the realm export, not a secret.
- **A failure is a status ≥ 400 or no response.** Redirects are the OIDC
  login itself, and Next.js aborts router prefetches as a matter of course
  (`net::ERR_ABORTED` on a 200), so neither is reported as a failure.
- **The `Content-Type` sent is read from the request headers**, never
  from the HAR's `postData.mimeType`: Playwright fills that in as
  `application/octet-stream` when the browser sent none, which is exactly
  the kind of detail a header bug hides behind. The API call table says
  **no Content-Type** instead.
- **Bodies the browser does not expose.** Chromium does not always hand a
  request body to Playwright (the recipe 415's body is one); the HAR then
  has an empty body but a `bodySize`, and the summary says how many bytes
  were sent but not captured rather than showing an empty body.
- **Config through the environment.** `playwright.config.ts` reads
  `HARNESS_OUTPUT_DIR`, `HARNESS_SCENARIO` (exactly one file), and
  `HARNESS_USER`; run directly (`npx playwright test` in `tools/harness`),
  it runs every scenario and writes to `.harness/playwright/` — unredacted,
  and outside the run folders, so prefer `harness ui`. No retries,
  one worker: a flaky failure is evidence too.

## Changes

- `devenv.nix` — Playwright env vars, `playwright-mcp`, pin check in
  `enterTest`.
- `tools/harness/` — `@playwright/test` devDependency (exact pin);
  `playwright.config.ts`; `scenarios/fixtures.ts` (`loggedInPage`, HAR and
  console capture), `scenarios/login.spec.ts`,
  `scenarios/create-recipe.spec.ts`; `src/commands/ui.ts`; `src/har.ts`
  (HAR summarizer), `src/playwright.ts` (runner, browser check, report
  parsing), `src/evidence.ts` (redacted copying), `src/zip.ts`,
  `redactHar` in `src/redact.ts`; tests for each.
- `.mcp.json`.
- AGENTS.md — Agent harness bullet, verification commands, repo layout.

## Tests

`npm test -w @mimos/harness` (no browsers or Docker needed): zip reading
(including a zip written by another tool) and round-tripping, trace and HAR
redaction, redacted copying, the HAR summarizer (modelled on the real 415:
no `Content-Type`, body not exposed, aborted prefetches, unreachable
requests, problem-details bodies), JSON-report parsing, the browser-folder
check, and `ui` argument parsing. The scenarios themselves run against the
stack (below); CI runs them from step 6.

## Verification (2026-10-01)

- After `harness reset && harness up` (all images rebuilt), `harness ui
  login` → passed.
- `harness ui create-recipe` → failed at "submit and land on the recipe";
  `summary.md` shows `POST /api/v1/recipes` → 415 with its request headers
  (no `Content-Type`), the 315 bytes the browser did not expose, the
  response body, and the API's `HttpMediaTypeNotSupportedException` log
  line. Recorded in [step 7](step-7-recipe-415.md).
- The redacted `trace.zip` opens in `playwright show-trace` (actions,
  steps, snapshots, network). No JWT anywhere under `.harness/runs/`,
  trace contents included.
- `.mcp.json`'s command, driven over stdio: the server initializes,
  `browser_navigate` to `http://localhost:3000/recipes` returns a page
  snapshot, and stdout carries nothing but JSON-RPC (the devenv banner goes
  to stderr).
