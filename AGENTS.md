# AGENTS.md

Read `NORTHSTAR.md` before making product decisions; read this before making
technical ones. This file is the source of truth for how Mimos is built.
**When a decision changes or a new one is made, update this file in the same
change** — an out-of-date AGENTS.md is worse than none.

## Prime directives (non-negotiable)

1. **Self-hosted parity.** `deploy/docker` must bring up the entire product
   (backend, frontend, auth, database) with no code changes. Any change that
   only works on a particular cloud provider is rejected. CI runs the
   self-hosted path on every PR.
2. **No cloud provider dependency.** Mimos relies on no provider's
   services, AWS included; a hosted instance runs the same published images
   through `deploy/selfhost` as a home server does. Use only standard,
   self-hostable pieces: containers, Postgres, an S3-compatible API,
   standard OIDC. An operator may point Mimos at a managed service that
   speaks one of those interfaces; nothing in the repo may require one or
   call a provider's own API. See ADR-0031.
3. **Plugins are a product surface.** Every significant design decision gets
   judged against: "could a third party build the country-picker plugin
   without modifying core?" (see below).
4. **Modular monolith by default.** We are open to microservices when a
   concrete need appears (independent scaling, plugin isolation, team
   boundaries). "It feels more scalable" is not a concrete need. Start with
   one deployable, strong internal module boundaries, and honest seams so
   extraction is possible later.
5. **Small, reviewable changes.** Agents work in increments: one feature or
   decision per change, tests included, docs updated. Ask before introducing
   any new framework, language, or top-level dependency.

## Decided — do not relitigate without asking the owner

