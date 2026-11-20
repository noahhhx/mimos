# 17. Parity audit and user guide

**Depends on:** 07–16

Proof that the app does what the web does, and the pages that tell people
how to get it.

## Build

- Run item 06's parity script: every API operation the web calls is called
  by the app, or listed here with the reason. Walk the web's routes against
  the app's screens the same way. Known exclusions: the signed-out public
  library pages (`/recipes`), which serve search engines.
- User guide: `docs/guide/android.md` (install the APK, check its
  SHA-256, connect with the web address, update with Obtainium), with
  emulator screenshots at a phone size, light theme. Link it from the
  web's home page and the docs nav.
- `docs/self-hosting.md`: the `mimos-mobile` client for existing
  instances (from item 04), and what the app needs from the instance's URLs.
- NORTHSTAR, ROADMAP, and AGENTS.md's Mobile row say the app shipped.

## Checks

- The parity script's difference is empty or fully explained.
- `devenv shell -- mkdocs build --strict`.
- Every flow green in CI.

## Result

**API operations.** `node tools/harness/src/api-parity.ts --check` on
e27b3dc plus this item: 56 operations, the web calls 50 and the app 49.

- Web only: `getPublicRecipe` and `listPublicRecipes`, the signed-out
  library pages, which serve search engines. They are the script's
  `WEB_ONLY_BY_DESIGN`, so a new web-only call fails the harness's tests
  in CI until it is built in the app or given a reason there.
- App only: `getInstance`, the version check when the app connects and
  opens an instance.
- Neither: `deleteIngredient`, `replaceIngredient`, `getMyPlannedTotals`
  (the plan and log read `getMyFuel`, which carries the same totals),
  `getMyTrainingSession`, and `listMyTraining` (training arrives in
  `getMyFuel`). Agents reach them over MCP.

**Pages.** Each web route under `apps/web/src/app`, against the app:

| Web | App |
| --- | --- |
| `/` | The connect screen, then Sign in or Create account |
| `/recipes`, `/recipes/[slug]` | None: the signed-out library pages, which serve search engines. Signed in, the Recipes tab lists the library |
| `/app` | Kitchen tab |
| `/app/recipes`, `/app/recipes/[id]`, `/app/recipes/new` | Recipes tab: the list, a recipe and its cook trail, and the form (New and Edit) |
| `/app/plan` | Plan tab |
| `/app/shopping-list` | Shopping tab |
| `/app/log` | Log tab |
| `/app/household` | Household, from the profile menu |
| `/app/join/[token]` | The app link `io.github.noahhhx.mimos://join`, or a pasted invite link on Household |
| `/app/plugins` | Plugins, from the profile menu |
| `/app/fuel` | Fuel, from the profile menu |
| `/app/account` | Your data, from the profile menu |
| `/app/callback` | AppAuth's redirect to `io.github.noahhhx.mimos.oauth:/oauth2redirect` |

The app adds Settings (theme, instance, versions, licences), which the web
keeps in its header's theme toggle and nowhere else.

**Docs.** `docs/guide/android-app.md` gained the connect and Kitchen
screenshots (1080 by 2400 from the API 36 emulator, light theme, shown
300 px wide) and a section on finding your way around. The web's home page
has an "On Android" card that links to it, and `docs/self-hosting.md`
says what the app reads from the web address and why those URLs must be
reachable from a phone. The item asked for `docs/guide/android.md`; the
page already existed from item 16 as `android-app.md`, linked from the
nav and the guide's index, so it kept that name.
