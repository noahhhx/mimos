# 16b. Flows against the last release

**Depends on:** 16, and a release whose images include `mimos-mobile`

Split from item 16, which had nothing to run this against: no release yet
ships the Keycloak client the app signs in as.

## Build

- A step on main runs the Android flows from main's app against the last
  `v*` release's published images (`deploy/selfhost`, as
  "Verification commands" in AGENTS.md describes booting it), so a server
  people still run is tested with the app they are about to get.
- Published Keycloak images carry no users. The harness creates the flows'
  accounts through the admin API, as `harness user create` already does.
- The job skips with a notice while the last release predates the
  `mimos-mobile` client.

## Checks

- The job runs green on main against the first release that has the client.
- Gates: harness.