| Area       | Decision                                                        |
| ---------- | --------------------------------------------------------------- |
| Backend    | Java 21 LTS, Spring Boot 4.x                                    |
| Frontend   | Next.js (App Router) with TypeScript. See "Frontend specifics". |
| Auth       | Keycloak via OIDC. Never build local/session auth. All users, roles, and tokens come from Keycloak. |
| Database   | PostgreSQL (latest stable). Schema changes via forward-only migrations (Flyway). |
| Repo       | Monorepo — all apps, plugins, libraries, and deployment in one repo. |
| Build      | Maven multi-module for the Java side.                           |
| Backend layout | Root parent POM + domain modules under `core/` + `apps/api` deployable. Base package `io.github.noahhhx.mimos`. See ADR-0001. |
| API style  | HTTP JSON APIs, contract-first: the OpenAPI spec in `contracts/api/openapi.yaml` is the source of truth; server stubs generate at build time and the TypeScript client in `libraries/api-client` is committed; the Android app's Kotlin client generates at build time too and is not committed (ADR-0029). `/api/v1` changes are additive only from the first app release: CI's `clients` job diffs the spec against the last `v*` tag's (`tools/harness/src/contract-additive.ts`) and fails on a removed operation, parameter, response status code, property, enum value, or `oneOf`/`anyOf`/`allOf` member, a property dropped from `required`, or a changed `type` (parameters and members are matched by name and `in` or by `$ref`, never by position). It does not catch every break: a `$ref` swapped to a different schema, or a new required request field or parameter, pass it, so reviewers still watch for those. See ADR-0003. |
| Config     | 12-factor: environment variables + Spring profiles. `local` profile is the default and must always work. |
| Runtime    | Docker. Every deployable (API, web, plugins) ships a Dockerfile that is built in CI; no bare-metal assumptions in app code. |
| Docs       | MkDocs with Material, moving to Zensical (`theme.variant: classic`, same `mkdocs.yml`) before Material's end of life on 2027-05-05 (ADR-0033); `mkdocs.yml` at repo root, source in `docs/`. The published site is the user guide only (using the app, self-hosting, writing plugins); contributor docs (`docs/design/`, `docs/harness/`, `docs/decisions/`, `docs/plans/`) stay in the repo and are kept off the site by `exclude_docs`. ADRs live in `docs/decisions/`. A build that takes more than one session gets a folder in `docs/plans/`: an `index.md` that tracks its work items, and one file per item. CI publishes the site from `main` to GitHub Pages (`https://noahhhx.github.io/mimos/`, the `site_url`), and the web home page links to it. Styled as Evening Kitchen by `docs/assets/stylesheets/mimos.css` only: no template overrides or hooks (see "Documentation site" in `docs/design/index.md`). |
| Deploy     | Build-from-source and CI parity via `deploy/docker` (compose); servers run the published images via `deploy/selfhost` (compose, production-mode Keycloak, operator's TLS proxy); no provider-specific deployment: a hosted instance is `deploy/selfhost` on any servers (ADR-0031). No click-ops. |
| Images     | CI publishes `ghcr.io/noahhhx/mimos-{api,web,keycloak,country-week}` (amd64) after every other job passes: `main` + `sha-*` from main, semver + `latest` from `v*` tags. A tag's API image is built at the tag's version (`MIMOS_VERSION` build argument), so `getInstance` answers it. Images carry no deployment-specific config. See ADR-0012. |
| Training and fuel | Per-person daily fuel targets in `core/core-fueling`, off until a person turns them on. Calories are an energy-availability level times fat-free mass plus exercise energy (45 kcal/kg FFM to fuel training, 40 and 35 to lose weight, 50 to gain). Carbs follow published g/kg ranges by the day's training duration, protein is set in g/kg, and fat takes the remainder within 20–35% of calories. Targets are calculated when read, never stored. Training comes from intervals.icu (`integrations/intervals-icu`: a personal API key encrypted under `MIMOS_SECRET_KEY`, polled, feature-gated by `mimos.intervals-icu.enabled`; connected, refreshed, and disconnected in the Fuel page's intervals.icu section, which also offers its weight, sex, and FTP as suggestions for the profile) or is entered by hand. Shown as "Fuel for the day" with no scoring: on the plan page's ledger (ADR-0028), the Mine view (and a household of one) has a Training row and a Planned row of each day's calories against it, and a selected day's inspector shows its training and a table of it and Planned, all read in one `GET /api/v1/me/fuel` for the week; sessions are added and removed in that inspector, and every change reloads that read. On the Log page, the week's totals table puts it under each day's logged totals, from the same read. Plugins never see any of it. Built as ROADMAP step 14 (done). See ADR-0022. |
| Food products | Packaged food by barcode from Open Food Facts (open data, ODbL, no key): `GET /api/v1/food-products/{barcode}` (`lookUpFoodProduct`, an MCP tool), 8 to 14 digits or a 400, answers a name (`product_name`, else `generic_name`; none is a 404), the first brand, nutrition per 100 g or per 100 ml (each value absent when Open Food Facts lacks it), and a serving size in that unit when it has one. The API looks it up, never the browser, so Open Food Facts sees the server and the barcode only; `integrations/open-food-facts`, on by default and switched off by `mimos.open-food-facts.enabled` (a 404, and no call ever made). Nothing is cached or stored; a product is logged as an ad-hoc meal log. Open Food Facts not answering is a 502. The web shows a looked-up product as "From Open Food Facts". The Log page's "Scan a barcode" dialog (`ScanBarcode`, with the camera in `BarcodeCamera`) reads EAN and UPC codes from the rear camera in any browser with `getUserMedia` (a secure context): the browser's own `BarcodeDetector` when it reads a food format (`chooseDetector`), or else the `barcode-detector` ponyfill on `zxing-wasm` (Safari, Firefox), imported only when the dialog opens. It always offers a typed Barcode number (`apps/web/src/lib/barcode.ts`). A found product is picked into What and logged by amount like an ingredient. See ADR-0025. |
| Library content | Seed file `core/core-recipes/src/main/resources/library/library-seed.json`, loaded by an idempotent startup seeder (`mimos.library.seed-enabled`, default on). Content fixes ship with a restart; never via Flyway migrations. See ADR-0005. |
| Ingredient catalog | One shared catalog seeded read-only from `core/core-recipes/src/main/resources/library/ingredient-seed.json` (always on, by slug, never deleted), plus each household's own ingredients, private to it and editable (`/api/v1/ingredients`, ADR-0016, ADR-0019). Recipe lines link to either by `catalogSlug` and carry prep in an optional `note`; the form's ingredient name is a search over both and the household's recipes. A line may instead link one of its owner's own recipes (`recipeId`, never both, never itself or a cycle), counted in `servings` of it (ADR-0018); the shopping list buys a linked recipe's own ingredients, through any depth, never the recipe as a line. A recipe's `nutritionSource` is `MANUAL` (typed) or `INGREDIENTS` (calculated when read, so catalog fixes and edits to a linked recipe reach every recipe that uses them). Metric units only: `g`, `kg`, `ml`, `l`, `tsp` (5 ml), `tbsp` (15 ml), or none for pieces, plus `servings` or `recipe` (the whole recipe, all its servings) for a recipe link. Library recipes are calculated and link no recipes. The Log page's What search offers ingredients too: one logs as an ordinary ad-hoc entry (`Sugar, 15 g`, one serving) carrying the nutrition the browser calculated and previewed from the same units (`apps/web/src/lib/log-entry.ts`), a snapshot like every log, so no API change. See ADR-0015, ADR-0016, ADR-0018. |
| Recipe pictures | Up to 8 per personal recipe, at most 2 MB each, JPEG, PNG, or WebP read from the bytes (`RecipePictureService` in `core-recipes`, the one owner of picture rules; `MAX_PICTURES` and `MAX_PICTURE_BYTES` are the only source of the limits, and the API's `MultipartConfigElement` is built from them). A sub-resource, never part of `RecipeInput`: `POST /api/v1/recipes/{recipeId}/pictures` (multipart part `file`; over 2 MB is Spring's 413 problem), `GET` and `DELETE …/pictures/{pictureId}`; `RecipeDetail.pictures` lists ids in upload order (always empty for library recipes). The GET is cached `private, max-age=31536000, immutable` with `nosniff`: a picture never changes. Upload and GET are `x-mcp: false`. Bytes live in Postgres (`recipe_picture_data`) behind the `PictureStore` seam (put and get by picture id, no delete: a non-Postgres store owes an orphan sweep). The next store, built when a deployment needs it, speaks the S3 API to any server the operator names, with Garage as the reference CI tests against; Postgres stays the default (ADR-0032). The web fetches images through the API client as Blobs (`BlobImage` owns object URLs), and the recipe form shrinks each pick to 1600 px and re-encodes it as JPEG, stripping its metadata, before Save sends fields, then removals, then uploads (`apps/web/src/lib/recipe-pictures.ts`). See ADR-0023. |
| Planned foods | A plan entry plans exactly one of a recipe and a food (`PlannedItem`, sealed, in `core-planning`: `Recipe(recipeId)` or `Food(catalogSlug, unit)`). A food is an ingredient, shared or the household's own, by `catalogSlug`, in the units a recipe line takes for its basis (checked on write; quantity at most 10,000). The entry's one `amount` (`servings` in the API and the `servings` column) is servings of the recipe or the food's quantity in `food.unit`, for all the diners together; the share rule is the same. `CatalogIngredient.nutritionOf(quantity, unit)` is the one place an amount of an ingredient becomes nutrition. Planned totals, Fuel for the day, the shopping list (a line by name and unit), and the account export (`ExportedPlanEntry.food`, since version 7) all count foods; plugins see a food as a slot with no `recipeId`, contract unchanged. Deleting an own ingredient deletes the entries planning it (V20). Scanning a barcode while planning creates a household ingredient from the lookup and plans it; logging a planned food logs an ad-hoc meal with the share's amount. See ADR-0026. |
| Shopping list additions | A week's shopping list holds generated items (from its plan, rebuilt by `generateShoppingList`) and items added by hand (`shopping_list_item.added`; `added` in the API). `POST /api/v1/plans/{startDate}/shopping-list/items` adds one: a name, an optional quantity (more than 0, at most 10,000), and an optional free-text unit (at most 30 characters, only with a quantity), in the aisle its name suggests; a week with no list gets one generated from its plan first. Regeneration keeps added items, ticks included, and never merges them with a generated line or gives them its tick. Only added items can be removed (`DELETE …/items/{itemId}`; a generated one is a 400). Generating, restoring, and adding each lock the week's list row first (`ShoppingListRepository.lockOrCreateList`), and the row is updated in place, never deleted and reinserted. The Shopping list page's "Add an item" form (its Unit is a dropdown of the recipe form's metric units, pieces by default; the API itself takes free text, so agents may add "pack") and each added item's Remove button (`apps/web/src/lib/shopping.ts` checks the fields). See ADR-0027. |
| Households | Every user belongs to exactly one household; a user on their own is a household of one, created with their profile. The household (`household`, `user_profile.household_id`) owns recipes, personal ingredients, plans, shopping lists, and plugin opt-ins and pseudonyms, all keyed by `household_id`; meal logs stay per person. Membership is Mimos domain data, not Keycloak groups. Every member is equal: any member reads and edits the household's data and creates invites, anyone may leave, nobody removes anyone. Recipes record who added them (`created_by_profile_id`; `createdBy` in the API, "Added by" in the web). Every plan entry names one or more diners, all members of its household (`meal_plan_entry_diner`; `diners` in the API as `Person`s). A create request without diners gives a dinner every member and any other meal the caller alone; the entry patch changes servings, diners, or both; an empty list or a non-member is a 400. Both writes hold the household's membership for their transaction (`HouseholdService.holdMembership`: share locks on the caller's profile row, then the household row), so nobody joins or leaves until they commit. `servings` is the amount cooked, so the shopping list ignores diners, and plugins never see them. Logging a planned meal logs the caller's share, servings divided by diners (to 0.1, editable before saving), which each entry answers as `yourShare` while the caller is one of its diners (`PlannedMeal.shareOf`), so no client computes it; Log shows only on meals the caller eats. Each person's planned total for a day (`GET /api/v1/plans/{startDate}/my-totals`, `MealPlanService.plannedTotals`, ADR-0022) sums that same share of every meal they eat, all servings when they eat alone; a value the recipe lacks adds nothing while its known values count, as Log records them, a value no meal that day has is absent from the total rather than zero, and a meal whose recipe lacks any of the four is counted in `mealsWithoutNutrition`. The plan page shows it in the ledger's Planned row (calories) and a selected day's inspector (all four) in Mine, and always in a household of one, never in Everyone. The plan page toggles Mine (meals the caller eats) and Everyone, remembered per browser, and the Kitchen home shows only meals the caller eats; a household of one sees no chips, no toggle, and the old default of 2 servings, while a shared household starts a meal at one serving per default diner. Any member creates a single-use invite valid for seven days (`household_invite`, SHA-256 of the token only; the web link is `/app/join/{token}`); joining from a household of one brings its recipes and ingredients and deletes its plans, lists, plugin opt-ins and pseudonyms; joining from a shared household leaves it first; leaving keeps the household's data, removes the leaver as a diner (deleting entries left with none), and unlinks their logs from its recipes. A plugin is on or off for the whole household and sees one `subject` per household. The account export stays one person's (see "Account data"). API under `/api/v1/household`; join, leave, and the invite preview are `x-mcp: false`. Web: the Household page (`/app/household`, in the profile menu) and `/app/join/[token]`, which also offers "Open in the app": `io.github.noahhhx.mimos://join?instance=<web address>&token=<token>` (`appJoinLink`), since the app cannot claim the instance's links. See ADR-0019. |
| Sign-up    | Keycloak self-registration, reached with OIDC `prompt=create` through `AuthProvider.signUp`, which every Create account button calls (the home page, and `SignInPrompt` on every signed-out page; the invite page returns to the invite). `MIMOS_SIGNUP` (`open` by default; any other value is closed) decides whether the web offers it; the realm's registration switch enforces it, so closing sign-up sets both. Identity and access stay separate: a paid sign-up later is a new `SignupPolicy` value whose `signUp` adds a Mimos checkout, plus an API-side entitlement on the household, never a gate inside Keycloak. See ADR-0020. |
| Public API | Unauthenticated read-only access to the curated library under `/api/v1/public/**` (SEO pages), and `GET /api/v1/public/instance` (`getInstance`: the server's version from Maven's build-info, qualifier dropped, and `minAppVersion`, a constant in `PublicInstanceController` raised only by a change an older app cannot survive), which apps read when they connect. The web serves its public config as `/runtime-config.json` too, the JSON twin of `/runtime-config.js`. Personal recipes never appear there; everything else requires a bearer token. |
| Account data | Per-person export/import as one versioned JSON document (`GET /api/v1/account/export`, `POST /api/v1/account/import`): domain-shaped, never a table dump; the household's recipes (with their pictures as base64, imported through the upload's own checks), ingredients, and shopping lists, only the plan entries the exporter eats (weeks with none are left out), the exporter's own logs, and their fuel profile and hand-entered training sessions (synced sessions and the intervals.icu key never); library recipes by slug; recipe links by the linked recipe's id within the document, restored linked-first; import only into an empty household of one (no recipes, own ingredients, planned meals, list items, logs, fuel profile, or training sessions, and no other member; 409 otherwise), atomically, through domain validation, with the importer as every entry's only diner. Current format: version 8 (shopping-list items carry `added`). Format changes, including a change to what a section holds, bump the version, add an `ExportUpgrader` step and a frozen `export/v<N>.json` test fixture. See ADR-0011, ADR-0018, ADR-0019, ADR-0022, ADR-0023, ADR-0026, ADR-0027. |
| Plugins    | HTTP sidecar plugins, pull-only in v1: config registry (`mimos.plugins.*`), manifest at `GET /manifest`, two capabilities (`plan-suggestions` cards, `week-panel` declarative panels with buttons), library-only context, households named by a per-plugin pseudonym (`subject`) the plugin may keep its own data against. See ADR-0006, ADR-0017. Registered means available; each household opts in from the Plugins page (`/app/plugins`, linked from the profile menu; API `/api/v1/me/plugins`), and every plugin starts off. See ADR-0013. |
| Agent access | MCP server inside `mimos-api` at `/mcp` (Spring AI 2.0.x MCP server starter, stateless Streamable HTTP; Spring AI's model layer excluded). Tools are derived from `contracts/api/openapi.yaml` at startup: every `/api/v1` operation is a tool unless it declares `x-mcp: false`, and a call loops back through the HTTP API with the caller's token. Agents sign in as the public Keycloak client `mimos-agent` (Authorization Code + PKCE), found through Protected Resource Metadata (RFC 9728). See ADR-0014. |
| Agent harness | `tools/harness` (`@mimos/harness`, TypeScript on Node type stripping) behind a devenv `harness` script; all agent tooling from devenv/nixpkgs; Playwright pinned to nixpkgs' `playwright-driver`; `.mcp.json` committed; debug-only behavior in `deploy/docker/compose.debug.yml`; fakes of third-party services in `tools/harness/fakes/`, run by `deploy/docker/compose.fakes.yml`; scenarios run in CI via devenv; `harness mobile up|shot|flow|logs|down` runs a headless Android emulator (AVD `mimos-pixel` written by the harness under `.harness/android/`, the stack reached through `adb reverse` for 8080, 8081, and 3000 and proved from the device; `flow <name>` runs `apps/mobile/flows/<name>.yaml` with Maestro on the installed app, with a fresh account and cleared app and browser data, evidence in `mobile/flows/<name>/` redacted, the password by value, and the app's request IDs in the summary from the debug build's `MimosRequest` logcat lines), `node tools/harness/src/api-parity.ts` lists the API operations the web and the app each call, and `harness user create` and `harness household join` make accounts for flows, `harness release up [<tag>]` boots the last release's published images from that tag's `deploy/selfhost` with the fakes, as project `mimos-release`, for flows against it, a flow tagged `household` signs in as one of two members of a household, and a flow tagged `invitee` gets a second fresh account on its own (`MIMOS_INVITEE`), and a flow tagged `country-week` starts with Italy chosen for its account's next week and the plugin off. Plan and decisions in `docs/harness/`. |
| Mobile | A native Android app in `apps/mobile` with every page the web has (plan in `docs/plans/android-app/`; user guide `docs/guide/android-app.md`; `node tools/harness/src/api-parity.ts --check` fails on an operation the web calls and the app does not, unless `WEB_ONLY_BY_DESIGN` gives the reason): Kotlin Multiplatform with only the Android target, Compose Multiplatform UI, Ktor and kotlinx.serialization, Gradle in its own wrapper. Platform services behind `commonMain` interfaces so iOS stays possible; iOS itself is not built (no Mac). No Google Play Services or proprietary dependencies, enforced by a Gradle check (`checkNoPlayServices`, in `apps/mobile/build-logic`, on `check`). Connects to any instance from its web address (`/runtime-config.json`); signs in as the public Keycloak client `mimos-mobile` (PKCE, redirect scheme `io.github.noahhhx.mimos.oauth`). Sign-in is AppAuth-Android in the device's browser; the refresh token (and id token, for end-session) is sealed under a non-exportable Android Keystore AES-GCM key and kept in DataStore, excluded from backups, never an offline token, and the access token stays in memory (`SessionModel` in `commonMain` owns the session's states, behind `Authorizer` and `TokenStore` interfaces). HTTPS only in release builds, trusting user-installed CAs as browsers do (ADR-0030); a debuggable build also takes `http://localhost`, for the emulator's `adb reverse`, and `parseWebAddress` and `discover` take that as `allowLocalhostHttp` (set from `FLAG_DEBUGGABLE`), so an address the network policy would refuse is refused with its own message. An `apiUrl` in a server's runtime config that is `http://` is `Discovery.InsecureServer`. Its Kotlin client is generated at build time and not committed (openapi-generator's `kotlin` generator, `library=multiplatform`, pinned in `gradle/libs.versions.toml`, with two template overrides in `shared/openapi-templates/`); it reads tolerantly, ignoring unknown fields and reading an unknown enum value as `unknown_default_open_api`; `PanelBlock`'s decoder is the one hand-written model, with an `Unknown` case. It accepts `application/problem+json` as well as JSON, since an operation whose only answers with a body are problems (`deleteRecipe`) produces nothing else. A non-2xx answer throws `ApiProblem` (status, problem title and detail, request id). The app's version is the root `pom.xml`'s, or `-Pmimos.version` from a `v*` tag in CI (`mimosVersion` in `build-logic`), and its `versionCode` is `ReleaseVersion`'s (`major * 1_000_000 + minor * 10_000 + patch * 100`, plus a candidate's `-rc.N`, 99 for a release, 0 for a snapshot). The connect screen tells the person when the server or the app is too old; once connected, the app asks `getInstance` again each time it opens the instance, shows "A newer app is available" while the server is newer, and stops at "Update the app" below `minAppVersion`, both linking to the latest GitHub release and never calling GitHub. The look is Evening Kitchen (ADR-0008) on Compose foundation alone, with no Material: `MimosColors` is generated at build time from `globals.css`'s `:root` and dark blocks (`generateDesignTokens`, the `mimos.design-tokens` plugin in `build-logic`), and the build fails when a colour is missing, differs between themes, or is not `#rrggbb`, so the app cannot drift; the type and spacing scales (`ui/Typography.kt`, `ui/Spacing.kt`) are copies, and the two fonts are TTFs unpacked from the web's `woff2` (`apps/web/src/fonts/README.md`, "Mobile"), weighted through variation settings. Light by default, dark only when chosen in the app (`ThemeModel`, sent to Keycloak as `mimos_theme`), never the system's; straight edges, no ripples, no motion (dialogs and sheets are windows with their animation off, `Overlay`). The signed-in shell is a header, a bottom tab bar (Kitchen, Recipes, Plan, Shopping, Log), and a profile sheet. The Kitchen tab (item 13, `kitchen/`), the first screen after sign-in, follows the web's Kitchen home, its rules ported in `KitchenRules.kt` and tested from `libraries/test-cases/kitchen.json`: tonight's meal comes first on a phone, then Still to buy, a plugin's thought, and This week; each block loads and fails on its own, and the tab reads them again each time it opens. Its "Plan tonight" opens the plan's picker for today's dinner, and "Swap meal" the meal's inspector (`PlanModel.planTonight`, `showMeal`). Debug builds have a component gallery (`gallery` intent extra). From the first app release `/api/v1` changes are additive only, since installed apps and self-hosted servers drift apart. The Recipes tab (items 07 and 07b) keeps its place as a route (the cook trail, or the recipe form: `New` from the list, or `Edit` from a recipe, which back returns to; `RecipeFormModel` lives in `RecipesModel`), and ticks in an in-memory `CookBook` that lasts as long as the app's process and is cleared on sign-out; recipe pictures (item 07c) follow the web's rules in `recipes/pictures/` (picked with the system photo picker or the phone's camera app, never a storage or camera permission, and shrunk behind the `PictureShrinker` interface), and their image bytes are kept in memory by `RecipePictures` until sign-out; the Plan tab (item 08) is a list of days with full-screen inspectors and a picker (`plan/`; the tablet ledger is deferred), its rules ported in `PlanRules.kt` and tested from `libraries/test-cases/plan.json`; a new ingredient of the household's own is added through one panel in `ingredients/` (`NewIngredientPanel`, `NewIngredientDraft`), from a recipe-form line or from the picker, which plans it at once (item 08b), and item 11 reuses it; the Plugins screen (item 09, `plugins/`, from the profile menu) follows the web's Plugins page, each plugin a switch for the household that shows at once and goes back if the save fails, its homepage opened in the browser only when http(s); the Plan tab shows the week's plugin panels (`WeekPanelsModel`, loaded per week rather than per change, so a meal planned never resets a landed wheel) above the days, then the suggestion cards, which `PlanWeek` carries and every change reloads, applied through `addMealPlanEntry` one meal at a time; the wheel (`plan/Wheel.kt`, a Canvas) is the app's one animation, turning forward for three seconds with the web's easing, and lands at once when the system's animations are off (`animationsEnabled`, an `expect` read from `ValueAnimator.areAnimatorsEnabled()`); its geometry and which blocks show while it spins are ported in `plan/WheelGeometry.kt` and `plan/Panels.kt`, tested from `libraries/test-cases/wheel.json` and `panels.json`; `AppModel` owns the connected instance's `MimosHttp`, so a request outlives a rotation. The Shopping tab (item 10, `shopping/`) follows the web's Shopping list page, its aisle order and add-an-item rules ported in `ShoppingRules.kt` and tested from `libraries/test-cases/shopping.json`; a tick shows at once and is sent, one the server did not take stays ticked as "Not sent" with a Retry until it goes through (`ShoppingModel.ticks`, kept until sign-out, a household change, or an import), and the screen stays awake while it is open. The Log tab (item 11, `log/`) follows the web's Log page, its rules ported in `LogRules.kt` and tested from `libraries/test-cases/log-entry.json`; it reads its week again each time it opens, and its selected day keeps its weekday when the week moves. The plan's meal inspector logs a meal the caller eats: in one tap when they eat alone, or a shared one by the share the API answers, editable first (`foodLogBody` and `parseShare` in `PlanRules.kt`). Fuel (item 12, `fuel/`, from the profile menu) follows the web's Fuel page, its rules ported in `FuelRules.kt` and tested from `libraries/test-cases/fuel.json`; the intervals.icu key is typed or pasted into a hidden field, sent once, and held in memory only until then. The Plan tab reads each week's `GET /api/v1/me/fuel` (not `my-totals`): while targets are on, each day's header names its training (or Rest) and its planned calories against its target, and a day's inspector adds and removes training by hand and shows Fuel for the day above Planned; the Log tab's week table puts each day's target under its totals from the same read. A fuel profile saved, or intervals.icu connected, synced, or disconnected, reloads an opened plan (`PlanModel.changedElsewhere`, which a meal planned from the Kitchen home calls too). Barcodes (item 11b, `barcode/`): one scan sheet, a `ScanModel` that the Log tab and the plan's picker each own, looks a product up through `lookUpFoodProduct` from a typed number, always offered, or the rear camera (`BarcodeCamera`, an `expect`: CameraX with a TextureView preview, each frame read by zxing-cpp for EAN-8, EAN-13, UPC-A, and UPC-E); the `CAMERA` permission is asked when the camera first shows, so the recipe form's Take a photo asks for it too. The Log tab picks a found product into What at a serving; the picker makes it the household's own ingredient once (`productIngredient`, `existingIngredient`) and plans it at a serving, or opens the new-ingredient panel with what Open Food Facts knew. Its rules are tested from `libraries/test-cases/barcode.json`. Maestro's `launchApp` grants every permission, so flows always have the camera. The Household screen (item 14, `household/`, from the profile menu) follows the web's Household and join pages, shares an invite link through the Android share sheet, and joins from a pasted link or the web join page's app link (`io.github.noahhhx.mimos://join?instance=…&token=…`, `parseInviteLink`; the app-link scheme, apart from the sign-in one). `MainActivity` is `singleTask`, so a link opened from the browser reaches the running app's models through `onNewIntent` rather than a second activity. An invite before connecting fills in its web address on the connect screen and shows once someone is signed in; one for another instance than the connected one offers to switch (sign out, then connect there). After a join or a leave, the Recipes and Plan tabs start fresh. Your data (item 15, `account/`) saves the export through the Storage Access Framework (the system's create and open pickers, never a storage permission, behind `DocumentStore`) byte for byte as the server sent it, never decoded into the app's models and written back, which would drop what a newer server added; an import sends the picked file as a JSON object, and after one the Recipes and Plan tabs start fresh. Settings, the one profile-menu destination the web lacks, holds the theme choice, the connected instance with the version it reports and Change instance, and the app's version and licences, which `generateLicences` (the `mimos.licences` plugin in `build-logic`) writes at build time from the POMs of everything on the release classpath, failing the build for a dependency whose licence it cannot find. Rules the app repeats from the web (recipe sort and tag order, ingredient amounts and names, and the recipe form's input, ingredient links, and catalog matching, ported in `recipes/form/`) are tested from JSON cases in `libraries/test-cases/`, which the web's `node --test` suites and `commonTest` both read (`generateTestCases` in `apps/mobile/shared/build.gradle.kts` embeds them as Kotlin strings); title order is a Kotlin port of `Intl.Collator` base sensitivity (`recipes/Collation.kt`), because `java.text.Collator` ignores spaces and marks. Verified with `commonTest` and Maestro flows on an emulator from devenv (`harness mobile flow`); CI's `android` job builds it without devenv, and its `android-flows` job runs every flow on an emulator through devenv (every PR for now; may move to main and tags if too slow). On a push to main, `android-skew` runs main's app against the last `v*` release's published images (`harness release up`), only the flows that tag already had, and skips with a notice while that release lacks the `mimos-mobile` client or its images (`tools/harness/src/release.ts pick`). Released as a signed APK on GitHub Releases with the server's `v*` tag; no Play Store. Release builds are minified by R8. Signing keys come only from the environment (`MIMOS_RELEASE_KEYSTORE` and `MIMOS_DEBUG_KEYSTORE`, each with `_KEYSTORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`), which CI fills from Actions secrets: the `release-apk` job, after every other job, attaches `mimos-<version>.apk` and its `.sha256` to the tag's release (and builds the same as an artifact on `workflow_dispatch`), and main's debug APK is signed with a shared debug key. Key setup, release steps, and recovery are in `docs/plans/android-app/16-release.md`. See ADR-0029. |

## Open decisions — resolve with the owner before building against them

| Area                  | Status | Notes / decision criteria                                                                                                                              |
| --------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| _None open._ | | Compute and IaC were settled by ADR-0031 (no cloud provider, so neither is needed), object storage by ADR-0032, and the docs generator by ADR-0033. |

## Architecture guidance

### Module boundaries (the seams that make #4 possible)

Inside the backend, treat modules as if they were services with a
wire-format contract:

- `core-recipes` — recipes, ingredients, nutrition, and recipe pictures.
  Pure domain; no knowledge of integrations, plugins, or HTTP clients.
  Owns its tables' JDBC persistence; the curated library and the
  ingredient catalog are seeded read-only content (ADR-0005, ADR-0015).
  Pictures (ADR-0023) are `recipe_picture` (what each is, keyed by
  recipe) and `recipe_picture_data` (bytes), which only
  `PostgresPictureStore`, behind the `PictureStore` seam, reads or
  writes; both cascade from the recipe.
- `core-planning` — meal plans, shopping lists, logging. Depends on
  recipes (through `RecipeService`'s and `IngredientService`'s public
  interfaces only — never their tables), nothing else. Owns its tables'
  JDBC persistence. A plan entry is a `PlannedItem`: a recipe or a food
  (an ingredient by slug), never both (ADR-0026).
- Neither core module knows about households (ADR-0019). Recipes,
  ingredients, plans, and shopping lists take an opaque `ownerId`, which
  the API sets to the caller's household id; meal logs, recipe creators,
  and plan-entry diners take profile ids. `core-planning` only requires a
  diner; who may be one, and the defaults, are decided in `apps/api`
  (`planning.Diners`, against the `household` package's member list).
- `apps/api` package `household` — membership, invites, and the join
  and leave rules (ADR-0019). Owns `household_invite` and
  `user_profile.household_id`, and creates and deletes `household` rows
  after sign-up (`identity` creates a new user's household of one). It
  changes what a household owns in other modules only through their
  public services (`moveAll`, `deleteAll`, `removeDiner`,
  `unlinkRecipesOf`), never their tables, and runs a join or leave in one
  transaction that locks the caller's profile row, then the households
  involved in id order. A plan write that names diners share-locks the
  same rows in the same order (`holdMembership`), so it never sees a
  member who is leaving.
- `apps/api` package `account` — account export/import (ADR-0011): an
  adapter over the core modules' public services, like the controllers.
  It never touches tables, so a migration that keeps the domain's meaning
  needs no export change; one that changes it needs a new format version
  (upgrade step + frozen fixture).
- `apps/api` package `mcp` — agent access over MCP (ADR-0014): parses
  the OpenAPI contract into tools at startup (`OpenApiToolParser`) and
  runs each call as a request to the API's own port (`ToolDispatcher`).
  It never calls the core modules, so it needs no change when an
  endpoint is added; the spec is its only input.
- `integrations/plugins` — the plugin runtime: registry, outbound
  extension-API client, suggestion-card and week-panel validation
  (ADR-0006, ADR-0017), and the tables it owns: per-household opt-ins
  (`plugin_opt_in`, ADR-0013) and per-household, per-plugin pseudonyms
  (`plugin_subject`, ADR-0017), keyed by `household_id` (ADR-0019).
  Depends on the core modules' public interfaces only; external API types
  never cross this boundary. Optional at runtime — no plugins registered,
  no behavior.
- `core-fueling` — fuel profiles and training sessions (ADR-0022),
  keyed by profile id, with their JDBC persistence (`fuel_profile`,
  `training_session`), and daily fuel targets: `targets.TargetCalculator`
  is a pure function of a profile and a day's sessions, with no clock,
  I/O, or Spring. Depends on no
  other module and knows nothing about households, plans, or
  intervals.icu; `apps/api` puts a day's target next to the caller's
  planned and logged totals. `FuelService` edits only `MANUAL`
  sessions; a synced one changes only when its source syncs. A profile
  never saved is targets off, never a 404 (`/api/v1/me/fuel-profile`,
  `/api/v1/me/training`; package `fueling` in `apps/api`).
  `GET /api/v1/me/fuel` reads up to 62 days, counted inclusively; each
  day carries its target (absent while targets are off), the caller's
  planned share (`MealPlanService.plannedTotals`), logged totals, and
  sessions. Never name a package `target`: `.gitignore` and
  `.dockerignore` drop every `target` directory as build output.
- `integrations/intervals-icu` — training from intervals.icu (ADR-0022).
  Its pieces: `IntervalsIcuClient` (HTTP Basic as `API_KEY` with a
  person's key; every call answers an `IcuResult`, with a 401 and a 429
  distinct from other failures), `IntervalsIcuTranslator`, and `SecretBox`
  (AES-256-GCM under `MIMOS_SECRET_KEY`, a fresh 96-bit nonce from the
  injected `SecureRandom` per encryption; a value that does not open under
  the current key throws `UnreadableSecretException`), and
  `IntervalsIcuConnections`, which owns `intervals_icu_connection` (one
  row per person: athlete id, the key sealed by `SecretBox`, status, last
  sync, last error, `retry_at` after a 429, and the running sync's
  claim) and exists only while the feature is active, and
  `IntervalsIcuSync`. Connect
  checks the key against the athlete record before saving it (a 401 or
  403 is a 400, no answer a 502 through `IntervalsIcuUnavailableException`);
  a key that no longer opens reads as `BROKEN`; disconnect deletes the row
  and the person's synced sessions (`FuelService.deleteSynced`). API:
  `/api/v1/me/intervals-icu` (package `fueling` in `apps/api`), a 404 on
  every route while the feature is off; `PUT` and `DELETE` are
  `x-mcp: false`. A sync (`POST /api/v1/me/intervals-icu/sync`, an MCP
  tool) first claims the connection row (`sync_claim`, a token with a
  two-minute lease), so one sync runs per person at a time; it then reads
  planned workouts and activities from 7 days back to 14 ahead of today
  by the injected `Clock`, and stores the outcome only while its claim
  stands, so a disconnect or a reconnect during a sync wins. A good read
  goes to `FuelService.replaceSynced`, which swaps that source's sessions
  in the range, and any of the incoming sessions' rows wherever they are
  dated, in one transaction. It always answers at once: a 401 or 403
  marks the connection `BROKEN`, a 429 stores `retry_at` from
  `Retry-After` in seconds (15 minutes without one, 24 hours at most; a
  same-athlete reconnect keeps it), a failure to store is a `lastError`,
  and nothing calls intervals.icu for a broken connection or before
  `retry_at`. `SecretBox` binds each sealed key to its profile id (GCM
  associated data), so a key copied to another row does not open.
  Each sync also reads 30 days of wellness and the athlete record into
  `ProfileHints` (weight, body fat, sex, cycling FTP; a value a fuel
  profile would refuse is left out), kept on the connection, each until a
  later sync finds a new one, and returned as `hints`: synced body fat
  feeds `TargetCalculator` when the profile has none, and the rest are
  suggestions the Fuel page offers, never applied on their own. An
  activity's reported `calories` become a synced session's `reportedKcal`
  (`REPORTED_CALORIES`, after work and before FTP: a device's figure for
  a session that happened beats a guess at its intensity). Activities
  that reach intervals.icu from Strava arrive as stubs (id, date,
  `source: STRAVA`, no duration) and are left out, as is any record
  without `moving_time`.
  `IntervalsIcuScheduledSync` runs the same sync for every connected
  person in turn every `mimos.intervals-icu.sync-interval` (default
  `30m`, at least a minute; `@EnableScheduling` only while the feature is
  active), skipping broken connections and people inside a `retry_at`,
  and moving on from one person's failure. No page load calls
  intervals.icu. External API
  types are package-private and never cross this boundary; events,
  activities, wellness, and the athlete record become `core-fueling`
  training sessions and `ProfileHints` at the edge. A synced session's id
  is derived from its person, kind, and intervals.icu id, so it keeps its
  id across syncs. Config: `mimos.intervals-icu.enabled`,
  `mimos.intervals-icu.base-url` (default `https://intervals.icu`),
  `mimos.intervals-icu.secret-key` (`MIMOS_SECRET_KEY`, 32 bytes in
  base64), and `mimos.intervals-icu.sync-interval`. The feature is on only when enabled and a key is set
  (`IntervalsIcuProperties.active()`); the `SecretBox` bean exists only
  then, and a malformed key fails startup. Compose ships a development
  key; the self-host `.env.example` asks for a generated one. A
  sync replaces a person's synced sessions in its date range, so a
  repeated or interrupted sync converges. Feature-gated and optional at runtime
  (self-hosters must not need it).
- `integrations/open-food-facts` — packaged food by barcode (ADR-0025).
  `OpenFoodFactsClient` makes one GET of
  `{base-url}/api/v2/product/{barcode}?fields=…` per lookup, with a
  `User-Agent` naming Mimos and its version, and a 5-second connect and
  read timeout. A 404 or `status: 0` is not found; any other non-2xx
  (a 429 included), unreadable JSON, or no answer throws
  `OpenFoodFactsUnavailableException`. `OpenFoodFactsTranslator` reads
  only the `_100g` nutriments (never `_serving` or
  `nutriments_estimated`) into `FoodProduct`, deriving kcal from
  kilojoules when it is missing, and calls a product per 100 ml when
  `nutrition_data_per`, `product_quantity_unit`, or
  `serving_quantity_unit` says ml (Open Food Facts says `100g` for most
  drinks). `Barcode` (8 to 14 digits) is the only way a value reaches
  the URL. Depends on no other module; Open Food Facts' JSON types are
  package-private. Config: `mimos.open-food-facts.enabled` (default
  true; the client bean exists only then) and
  `mimos.open-food-facts.base-url` (default
  `https://world.openfoodfacts.org`). API: `FoodProductsController` in
  package `food` of `apps/api`. No table, no cache.
- `plugins` — see below.
- Modules communicate through their public interfaces only; no reaching into
  another module's internals or tables. Cross-module FKs (e.g.
  `meal_plan_entry.recipe_id`) are integrity constraints, not access paths.
  Flyway migrations live in `apps/api` (the deployable owns the schema).
  Enforce via Maven's explicit inter-module dependencies plus ArchUnit
  tests once scaffolded.

### Plugin system

The reference use case: **"Country of the Week" — suggests a country, and the
week's meal plan leans into that cuisine.** The design is decided; ADR-0006
is the contract:

- Plugins are HTTP sidecar services (containers) registered via instance
  config (`mimos.plugins.*`): registered means available, changes take
  effect on restart. Static misconfiguration fails startup; an unreachable
  plugin never does.
- Plugins are opt-in per household (ADR-0013, ADR-0019): every plugin
  starts off, a member turns it on in the Plugins page (`/app/plugins`,
  linked from the profile menu), and `SuggestionService` never calls a
  plugin with the context of a household that has not. Opt-ins are consent for this instance's
  plugins, so the account export leaves them out. A manifest's
  `homepageUrl` becomes a link there, so only http(s) URLs survive
  parsing.
- v1 is pull-only: Mimos calls `POST {base}/v1/plan-suggestions` with a
  context snapshot (week, planned slots, library catalog) and `POST
  {base}/v1/week-panel` with a week and an optional button press;
  plugins never call Mimos and cannot change Mimos data. Applying a
  suggestion reuses the existing plan-entry endpoint — no new mutation
  path.
- Plugins see curated library recipes only. Personal recipes, profiles,
  identities, and logs never cross the plugin boundary. Each request
  names the household by `subject`, a random UUID per household and
  plugin (ADR-0017, ADR-0019), so a plugin can keep its own data about a
  household without learning who is in it. That data lives in the plugin's storage; the
  account export does not include it.
- UI is declarative: suggestion cards (title/blurb/icon/entries) and week
  panels (a collapsed summary, then `text`, `highlight`, `wheel`, and
  `actions` blocks; ADR-0017), validated by core, rendered by web as
  plain text and attributed to the plugin. No plugin code in the browser,
  no iframes in v1. A panel rendered without an action must not change
  the plugin's state.
- The extension API is versioned from day one (`mimos.plugin.manifest/v1`,
  `/v1/plan-suggestions`, `/v1/week-panel`), specified as OpenAPI in
  `contracts/plugins/v1/`, and treated as a public contract: additive
  changes only, deprecation with notice.
- Plugins never get raw database access; the runtime lives in
  `integrations/plugins` and talks to core through public interfaces only.
- The reference plugin `plugins/country-week` is a zero-dependency
  TypeScript service on `node:http`, run straight from source via Node's
  native type stripping (engines node >= 22.18); plugin packages live in
  the `plugins/*` npm workspace, and its tests run on `node --test`. It
  keeps each household's wheel in SQLite through the built-in `node:sqlite`,
  on the `country-week-data` compose volume.
- Deferred (designed in ADR-0006, built only when a plugin needs them): the
  plugin→Mimos callback direction with service accounts, iframe UI slots,
  card persistence, a plugin directory.

### Frontend specifics

- `apps/web` is Next.js (App Router) with TypeScript, sources under `src/`.
  Public recipe pages render server-side (SSR + ISR revalidation) for SEO;
  the logged-in product is client-heavy.
- The frontend talks to `mimos-api` through the OpenAPI contract; the
  generated client lives in `libraries/` (`@mimos/api-client`), never
  hand-written per feature (ADR-0003).
- Authentication happens in the browser via `oidc-client-ts` as the public
  `mimos-web` client (Authorization Code + PKCE); the app calls the API
  directly with bearer tokens and the API allows CORS for the web origin
  (ADR-0004). No server-side sessions, no Next-auth. The user is kept in
  `localStorage`, and the realm keeps a session 30 days unused and a year
  at most, so people stay signed in across tabs and browser restarts
  (ADR-0024); no Remember me, no offline tokens for the browser.
- Public URLs (API, Keycloak, app) and the sign-up policy are runtime env
  (`MIMOS_API_URL`, `MIMOS_OIDC_AUTHORITY`, `MIMOS_OIDC_CLIENT_ID`,
  `MIMOS_APP_URL`, `MIMOS_SIGNUP`), never build args (ADR-0012,
  ADR-0020): the server reads them in `src/lib/config.ts`, the browser
  from `/runtime-config.js`, which the root layout loads
  `beforeInteractive`. Never add a `NEXT_PUBLIC_*` value — it would bake
  one deployment's config into the published image. Prerendered pages
  carry the build's defaults, so a component that renders differently by
  this config reads it with `useSyncExternalStore` and a neutral server
  snapshot (`CreateAccountButton`), or the browser's first render
  disagrees with the prerendered HTML.
- Runs as a standalone Node container in compose (non-root). No
  Vercel-specific features that break self-hosting; anything platform-tied
  is rejected on the same grounds as prime directive #2.
- Server-side fetches (public pages) reach the API through `API_SERVER_URL`
  (runtime env, default `http://api:8080` in compose) — distinct from
  `MIMOS_API_URL` so the browser-facing and container-facing URLs can
  differ.
- Logic a future mobile app would share (API clients, types, validation
  schemas) lives in `libraries/`, not in app code.
- **Visual design** is Evening Kitchen (ADR-0008; reference and original mockup in
  `docs/design/`). Colors are tokens in `src/app/globals.css`, with light
  in `:root` (the default) and dark in `:root[data-theme="dark"]` (only
  when chosen with the toggle) — no literal colors in components. Errors
  use `--danger`, never `--accent`. Straight edges (no radius, no soft
  shadows) and no transitions or animations, except the week panel's
  wheel, which spins (and lands at once under `prefers-reduced-motion`;
  ADR-0017). Fonts are committed files
  in `src/fonts/` loaded with `next/font/local`; never a font host or a
  font npm package. Rebuild them as `src/fonts/README.md` says (its tools
  come from devenv). Keycloak's login theme and the docs site mirror both
  themes' tokens and reuse these fonts (`deploy/keycloak`, ADR-0010,
  ADR-0021; `docs/assets/`); the Android app generates its colours from
  `globals.css` at build time but copies the type and spacing scales
  (`apps/mobile/shared/.../ui/`) and the fonts (as TTFs); change them all together. The exception is the
  Twemoji flag subset (`src/fonts/twemoji-flags/`, ADR-0017), which is
  web only and draws nothing but flags. Wasm follows the same rule:
  zxing's reader is served from the web app's own origin, never a CDN
  (ADR-0025). `src/lib/barcode-polyfill.ts` points `prepareZXingModule`'s
  `locateFile` at `new URL("zxing-wasm/reader/zxing_reader.wasm",
  import.meta.url)`, which Next copies from `node_modules` into
  `.next/static/media/` under a content hash; never copy the binary by
  hand, and keep that module behind a dynamic `import()`. Pages start with
  `PageHeader` (eyebrow, serif h1, amber rule, actions) or, for a rail
  layout, `SplitPage`. The plan page is the one page wider than 1080 px: a
  ledger of days by meals whose cells show names only, with an inspector
  for the selected meal or day and the week's plugins in a rail beside it
  (a bottom sheet below 1100 px); its `main` and the header grow to
  1440 px through `:has(.plan-ledger)` (ADR-0028). Harness scenarios reach
  it through `planLedger(page)` in `tools/harness/scenarios/fixtures.ts`.
- Unit tests live in `apps/web/test/` and run on `node --test` (native type
  stripping, no test framework). A module they import must not touch the
  browser or Next, and imports its siblings with a `.ts` extension (Node
  resolves no others); keep such logic in small modules like
  `src/lib/api-fetch.ts`. Browser flows are tested by harness scenarios.

### Auth specifics

- Keycloak runs as a container in local compose; realm/client config is
  exported and checked into `deploy/keycloak` so environments are
  reproducible. The web origin in the realm is the import-time placeholder
  `${MIMOS_WEB_ORIGIN:http://localhost:3000}`. The Keycloak image bakes in
  the export minus its `users` (ADR-0012); only the dev compose stack mounts
  the full export with `test`/`test2`. Users added to the export never
  reach a published image.
- Keycloak's pages wear the `mimos` login theme (ADR-0010):
  `deploy/keycloak/themes/mimos/` extends `keycloak.v2` with CSS and one
  script (no copied templates, so upstream form ids stay). It follows the app's
  light or dark choice (ADR-0021): `signIn` and `signUp` send it as the
  `mimos_theme` query parameter, and `js/theme.js` stores it on
  Keycloak's origin, applies it as `data-theme`, and adds the app's
  toggle; `darkMode=false` stays, so the OS setting is not followed. A
  choice made on Keycloak's pages never reaches the app. Compose builds
  `deploy/keycloak/Dockerfile`, which adds the theme and the web app's
  committed fonts to the stock image. Its tokens mirror `globals.css`;
  the `keycloak-theme` harness scenario checks it. The brand above the
  form links to the web app's home through the realm's `displayNameHtml`
  (an `<a>` to `${MIMOS_WEB_ORIGIN}/`, which Keycloak's sanitizer keeps),
  not a template override.
- Spring Security with OIDC resource-server on the API. Authorization roles
  are realm/client roles from Keycloak; no parallel user tables in the app
  beyond a lightweight profile keyed by subject ID and its household
  membership (ADR-0019).
- The API validates the browser-facing issuer (`http://localhost:8081/realms/mimos`)
  but fetches JWKS through the internal compose host
  (`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`) — tokens carry the
  external issuer, so both must be configured when the network position
  differs.
- Security failures (401/403) are RFC 9457 problem-details, as are API errors
  generally. Domain errors map once in `ApiExceptionHandler`:
  `IllegalArgumentException` → 400, `NoSuchElementException` → 404,
  `ReadOnlyRecipeException` and `ReadOnlyIngredientException` → 403, `AccountNotEmptyException` and `MembershipConflictException` → 409, `InviteNoLongerValidException` → 410, `PluginActionFailedException`, `IntervalsIcuUnavailableException`, and `OpenFoodFactsUnavailableException` → 502. It extends Spring's
  `ResponseEntityExceptionHandler`, so Spring MVC's own errors (415, 405,
  unknown-path 404, 406, 413 for an upload over the multipart limit,
  malformed input) are problem-details too; anything
  unmapped is a 500 problem, logged with its stack trace, its message never
  sent. Adding method security means leaving its exceptions to the security
  chain, not this catch-all.
- `/actuator/health`, `/api/v1/public/**`, and
  `/.well-known/oauth-protected-resource/**` are the only unauthenticated
  endpoints (compose probes, SEO pages and the instance version, and where MCP
  clients learn to sign in). A 401 names that metadata in `WWW-Authenticate`.
- AI agents sign in as `mimos-agent`, a public client with PKCE S256
  required and no password grant (ADR-0014). Its loopback redirect URIs
  have no port, which Keycloak matches against any port; the claude.ai
  connector callback is listed exactly. Tokens are not audience-checked,
  so both clients' tokens work on every endpoint. The API reads
  `X-Forwarded-*` from private-network proxies
  (`server.forward-headers-strategy: native`) to name its public URL.
- API integration tests use Testcontainers Postgres **and** Keycloak (the
  realm export from `deploy/keycloak` is on the test classpath); the Keycloak
  container module is `com.github.dasniko:testcontainers-keycloak` (the
  upstream module left the core Testcontainers BOM). `test` and `test2`
  are shared by every test class; a test that needs an untouched account
  creates one with `ApiIntegrationTestSupport.createUser()` (Keycloak
  admin API).

## Repo layout (target)

```
mimos/
├── NORTHSTAR.md          # product vision
├── ROADMAP.md            # ordered build plan; steps get fleshed out as picked up
├── AGENTS.md             # this file
├── pom.xml               # Maven parent/aggregator for the backend
├── mkdocs.yml            # documentation config
├── .mcp.json             # MCP servers for agent sessions (Playwright browser, via devenv)
├── .claude/skills/       # Claude Code skills — thin pointers into AGENTS.md and docs (mimos-harness)
├── docs/                 # MkDocs source. Published: user guide (guide/), self-hosting, plugin authoring; assets/ holds the site theme and app screenshots. Repo-only: design/, harness/, decisions/, plans/
├── apps/
│   ├── api/              # Spring Boot modular monolith (the only deployable backend)
│   ├── web/              # Next.js frontend (src/fonts/: committed web fonts, ADR-0008)
│   └── mobile/           # Android app, Kotlin Multiplatform + Compose (ADR-0029): shared/ (commonMain UI and logic), androidApp/, build-logic/ (the Google Play Services ban)
├── contracts/
│   ├── api/              # OpenAPI spec — the source of truth for the HTTP API (ADR-0003) and its MCP tools (ADR-0014)
│   └── plugins/          # versioned plugin extension-API specs (ADR-0006)
├── core/
│   ├── core-recipes/     # recipe domain module
│   ├── core-planning/    # planning domain module
│   └── core-fueling/     # fuel profiles, training sessions, daily targets (ADR-0022)
├── integrations/
│   ├── plugins/          # plugin runtime: registry, extension-API client, card validation (ADR-0006)
│   ├── intervals-icu/    # training sync from intervals.icu (ADR-0022): client, translator, key encryption, connections, and sync
│   └── open-food-facts/  # packaged food by barcode from Open Food Facts (ADR-0025): client and translator
├── libraries/            # shared contracts: OpenAPI-generated clients, plugin SDK
│   ├── api-client/       # @mimos/api-client — generated TypeScript client (committed)
│   ├── plugin-sdk/       # @mimos/plugin-sdk — generated TypeScript types for the plugin API (committed)
│   └── test-cases/       # JSON cases for rules the web and the app both implement; read by the web's tests and the app's commonTest (README)
├── plugins/
│   └── country-week/     # reference plugin (build early to prove the API)
├── tools/
│   └── harness/          # agent harness: deploy, drive, observe, debug the stack (docs/harness); fakes/ of third-party services
└── deploy/
    ├── docker/           # compose: postgres, keycloak, api, web, plugin; compose.debug.yml and compose.fakes.yml overlays (harness only)
    ├── selfhost/         # compose + .env.example for servers, from the published images (ADR-0012); also how any hosted instance runs (ADR-0031)
    └── keycloak/         # realm export, mimos login theme, Keycloak image (ADR-0010)
```

## Conventions and defaults

- **Testing:** JUnit 5. Integration tests use Testcontainers (Postgres,
  Keycloak) — never mocked repositories or a hand-rolled test DB. New code
  ships with tests; a change isn't done until they pass.
- **Formatting:** Spotless formats Java (palantir-java-format: 4-space
  indent, 120 columns) and every `pom.xml` (sortPom, 4-space) — it applies
  automatically on every build; CI gates with `spotless:check`. Run
  `./mvnw spotless:apply` to fix violations. The Android app's Kotlin and
  Gradle scripts follow the same rule with Gradle's Spotless (ktfmt,
  kotlinlang style): `./gradlew check` applies it, CI gates with
  `./gradlew spotlessCheck`.
