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

## Built

What the item built. The local, emulator, and CI checks are recorded
under "Verified".

- `harness mobile flow <name>` (`tools/harness/src/commands/mobile.ts`)
  runs `apps/mobile/flows/<name>.yaml`. It builds and installs nothing.
  Its steps: emulator, installed app, reverse rules, stack probes from the
  device, app and Cromite data cleared, a fresh account, Maestro. Maestro's
  report and logs land in `mobile/flows/<name>/` after redaction, with the
  account's password replaced by value; the console output is captured, not
  streamed. `summary.md` leads with the failed prerequisite or the JUnit
  assertion, then lists each request the app sent. A passing flow with no
  request IDs fails.
- The debug app's OkHttp interceptor (`RequestLog.kt`, installed only
  when the build is debuggable) logs `MimosRequest: requestId=… method=…
  path=…`. The harness reads those lines from logcat.
- Flows: `connect-and-sign-in.yaml` (also run directly) and
  `stay-signed-in.yaml`, which runs it as a subflow, restarts the app
  without clearing state, and asserts the same account.
  `connect-and-sign-in` checks the account in the profile sheet, then
  closes the sheet, so a flow that reuses it starts in the shell.
- `.mcp.json` registers `maestro mcp` beside Playwright.
- CI: the `android-flows` job runs beside `android` and `compose`, not
  after them, caches the Nix store (saved on `main` only), and gates
  `publish`.
- `node tools/harness/src/api-parity.ts` lists the contract's operations
  (from its path methods) by who calls them. A file counts only when it
  uses the generated client (`@mimos/api-client` or
  `io.github.noahhhx.mimos.api.apis`) and calls the operation, so
  Android's `Cipher.getInstance` and `KeyStore.getInstance` are not the
  `getInstance` operation.

### Starting parity (run twice, identical output)

```text
API operations: 56. Web calls 50, the app calls 2.

Web only (49)
  addMealPlanEntry
  addMyTraining
  addRecipePicture
  addShoppingListItem
  connectMyIntervalsIcu
  createHouseholdInvite
  createIngredient
  createMealLog
  createRecipe
  deleteMealLog
  deleteMealPlanEntry
  deleteMyTraining
  deleteRecipe
  deleteRecipePicture
  disconnectMyIntervalsIcu
  estimateRecipeNutrition
  exportAccount
  generateShoppingList
  getHousehold
  getMealPlan
  getMyFuel
  getMyFuelProfile
  getMyIntervalsIcu
  getPlanSuggestions
  getPublicRecipe
  getRecipe
  getRecipePicture
  getShoppingList
  getWeekPanels
  importAccount
  joinHousehold
  leaveHousehold
  listIngredients
  listLibraryRecipes
  listMealLogs
  listMyPlugins
  listMyRecipes
  listPublicRecipes
  lookUpFoodProduct
  pressWeekPanelAction
  previewHouseholdInvite
  removeShoppingListItem
  replaceRecipe
  summarizeMealLogs
  syncMyIntervalsIcu
  updateMealPlanEntry
  updateMyFuelProfile
  updateMyPlugin
  updateShoppingListItem

App only (1)
  getInstance

Both (1)
  getMe

Neither (5)
  deleteIngredient
  getMyPlannedTotals
  getMyTrainingSession
  listMyTraining
  replaceIngredient
```

The web calls 50 of the 56 operations and the app 2 (`getMe`,
`getInstance`), so 49 are web only. Item 17 starts from this list.

### Verified

On the local emulator (API 36, `mimos-pixel`) against the compose stack
with the fakes:

- `harness mobile flow connect-and-sign-in` and `stay-signed-in` pass in
  one run. The first lists 2 app requests (`getInstance`, `getMe`), and the
  second lists 3, so no IDs carry over from the flow before it.
- `harness logs --request-id <id>` with an ID from `summary.md` finds the
  API's `GET /api/v1/public/instance -> 200` line.
- No file in the run contains the account's password.
- With the web, API, and Keycloak containers stopped, the flow exits 1, and
  its section opens with `Failed at: Reach the stack from the device` and the
  adb diagnostics.
- After `adb kill-server`, the flow re-makes the reverse rules and passes.
- A flow with an assertion on absent text opens its section with Maestro's
  `Assertion is false: ...` and keeps the failure screenshot.

In CI, on PR #2 (run 44379730247), the `android-flows` job passed on its
first run:

- The job took 12 min 47 s. Nix, devenv, and the npm install took
  33 s from the cache, booting the stack and the emulator 7 min 5 s, and
  both flows 2 min 21 s.
- It runs beside the other jobs, so it costs a PR its lead over the
  slowest of them, `compose` at 8 min 38 s: about 4 minutes. That is under
  the 10-minute bar, so flows stay on every PR.

### To watch

- The Nix cache entry. Saves happen on `main` only, capped at 8 GB, with
  no purge, and GitHub keeps 10 GB of cache per repository. Check its size
  with `gh cache list` after the first run on `main`.
- The parity scanner's blind spots, for item 17. It does not see a Kotlin
  call inside a scope function (`with(api) { getMe() }`), a web call inside
  a template string, or a web file that reaches the client through a module
  that re-exports it. None of these exist in the code today.
