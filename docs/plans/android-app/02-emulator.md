# 02. A virtual device for agents

**Depends on:** 01

Before any screen is worth testing, agents can boot an Android emulator on
this machine, install a build, reach the compose stack from it, and read
the evidence. Items 03 onward check their work on it.

## Build

- An AVD created by the harness from devenv's system image, with a fixed
  name and hardware profile, stored under `.harness/` (gitignored).
- A browser on the AVD. Sign-in runs in the device's browser, and the
  `default` image may ship none. A pinned free browser APK, fetched by Nix
  with a fixed hash (not at run time), installed when the AVD is created.
  Item 04 confirms sign-in works with it.
- Reaching the stack. The issuer in tokens is `http://localhost:8081`,
  which the device cannot reach as `localhost`. `adb reverse` for 8080,
  8081, and 3000 makes the device's `localhost` the host's, so the app and
  the browser use the same URLs as a desktop browser, and the API accepts
  the issuer. Record this in `docs/harness/`.
- `tools/harness`, documented in `docs/harness/usage.md` and its own
  page in `docs/harness/`:
  - `harness mobile up`: start the emulator headless (`-no-window
    -no-audio -gpu swiftshader_indirect`, KVM), wait for boot, set up the
    reverse ports, build and install the debug APK.
  - `harness mobile shot`, `mobile logs` (logcat for the app's process),
    `mobile down`.
  - Evidence in `.harness/runs/<run>/mobile/`, with `summary.md` as
    for `ui`.
- Account setup for flows, since a Maestro flow cannot do it itself:
  `harness user create` (a fresh Keycloak user through the admin API, as
  `ApiIntegrationTestSupport.createUser()` does) and `harness household
  join <owner> <member>` (an invite created and accepted through the API).
  Both print what a flow needs as env values.

## Checks

- `devenv shell -- harness mobile up` boots, installs item 01's APK, and
  `harness mobile shot` shows it running.
- From the device's browser, `http://localhost:3000` loads the web app.
- `harness user create` gives an account that can sign in on the web.
- `npm run typecheck -w @mimos/harness` and `npm test -w @mimos/harness`.