- **Null safety:** NullAway (JSpecify mode) via Error Prone; nullness
  violations in `@NullMarked` code fail the build. Every package needs a
  `@NullMarked` `package-info.java` (the build fails otherwise); annotate
  anything that may be `null` with `org.jspecify.annotations.Nullable`
  (type-use placement). `@NullUnmarked` is the escape hatch for code that
  cannot be checked yet; never `@SuppressWarnings("NullAway")` without a
  comment explaining the false positive. `.mvn/jvm.config` holds the javac
  `--add-exports`/`--add-opens` Error Prone needs — keep it in sync with the
  Error Prone install docs when bumping `error-prone.version`. Only
  nullness gates the build; other Error Prone checks stay disabled.
- **Containers:** Docker is the only supported runtime — tests (via
  Testcontainers), local dev (via compose), and CI all require it. A new
  deployable ships its Dockerfile and its compose service in the same
  change; images build in CI and run as non-root. "Works on my machine,
  outside Docker" does not count. The web and plugin images `npm ci` the
  whole npm workspace from its manifests, so a new workspace's
  `package.json` is copied into those Dockerfiles too. The Android APK is a
  client artifact like a browser, not a deployable, so it has no Dockerfile
  or compose service (ADR-0029); CI builds it in the `android` job.
- **Dev environment:** `devenv.nix` (+ `devenv.yaml`, `devenv.lock`,
  `.envrc`) provides the toolchains for Nix users, pinned to match CI (JDK
  21, Node 22, Python 3.13 + `docs/requirements.txt`). Keep it in sync when
  CI versions change. Product paths (build, tests, compose, CI) must not
  depend on it, and the Docker daemon remains a host concern. **Agent
  tooling is the exception:** anything an agent needs to drive the repo
  (the debug harness, browsers, MCP servers, `jdb`, `jq`) comes from
  `devenv.nix` / nixpkgs — never ad-hoc downloads or host installs — and
  agents invoke it as `devenv shell -- <cmd>`. The shell's banner goes to
  stderr so that command's stdout stays clean. The Android SDK (unfree, so
  `devenv.yaml` allows unfree and accepts its licence) is an `androidenv`
  composition: platform tools, the emulator, platforms `android-37.0`
  (`compileSdk`) and `android-36` (`targetSdk`), build tools 36.0.0, and
  one x86_64 `default` (AOSP, no Google APIs) system image at `targetSdk`,
  the newest API nixpkgs has an AOSP image for; `ANDROID_HOME` points at
  it, and Maestro is on the PATH. The emulator's browser, Cromite (the
  image ships none), is a `fetchurl` of a pinned release APK in
  `devenv.nix`, exported as `MIMOS_ANDROID_BROWSER_APK`; bump its URL and
  hash together. `apps/mobile/gradle/libs.versions.toml`
  is the one place the SDK versions are stated: `devenv.nix` reads
  `compileSdk`, `targetSdk`, and `buildTools` from it, and CI's `android`
  job reads `compileSdk` and `buildTools`, because Gradle cannot install
  SDK pieces into the Nix store and must find them exactly.
  devenv also sets `android.aapt2FromMavenOverride` (through `GRADLE_OPTS`)
  to the SDK's `aapt2`, since the one AGP downloads does not run on NixOS.
