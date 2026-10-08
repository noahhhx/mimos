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
