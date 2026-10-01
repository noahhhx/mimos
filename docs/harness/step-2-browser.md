# Step 2 — Browser driving

**Status:** planned · [Back to the plan](index.md)

## Goal

An agent can reproduce any web flow with one command and get a trace,
network log, console log, and screenshots — plus an interactive browser for
exploration before a scenario exists.

## Prerequisites

- Step 1.
- Decided ([decisions](index.md#decisions)): `@playwright/test` pinned to
  nixpkgs' `playwright-driver`; `.mcp.json` is committed.

## Scope

In: Playwright scenarios run through `harness ui`, a shared login fixture,
the first two scenarios, the Playwright MCP server registration.

Out: CI wiring (step 6), request-ID correlation in the summary (step 3).

## Design

### Browsers from Nix

Playwright's downloaded browsers do not run on NixOS, and AGENTS.md's agent
tooling rule says tools come from devenv anyway. `devenv.nix` provides them:

```nix
env.PLAYWRIGHT_BROWSERS_PATH = "${pkgs.playwright-driver.browsers}";
env.PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "1";
env.PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS = "true";
packages = [ pkgs.playwright-mcp ];
```

- The npm `@playwright/test` version **must equal**
  `pkgs.playwright-driver.version` (1.61.1 at the current `devenv.lock`),
  or the browser revisions will not match. Pin it exactly (no range), and
  add an `enterTest` check that compares the two. Bumping `devenv.lock`
  may require bumping the npm pin in the same change.
- Browsers run on the host (inside the devenv shell), so they see the same
  `localhost:3000/8080/8081` URLs that are inlined into the web build and
  that Keycloak issues tokens for — no container networking tricks.

### Scripted scenarios

- Scenarios are Playwright test files in `tools/harness/scenarios/`
  (`<name>.spec.ts`); `harness ui <name>` runs exactly one, with:
  - `trace: "on"`, `screenshot: "on"`, video retained on failure;
  - a HAR of the browser context (`recordHar`, full content);
  - a console/page-error listener writing `browser/console.jsonl`;
  - output directed into the run folder.
- After the run, the harness parses the HAR and writes into `summary.md`:
  pass/fail, the failing step, and **every non-2xx response with its
  request method, URL, request headers (redacted), request body, and
  response body**. For a bug like the 415, that table is the evidence.
- Service logs for the run's time window are captured automatically (as in
  step 1).
- `--headed` for a human watching; `--as <user>` picks the realm user.

### Login fixture

- oidc-client-ts keeps tokens in `sessionStorage`, which Playwright's
  `storageState` does not persist — so the fixture logs in through the real
  Keycloak form each time (fast on a local stack, and it exercises the real
  auth path).
- Exposed as a `loggedInPage` fixture; scenarios start from an
  authenticated app shell.

### First scenarios

- `login` — log in, land on the app shell, see the profile. Smoke for the
  fixture itself.
- `create-recipe` — fill and submit the new-recipe form, expect to land on
  the recipe page. Expected to **fail** today with the 415; it stays
  failing until [step 7](step-7-recipe-415.md) fixes the bug.
- Scenarios use unique, timestamped names so reruns never collide; a clean
  database is `harness reset` away.

### Exploratory browsing (MCP)

`.mcp.json` at the repo root registers the nixpkgs Playwright MCP server
through devenv:

```json
{
  "mcpServers": {
    "playwright": {
      "command": "devenv",
      "args": ["shell", "--", "playwright-mcp", "--headless", "--isolated",
               "--output-dir", ".harness/mcp"]
    }
  }
}
```

An agent explores with it, then writes what it found down as a scenario —
exploration is not evidence until it is reproducible.

## Changes

- `devenv.nix` — Playwright env vars, `playwright-mcp`, version check.
- `tools/harness/` — `@playwright/test` devDependency (exact pin),
  `playwright.config.ts`, fixtures, `scenarios/login.spec.ts`,
  `scenarios/create-recipe.spec.ts`, HAR summarizer (+ unit tests).
- `.mcp.json`.
- Docs: this page → usage notes; AGENTS.md verification commands.

## Done when

- `harness ui login` passes on a fresh stack.
- `harness ui create-recipe` fails and its `summary.md` shows the failing
  request with its headers and the problem-details body; `trace.zip` opens
  in `playwright show-trace`.
- An agent session can open the app through the MCP browser.
- HAR-summarizer unit tests pass.
