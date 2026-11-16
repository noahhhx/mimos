# 05. Emulator harness, Maestro, and CI flows

**Depends on:** 03, 04

Agents and CI can boot a virtual Android device, install the app, drive it
against the compose stack, and read the evidence. Every later item lands
with a flow.

## Build

- `tools/harness`: `harness mobile` subcommands, documented in
  `docs/harness/usage.md` and a page in `docs/harness/`:
  - `mobile up`: start a headless emulator (`-no-window -no-audio`,
    KVM, a fixed AVD created from devenv's system image), wait for boot,
    build and install the debug APK.
  - `mobile flow <name>`: run `apps/mobile/flows/<name>.yaml` with
    Maestro, against the compose stack (see below for how the device
    reaches it).
  - `mobile shot`, `mobile logs`, `mobile down`.
  - Evidence in `.harness/runs/<run>/mobile/`: Maestro's report and
    screenshots, logcat for the app's process, and the request IDs the app
    sent, so `harness logs --request-id` finds the API's side.
    `summary.md` lists the failing step first, as for `ui`.
- Keycloak from the emulator: the stack's issuer is
  `http://localhost:8081`, which the emulator cannot reach as
  `localhost`. Use `adb reverse` for 8080 and 8081 so the device's
  `localhost` is the host's; then the app can use `http://localhost:8080`
  and tokens carry the issuer the API expects. Record this in
  `docs/harness/`.
- A browser on the AVD: sign-in runs in the device's browser, and system
  images without Google APIs may ship none. Install a pinned free browser
  APK (fetched by Nix with a fixed hash, not at run time) when the AVD is
  created, and check AppAuth's sign-in works with it.
- Flows in `apps/mobile/flows/`: `connect-and-sign-in` (type the
  instance, sign in as `test` in the browser, land on Kitchen) as a
  subflow every other flow reuses; `stay-signed-in` (restart the app).
- `.mcp.json`: Maestro's MCP server (`maestro mcp` through devenv) for
  exploratory driving, beside Playwright's.
- CI: an `android-flows` step in the compose job (or its own job with the
  compose stack and fakes) booting the emulator with KVM and running every
  flow, uploading `.harness/runs/` on failure. A `KNOWN_FAILING` list as
  for web scenarios.
- Version skew: a CI step (main and tags, not every PR) running the flows
  against the last release's published images.
- A parity lever for item 16: a script that lists the API operations the
  web's code calls and the ones the app's code calls, and prints the
  difference. Run it in this item to get the starting list.

## Checks

- `devenv shell -- harness mobile up && devenv shell -- harness mobile flow
  connect-and-sign-in` passes locally, and fails with a readable summary
  when the stack is down.
- `npm run typecheck -w @mimos/harness` and `npm test -w @mimos/harness`.
- CI runs the flows green. Record the job's wall time here; if it adds more
  than about 10 minutes per PR, take the decision in the index to the
  owner.
