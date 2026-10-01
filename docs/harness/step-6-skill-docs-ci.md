# Step 6 — Agent skill, docs, and CI

**Status:** done (2026-10-01) · [Back to the plan](index.md)

## Goal

Any agent working in the repo knows the harness exists and follows the
same investigation loop; scenarios guard against regressions in CI.

## Using it

- **Agents** learn the loop from AGENTS.md's "Debugging the running
  stack" section. In Claude Code, the `mimos-harness` skill triggers on
  "run the app", "reproduce", "debug", or "why does X fail locally" and
  points there.
- **Everyone** gets the command reference in [Using the harness](usage.md).
- **CI** runs every scenario after the compose smoke tests. A failing
  scenario fails the PR, and the run folder is attached as the
  `harness-runs` artifact ([details](usage.md#in-ci)).

## Design

### Investigation loop (the contract for agents)

1. `harness up --debug` (fresh images, debug overlay).
2. **Reproduce** — a scenario (`harness ui`) or API call (`harness api`).
   No reproduction, no fix.
3. **Read the evidence** — `summary.md` first, then HAR, logs by request
   ID, `diag`.
4. **Hypothesize from evidence**; narrow with `loglevel` or
   `debug break`.
5. **Fix** at the right layer, with a test at that layer.
6. **Re-run the scenario**; it must pass.
7. **Keep the scenario** as a regression test.

### Where agents learn it

- **AGENTS.md** — "Debugging the running stack": the loop, the entry point
  (`devenv shell -- harness …`), and which command serves each step.
  AGENTS.md is tool-agnostic, so it is the source of truth. The command
  details that had collected in the Conventions bullet moved there. The
  bullet keeps only the rules for building the harness: the Playwright pin,
  the debug overlay, loopback ports, and `KNOWN_FAILING`.
- **`.claude/skills/mimos-harness/SKILL.md`** — a thin Claude Code skill.
  It names AGENTS.md and `usage.md` as the source of truth, repeats the
  loop's rules in brief, and has a "Launching the app" section. The
  built-in `run` skill looks for a project skill that covers launching the
  app, and finds `harness up` there.

### Docs

[Using the harness](usage.md) is a sibling page to the plan, with every
command, the run folder, and CI. The plan and the step pages keep the
design and the decisions made while building. `mkdocs build --strict`
passes.

### CI

The existing `compose` job runs these steps after its smoke tests:

1. `cachix/install-nix-action`, `cachix/cachix-action` with the `devenv`
   cache, then `nix profile install nixpkgs#devenv`, as devenv documents
   for GitHub Actions.
2. `devenv shell -- npm ci`, which installs the pinned `@playwright/test`
   with devenv's Node.
3. Every `tools/harness/scenarios/*.spec.ts` through `devenv shell --
   harness ui <name>`, into one run folder (`--run latest` after the
   first). The run's `summary.md` is appended to the job summary.
4. On failure, `actions/upload-artifact` uploads `.harness/runs/` as
   `harness-runs`.

The smoke steps before them stay devenv-free, because they are the
product's self-host parity check. The job's timeout went from 30 to 45
minutes for devenv's cold start.

## Design notes

Decisions made while building, beyond the plan:

- **Known failures, not skipped scenarios.** `create-recipe` reproduces
  the recipe 415, which step 7 fixes. Running every scenario as-is would
  fail every PR until then. Leaving the scenario out would lose the
  reproduction, and with it the signal that the fix worked. Instead, the
  workflow's `KNOWN_FAILING` list (space-separated scenario names) marks
  scenarios that must still fail. One that fails is a warning annotation.
  One that passes fails the job until it is taken off the list, so fixing
  the bug also turns its scenario into a regression test. Playwright's own
  `test.fail()` was rejected: `harness ui` would then report the
  reproduction as passing, and the bug would vanish from its summary.
- **One run folder per job.** The first scenario creates the run, and
  the rest append with `--run latest`. So `summary.md` covers every
  scenario, and it is also the job summary on the Actions page. The upload
  leaves out the `latest` symlink, which would otherwise upload the run a
  second time. It also needs `include-hidden-files`, because `.harness/`
  is a dot-directory, and `upload-artifact@v4` drops hidden paths by
  default.
- **No cache beyond the devenv cachix.** Nixpkgs paths (JDK, Node,
  Chromium) come from `cache.nixos.org`. Add a project binary cache if
  cold starts become a problem.

## Changes

- `AGENTS.md` — "Debugging the running stack" section; Agent harness
  bullet trimmed to rules; `.claude/skills/` in the repo layout; CI note
  in the verification commands.
- `.claude/skills/mimos-harness/SKILL.md`.
- `docs/harness/usage.md`, and its entry in the `mkdocs.yml` nav; this
  page, the plan's status, step 7's "Done when".
- `.github/workflows/ci.yml` — Nix, devenv, `npm ci`, the scenario loop
  with `KNOWN_FAILING`, the artifact upload, and the job timeout.

## Tests

The scenario loop is the workflow's own script. It was run locally,
extracted from `ci.yml`, against the running stack (below). The
workflow's devenv installation runs only in CI. No harness code changed,
so `npm test -w @mimos/harness` is unaffected.

## Verification (2026-10-01)

- The "Harness scenarios" script, run locally against a healthy stack
  with `KNOWN_FAILING=create-recipe`: `create-recipe` failed with the 415
  (a warning annotation), `login` passed, both landed in one run folder,
  its `summary.md` was appended to `$GITHUB_STEP_SUMMARY`, and the exit
  code was 0.
- With `KNOWN_FAILING=login`, the passing `login` scenario and the
  failing, now unlisted, `create-recipe` both reported errors, and the
  exit code was 1.
- `mkdocs build --strict` passes.

## Done when

- A fresh agent session, asked to investigate a local failure, uses the
  harness without being told to: AGENTS.md (always loaded) and the skill
  both send it there.
- CI runs the scenarios, and a failing scenario fails the PR with the run
  folder attached. This is confirmed on the first CI run of this change.
