# 06. Maestro flows and CI

**Depends on:** 04, 05

Agents and CI drive the app end to end against the compose stack. Every
later item lands with a flow.

## Build

- `harness mobile flow <name>`: run `apps/mobile/flows/<name>.yaml` with
  Maestro on item 02's emulator, passing account values from `harness user
  create` as env. Maestro's report and screenshots go into the run's
  evidence, with the request IDs the app sent, so `harness logs
  --request-id` finds the API's side. The failing step comes first in
  `summary.md`.
- Flows: `connect-and-sign-in` (connect to the web address, sign in in
  the browser, land in the shell) as a subflow every other flow reuses, and
  `stay-signed-in` (restart the app).
- `.mcp.json`: Maestro's MCP server (`maestro mcp`, through devenv) for
  exploratory driving, beside Playwright's.
- CI: an `android-flows` job with the compose stack and fakes, as the web
  scenarios have, running every flow on an emulator through devenv. GitHub
  Linux runners need a udev rule to open `/dev/kvm`. The Android SDK is
  unfree, so it is not in the public Nix caches: cache the devenv store
  between runs. `KNOWN_FAILING` as for web scenarios; `.harness/runs/`
  uploaded on failure.
- A parity lever for item 17: a script that lists the API operations the
  web's code calls and the ones the app's code calls, and prints the
  difference. Run it here for the starting list.

## Checks

- `devenv shell -- harness mobile flow connect-and-sign-in` passes
  locally, and fails with a readable summary when the stack is down.
- `npm run typecheck -w @mimos/harness` and `npm test -w @mimos/harness`.
- CI runs the flows green. Record the job's wall time here; if it adds more
  than about 10 minutes per PR, take the decision in the index to the
  owner.
