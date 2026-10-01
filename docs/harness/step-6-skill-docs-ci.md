# Step 6 — Agent skill, docs, and CI

**Status:** planned · [Back to the plan](index.md)

## Goal

Any agent working in the repo knows the harness exists and follows the
same investigation loop; scenarios guard against regressions in CI.

## Prerequisites

- Steps 1–5 (the skill describes what exists, not what is planned).
- Decided ([decisions](index.md#decisions)): scenarios run in CI, with
  browsers through devenv.

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

- **AGENTS.md** — a short "Debugging the running stack" section with the
  loop and the entry point (`devenv shell -- harness …`). AGENTS.md is
  tool-agnostic, so it is the source of truth.
- **`.claude/skills/mimos-harness/SKILL.md`** — a thin Claude Code skill
  that points at AGENTS.md and the docs, triggers on "run the app",
  "reproduce", "debug", "why does X fail locally". It also gives the
  built-in `run` skill a project-specific way to launch the app.

### Docs

The step pages turn from plan into reference as each step lands; once
step 6 is done, `docs/harness/index.md` gains a "Using the harness"
section (or a sibling page) with every command, and keeps the design
sections for context. `mkdocs build --strict` must pass.

### CI

In the existing `compose` job, after the stack is healthy, install Nix
(`cachix/install-nix-action`) and devenv, run every scenario with
`devenv shell -- harness ui <name>`, and upload `.harness/runs/` as a
workflow artifact on failure. Browsers come from devenv — the same
tooling agents use, with no second, separately version-matched way of
getting them. The job's existing smoke steps stay devenv-free (they are
the product's self-host parity check). Cold-start time is the known cost;
add a Nix binary cache if it becomes a problem.

## Changes

- AGENTS.md section; `.claude/skills/mimos-harness/SKILL.md`.
- `docs/harness/` usage section; `mkdocs.yml` nav.
- `.github/workflows/ci.yml` — devenv setup, scenario runs, artifact upload.

## Done when

- A fresh agent session, asked to investigate a local failure, uses the
  harness without being told to.
- CI runs the scenarios and a failing scenario fails the PR
  with the run folder attached.