- **Agent harness:** `tools/harness` (`@mimos/harness`), on the PATH in the
  devenv shell as `harness`; how to use it is in "Debugging the running
  stack" below. Browser flows are Playwright scenarios in
  `tools/harness/scenarios/`, and every scenario runs in CI's compose job
  (a known failure is listed in the job's `KNOWN_FAILING` until fixed).
  `.mcp.json` gives agent sessions a Playwright MCP browser for
  exploration. `@playwright/test` is pinned to nixpkgs' `playwright-driver`
  version — bump both together. Debug-only behavior goes in
  `deploy/docker/compose.debug.yml` (`harness up --debug`), never in
  `compose.yml`, and debug ports bind to loopback only. A third-party
  service Mimos calls (intervals.icu, Open Food Facts) gets a fake for scenarios: a
  zero-dependency `node:http` service in `tools/harness/fakes/<service>/`
  with in-memory state, fixtures, and `/_fake/` control routes, tested
  with the harness's own tests and run by `deploy/docker/compose.fakes.yml`,
  which points the API at it and publishes it on loopback only. Never in
  `compose.yml` or `deploy/selfhost`. `harness up` always adds that
  overlay; CI adds it after the self-host smoke tests, before the
  scenarios. The plan and one
  page per step are in `docs/harness/`, the command reference in
  `docs/harness/usage.md`; read them before building harness pieces, and
  keep them current as the harness changes.
- **Docs:** MkDocs. Doc changes ship with the code change they describe;
  `mkdocs build --strict` is the check once `mkdocs.yml` exists. Keep the
  nav in `mkdocs.yml` accurate; ADRs are pages under `docs/decisions/`.
  The site is written for people who use or host Mimos, as a user guide:
  a change to what users see updates its page in `docs/guide/` (and its
  screenshot in `docs/assets/screenshots/`, taken with the Playwright MCP
  browser at 1280px wide, light theme, against the compose stack).
  Contributor material goes in a repo-only directory listed in
  `exclude_docs`; a site page that needs one links to it on GitHub, never
  relatively.
- **Migrations:** Flyway, forward-only. Never edit an applied migration;
  add a new one. Migrations run automatically on startup so self-hosters
  upgrade by pulling and restarting.
- **Agent tools:** a new endpoint under `/api/v1` is an MCP tool
  automatically, named by its `operationId`. Its `summary` and
  `description` are all an agent reads, so state the rules a caller
  would otherwise guess (a plan's `startDate` is a Monday). Opt an
  operation out with `x-mcp: false` only when an agent should not call
  it; `McpEndpointTests` pins the opt-out list. The server's
  `instructions` live in `application.yml`.
- **Error handling:** RFC 9457 problem-details responses from the API.
  Request bodies are read strictly: a fractional value for an integer
  field is a 400, never truncated (`spring.jackson.deserialization.accept-float-as-int: false`).
- **Logging and request IDs:** every API request gets an `X-Request-Id`
  (a well-formed client one is kept, otherwise generated), echoed on the
  response, in the MDC as `requestId`, and on every log line the request
  produces. `RequestLoggingFilter` writes one access line per request, and
  a 4xx/5xx line names the exception that caused it. Errors raised in the
  security chain must reach `RequestLoggingFilter.recordFailure` (as
  `ProblemDetailSecurityHandlers` does), or the line cannot name them.
  Clients send their own ID per call (`apps/web/src/lib/request-id.ts`),
  except cached server-side fetches: Next's fetch cache keys on headers.
  Text logs by default; `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` for JSON.
- **User-facing copy:** no em-dashes (U+2014) in anything a user reads:
  web UI strings and page titles, the Android app's strings, library seed content, plugin card text
  (the reference plugin included), the Keycloak theme, and API error
  messages. Rewrite the sentence with a period, comma, colon, or
  semicolon; don't just swap in another dash. En dashes are fine for
  ranges (`20–25 minutes`) and for an unknown value (`–`). Code comments
  and docs are not covered.
- **Time/money-free:** no wall-clock dependence in domain logic; clocks and
  randomness are injected (`TimeConfig`'s `Clock` and `SecureRandom`).
- **Commit style:** conventional commits, present tense, scoped when it
  helps (`feat(planning): ...`).
- **Naming:** product name is **Mimos**; the repo/backend service is
  lowercase `mimos-api`; database `mimos`.

## Debugging the running stack

When something fails on the running stack — a 4xx/5xx, a broken page, a
flow that does not work — investigate with the agent harness, not by
guessing. Run it as `devenv shell -- harness <command>`; every command
writes evidence to `.harness/runs/<run>/` (gitignored), and
`.harness/runs/latest/summary.md` is the first thing to read.
`docs/harness/usage.md` documents every command.

1. **Start** — `harness up --debug`: fresh images plus the debug overlay
   (JSON API logs, actuator diagnostics, JDWP on `127.0.0.1:5005`, the
   Node inspector on `127.0.0.1:9229`). Prefer it over a bare `docker
   compose up`, which does not rebuild images.
2. **Reproduce** — a browser scenario (`harness ui <scenario>`; write one in
   `tools/harness/scenarios/` if none covers the flow) or an API call
   (`harness api <METHOD> <path> [--body <file>]`). No reproduction, no fix.
3. **Read the evidence** — `summary.md` first (failing step, every API
   call with its request ID, failed requests in full), then the HAR,
   `harness logs --request-id <id>` (one request's lines across services),
   and `harness diag` (state, actuator, `routes.txt` with each route's
   media types, migrations vs. the repo, plugin manifests).
4. **Hypothesize from evidence, and narrow it** — `harness loglevel <logger>
   <level>` changes API log levels live (`--reset` restores them);
   `harness sql "<query>"` reads the database; `harness debug break
   <Class:line|Class.method> --then "<api|ui …>"` stops the API at a
   breakpoint and records stack, locals, and `--print` expressions — or an
   explicit "not hit", which is evidence too.
5. **Fix** at the layer that is wrong, with a test at that layer.
6. **Re-run the reproduction**; it must pass.
7. **Keep the scenario** — it is the regression test, and CI runs it.

## Definition of done (for any change)

1. Builds clean; existing tests pass; new behavior is tested.
2. `deploy/docker` still boots the full stack (if the change touches
   anything it runs).
3. Any new deployable has a Dockerfile and a compose service, and all
   affected images still build.
4. Migrations (if any) are forward-only and safe on existing data.
5. OpenAPI contract updated if API shape changed.
6. AGENTS.md updated if a decision, convention, or layout element changed.
7. No secrets in code or compose files; config comes from env with sane
   local defaults.

## Verification commands

- `./mvnw -B verify` — full backend build + tests. Requires Docker:
  integration tests use Testcontainers (Postgres). JDK 21+ (compiled with
  `--release 21`). Formatting applies automatically during the build;
  `.mvn/jvm.config` (javac `--add-exports`/`--add-opens` for Error Prone)
  is picked up automatically by `mvnw`.
- `./mvnw -B spotless:check` — formatting gate (Java + POMs) without
  writing.
- `mkdocs build --strict` — docs build. Requires `pip install -r
  docs/requirements.txt`.
- `npm ci && npm run generate -w @mimos/api-client` and
  `npm run generate -w @mimos/plugin-sdk` — regenerate the TypeScript API
  client and plugin SDK from `contracts/`; commit the results.
  Regenerating must produce no diff when the specs are unchanged.
- `npm run typecheck -w @mimos/web` and `npm run build -w @mimos/web` —
  frontend types and production build.
- `npm test -w @mimos/country-week` — the reference plugin's tests
  (`node --test`; Node 22.18+ for native type stripping).
- `cd apps/mobile && ./gradlew check assembleDebug` — the Android app:
  `commonTest`, ktfmt through Spotless, Android lint (warnings are errors),
  and the Google Play Services ban, with its TestKit tests. On NixOS run it
  as `devenv shell -- bash -c 'cd apps/mobile && ./gradlew check
  assembleDebug'`, which provides the SDK. JDK 21.
- `devenv shell -- harness mobile flow connect-and-sign-in` — a Maestro flow
  against the running stack and the installed debug app (`harness up` and
  `harness mobile up` first); `node tools/harness/src/api-parity.ts` — the
  operations the web calls and the app calls.
- `npm test -w @mimos/web` — the web app's unit tests (`node --test`, as
  above).
- `npm run typecheck -w @mimos/harness` and `npm test -w @mimos/harness` —
  the agent harness's types and unit tests (no Docker or browsers needed).
- `node tools/harness/src/contract-additive.ts <old.yaml> <new.yaml>` — the
  API contract's additive-only check; CI runs it against the last `v*`
  tag's `openapi.yaml`. Locally: `git show <ref>:contracts/api/openapi.yaml >
  /tmp/old.yaml`.
- `./mvnw -B -pl apps/api test -Dtest='McpEndpointTests,AgentSignInTests,OpenApiToolParserTest'`
  — the MCP server and agent sign-in on their own (part of `verify`).
  Against a running stack, `curl
  http://localhost:8080/.well-known/oauth-protected-resource/mcp` names
  the realm, and `claude mcp add --transport http --client-id
  mimos-agent mimos http://localhost:8080/mcp` connects Claude Code.
- `devenv shell -- harness diag` — diagnostics snapshot of a running
  stack (full with `harness up --debug`); exit 1 means it found a problem
  (listed in `.harness/runs/latest/summary.md`).
- `devenv shell -- harness debug break MeController:27 --then "api GET
  /api/v1/me"` — debugger smoke against a stack started with `harness up
  --debug`: exit 0 and a hit with locals in
  `.harness/runs/latest/debug/1/hits.md`.
- `devenv shell -- harness ui login` — browser smoke against a running
  stack (Chromium from devenv); `harness ui create-recipe` drives a write
  through the form.
- `docker compose -f deploy/docker/compose.yml up -d --wait` — boots the
  full self-hosted stack (Postgres, Keycloak, API, web, and the reference
  plugin `country-week`); healthy when `--wait` returns 0. Note: `up` does
  not rebuild images — run `docker compose build` first when code changed
  (`devenv shell -- harness up` does both and records the outcome).
  Smoke: API at `http://localhost:8080/actuator/health`, Keycloak realm at
  `http://localhost:8081/realms/mimos`, web at `http://localhost:3000`
  (log in with `test` / `mimos-test`; the realm also ships `test2`, same
  password, for cross-user isolation), seeded library at
  `http://localhost:8080/api/v1/public/recipes` and
  `http://localhost:3000/recipes`, the Country of the Week panel on the
  plan page (after login, with the plugin turned on in the Plugins page
  or via `PUT /api/v1/me/plugins/country-week`; API
  `/api/v1/plans/{monday}/panels`), and, once a country with library
  recipes is chosen for the week (`POST
  /api/v1/plans/{monday}/panels/country-week/actions` with
  `{"id":"choose","value":"IT"}`), a suggestion card from
  `http://localhost:8080/api/v1/plans/{monday}/suggestions` or the plan
  page's Suggestions panel.
- `POSTGRES_PASSWORD=x KEYCLOAK_ADMIN_PASSWORD=x docker compose -f
  deploy/selfhost/compose.yml --env-file deploy/selfhost/.env.example
  config -q` — the self-host compose file resolves. To boot it against
  local builds, tag them `ghcr.io/noahhhx/mimos-<name>:<tag>`, set
  `MIMOS_VERSION=<tag>` and localhost URLs in an env file, and use a
  different project name (`-p`) from the dev stack.
- CI (`.github/workflows/ci.yml`) runs all of the above on every PR; the
  compose job is the self-host parity check. On a push to main or a `v*`
  tag, the `publish` job then pushes the images (ADR-0012). The compose
  job's last steps run every harness scenario through devenv against the
  booted stack and upload `.harness/runs/` as the `harness-runs` artifact
  when one fails. On a push to main, the `pages` job publishes the docs
  site to GitHub Pages once `docs` passes.

## Decision log

Significant decisions beyond the table above get a short ADR in
`docs/decisions/` (context, decision, consequences). When in doubt whether
something is significant: if a future agent could plausibly choose
differently, it is.
