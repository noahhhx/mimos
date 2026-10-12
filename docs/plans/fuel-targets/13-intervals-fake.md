# 13. A fake intervals.icu for the stack

**Step:** 14.5 · **Depends on:** 12 · **Review:** new convention

Every later item checks itself against this fake, so it comes first.

## Build

- `tools/harness/fakes/intervals-icu/`: a zero-dependency `node:http`
  service run from source, as `plugins/country-week` is. It serves the four
  endpoints from item 12 from fixtures, accepts one good key for athlete
  `i1`, and has control routes so a scenario can change a workout, add an
  activity, set wellness body fat, force a 429 (with `Retry-After`) or a
  401, and read its request log. Unit tests on `node --test`.
- `deploy/docker/compose.fakes.yml`: runs the fake and points the API's
  `mimos.intervals-icu.base-url` at it. The scenario needs to reach the
  control routes: either publish them on `127.0.0.1` only, as the debug
  overlay does, or drive them through `docker compose exec`. Pick one and
  say why.
- `harness up --debug` and CI's compose job start the overlay;
  `compose.yml` alone still boots without it.
- AGENTS.md records the convention: fakes of third-party services live in
  `tools/harness/fakes/`, run in `compose.fakes.yml`, never in
  `compose.yml`. Update `docs/harness/usage.md`.

## Checks

- The fake's unit tests.
- `harness up --debug`; `harness diag` shows the fake healthy; every
  scenario still passes.
- `POSTGRES_PASSWORD=x KEYCLOAK_ADMIN_PASSWORD=x docker compose -f
  deploy/selfhost/compose.yml --env-file deploy/selfhost/.env.example
  config -q` still resolves (self-host is untouched).

## Review

`compose.fakes.yml` is a new convention. Ask the owner whether it belongs
there or in `compose.debug.yml` (which CI does not boot, so the
intervals.icu scenario would not run in CI). Land on `main`; set row 13 to
`done`.

## Built

- The fake is part of the `@mimos/harness` package (`tools/harness/fakes/intervals-icu/src`),
  not a workspace of its own: the harness gate typechecks and tests it, and
  no Dockerfile has to copy a new workspace manifest. Its image copies only
  `src`, as `plugins/country-week` does.
- Control routes are reached on `127.0.0.1:8090` (`INTERVALS_ICU_FAKE_PORT`),
  not through `docker compose exec`. Scenarios are Node code on the host that
  already call the API over HTTP; `exec` would start a docker process per
  call and need an HTTP client in the image. Loopback-only matches how
  `compose.debug.yml` publishes its ports.
- `harness up` adds `compose.fakes.yml` with or without `--debug`, so a plain
  `harness up` is the stack CI's scenarios run against.
- CI adds the overlay after the devenv-free smoke tests, so those still run
  on `compose.yml` alone, then restarts the API with the fake's base URL
  before the scenarios.
- `tools/harness/test/compose.test.ts` fails if `compose.yml` ever builds a
  fake.

Checks at the working tree: harness typecheck and 95 tests (13 for the fake);
`harness up --debug` healthy with `intervals-icu-fake` healthy and the API's
`mimos.intervals-icu.base-url` bound to it; `harness diag` no problems; all
21 scenarios pass; `compose.yml` alone boots healthy with the default base
URL; the self-host compose file resolves.

Owner chose `compose.fakes.yml` over `compose.debug.yml` on 2026-10-12. Landed at 5f14fb1.
