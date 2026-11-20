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

## As built

- `android-skew` in `.github/workflows/ci.yml` runs on a push to main
  only, beside `android-flows`, and gates nothing: a red run says a
  server people still run breaks main's app.
- "The last release" is the last `v*` tag reachable from HEAD, as the
  contract check in `clients` finds it, so a candidate counts as soon as
  it is tagged on main.
- `node tools/harness/src/release.ts pick` decides, on Node alone, before
  the job installs Nix: it skips with a notice when there is no tag, when
  the tag's `deploy/keycloak/mimos-realm.json` has no `mimos-mobile`
  client, or when any of the tag's four images is not in the registry
  (`docker manifest inspect`, logged in with the job's token). The last
  case covers a push to main while the tag's own run is still publishing.
- It runs only the flows the tag already had. A flow added since may test
  something the release lacks, and it first runs against the next
  release, with no list to keep. A flow the tag had that main changed to
  test something newer goes in the step's `KNOWN_FAILING` until then.
- `harness release up [<tag>]` boots the release from that tag's own
  `deploy/selfhost/compose.yml`, the file a server runs, with this
  checkout's `compose.fakes.yml` over it, as compose project
  `mimos-release` on the dev stack's ports. Its `.env` sets localhost
  URLs, the admin login `harness user create` already uses, and a fresh
  `MIMOS_SECRET_KEY`, so intervals.icu is on. The fakes come from main
  because main's flows expect main's fixtures. Accounts are made through
  the admin API as before, so `harness mobile flow` needed no change.
  `harness release down` removes the stack and its volumes.
- Checked on this machine with a local tag `v0.0.1` at 70d4e51 (deleted
  after) and the dev images tagged as its release: `pick` skipped with
  "images are not published" against the real registry, `harness release
  up v0.0.1` came up healthy in production mode, and all 19 flows passed
  against it. The item's check, a green run on main against the first
  release with the client, waits for that release; until then the job
  skips with "No v* release yet".
