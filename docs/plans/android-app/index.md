# Android app: work items

Built to [ADR-0029](../../decisions/adr-0029-android-app.md). A native
Android app with every feature the web app has, for people who would
rather open an app than a browser tab. Kotlin Multiplatform with only the
Android target, Compose UI, no Google Play Services, connected to any
Mimos instance, and released as a signed APK on GitHub Releases. iOS is
not built, but nothing here may close it off.

The order is deliberate. A virtual device comes right after the scaffold,
so every item from sign-in onward checks itself on it. The client, sign-in,
the theme, and Maestro flows in CI come next, so every feature item after
them lands with a flow CI runs. Feature items follow the web's pages.

## How to work an item

1. Take the first `todo` item whose dependencies are `done`. One item per
   session.
2. Read AGENTS.md, ADR-0029, and the item before writing code. A feature
   item also reads the ADRs its web page was built to; the app follows the
   same rules, and the web page is the reference for behavior and copy.
3. Build what the item says. If it turns out bigger than one session, split
   it here first: add the new item file and its row, then carry on with the
   smaller piece.
4. Run the item's checks, plus the gates below for what you touched. Only
   one stack runs at a time, because compose binds fixed host ports.
5. An item marked **Review** changes what people see in a way the web does
   not settle, or needs the owner's decision. Post its screenshots (or the
   question) and wait for the go before landing.
6. Land on `docs/mobile-app-plan`: conventional commits, present tense
   (`feat(mobile): ...`), each commit builds. In the item's last commit, set
   its row below to `done` with the commit's short SHA.
7. Anything out of the item's scope goes in "Found along the way".

## Items

| #  | Item | Depends on | Status |
| -- | ---- | ---------- | ------ |
| 01 | [Scaffold, toolchain, and build gates](01-scaffold.md) | – | done (73a86f9) |
| 02 | [A virtual device for agents](02-emulator.md) | 01 | done (60f3a16) |
| 03 | [Generated client and instance discovery](03-client-and-discovery.md) | 01 | done (30ca9b9) |
| 04 | [Sign-in and sign-up](04-sign-in.md) | 02, 03 | done (cc91ea8) |
| 05 | [Evening Kitchen in Compose](05-design-system.md) | 01 | done (6577f8b) |
| 06 | [Maestro flows and CI](06-flows-and-ci.md) | 04, 05 | done (43e67dc) |
| 07 | [Recipes: browse, read, and cook](07-recipes.md) | 06 | done (dc1354c) |
| 07b | [The recipe form](07b-recipe-form.md) | 07 | done (fcbdc80) |
| 07c | [Recipe pictures](07c-recipe-pictures.md) | 07b | done (bba561e) |
| 08 | [The week's plan](08-plan.md) **Review** | 07 | review (branch `mobile/08-plan`) |
| 08b | [A new ingredient from the plan's picker](08b-plan-new-ingredient.md) | 08 | done (93c15ac) |
| 09 | [Plugins: page, suggestions, week panels](09-plugins.md) | 08 | done (f9e9c8e) |
| 10 | [Shopping list](10-shopping-list.md) | 08 | done (24d83bc) |
| 11 | [Log and barcode scanning](11-log-and-barcodes.md) | 08 | todo |
| 12 | [Fuel and intervals.icu](12-fuel.md) | 11 | todo |
| 13 | [Kitchen home](13-kitchen.md) | 08, 09, 10 | todo |
| 14 | [Household and invites](14-household.md) | 06 | done (e5731f5, branch `mobile/14-household`, stacked on 08) |
| 15 | [Account, settings, export and import](15-account.md) | 06 | done (965f7de, branch `mobile/15-account`, stacked on 14) |
| 16 | [Release: versions, signing, GitHub Releases](16-release.md) | 03, 06 | review (fe5ab25, branch `mobile/16-release`, stacked on 15): the owner's keys and the CI dry run |
| 16b | [Flows against the last release](16b-release-skew.md) | 16, a release with `mimos-mobile` | todo |
| 17 | [Parity audit and user guide](17-parity-and-docs.md) | 07–16 (07b, 07c, and 08b included) | todo |
| 18 | [Offline shopping list](18-offline-shopping-list.md) **Review** | 10, 17 | todo |

Once their dependencies are done, items 09 to 12 and 14 to 16 are
independent and can run in parallel in separate worktrees. Item 16 can
land right after 06, so testers get signed builds while features arrive.

## Gates

- Mobile: `cd apps/mobile && ./gradlew check assembleDebug` (unit tests,
  ktfmt through Spotless, Android lint, and the Google Play Services ban).
- Mobile flows: `devenv shell -- harness up`, then
  `devenv shell -- harness mobile flow <flow>` for the item's flows.
- Backend (items that touch it): `./mvnw -B verify`.
- Contract (items that touch it): `npm run generate -w @mimos/api-client`
  leaves no diff, and `./gradlew check` compiles the regenerated Kotlin
  client.
- Web (items that touch it): `npm run typecheck -w @mimos/web`,
  `npm test -w @mimos/web`, `npm run build -w @mimos/web`.
- Harness: `npm run typecheck -w @mimos/harness`, `npm test -w @mimos/harness`.
- Docs: `devenv shell -- mkdocs build --strict`.

## Decided by default, open to the owner

The owner settled the platform (Android only), the language (Kotlin), the
verification approach, and distribution (a signed APK on GitHub Releases,
no Google Play Services). The planning agent filled in the rest, and the
owner can overturn any of it:

- `minSdk` 26 (Android 8.0), for older family phones.
- `apps/mobile` is the project root, and `io.github.noahhhx.mimos` the
  application id (`.debug` for debug builds).
- People connect with the instance's web address, not its API URL.
- The Kotlin client is generated at build time, not committed.
- The visual design mirrors Evening Kitchen exactly, not Material defaults.
- App and server share one version and release on the same `v*` tag.
- The app works online only until item 18.
- Flows run on every PR in CI. If the emulator job costs too much (item 06
  measures it), they move to main and tags only. Item 06 measured about 4 minutes added
  per PR, so they stay on every PR.

## Settled by the owner's grant

- **Plain HTTP in release builds.** No. Release builds connect over
  HTTPS only and trust user-installed CAs, so a home instance with a
  private CA works as it does in the phone's browser. An instance on
  plain HTTP cannot sign in from the web either (the self-hosting
  guide's "Why HTTPS"). Item 03's connect screen names the reason. See
  [ADR-0030](../../decisions/adr-0030-app-https-only.md).

## Later, not in this plan

- The plan ledger on tablets (ADR-0028). Phones get a list of days first;
  tablets use the phone layout until then.
- An F-Droid build: reproducible APKs (pinned dependencies, Gradle
  dependency verification, no timestamps) and a check that zxing-cpp's
  prebuilt native libraries are acceptable there.
- A QR code on the web for connecting, if typing the web address proves
  to be a stumbling block.
- iOS, which needs a Mac and its own ADR.

## Found along the way

- Compose 1.12 needs `compileSdk` 37, but nixpkgs has no AOSP `default`
  system image above API 36. So `compileSdk` is `37.0`, `targetSdk` is 36,
  and the emulator image is API 36 (item 01). Move `targetSdk` up when
  nixpkgs gets a newer AOSP image.
- Debug builds permit cleartext to `localhost` only, not `10.0.2.2`: item
  02 reaches the stack through `adb reverse`, so the device's `localhost`
  is the host's and the token issuer matches.
- The KMP Android library plugin gives lint no analysis of `commonMain`.
  `shared` applies `com.android.lint` so `androidApp`'s lint
  (`checkDependencies`) reads it.
- `androidx.lifecycle` 2.9, which `activity-compose` brings, ships lint
  checks this lint cannot load (`ObsoleteLintCustomCheck`, an error under
  warnings-as-errors). `androidApp` pins `lifecycle-runtime` 2.11.0.
- `gradle/actions/setup-gradle` caches with a proprietary provider by
  default. CI sets `cache-provider: basic`, its MIT one.
- `adb reverse` rules belong to the adb server that made them. Any server
  restart (`adb kill-server`, or a client of another adb version) empties
  them, and the device then refuses connections to `localhost`. `harness
  mobile up` re-makes and proves them, but a flow run (item 06) must check
  them first, or pin `ANDROID_ADB_SERVER_PORT` for the harness so nothing
  else restarts its server. Maestro's own adb use is unchecked.
- The first time the spike saw `ERR_CONNECTION_REFUSED` in the browser with
  the rules listed, the cause was never found. Two things reproduce it
  from the device's side: no rules after a server restart, and rules
  listed while nothing answers on the host port. If it recurs, read
  `mobile/adb.txt` and `mobile/probe.txt` from the failed `up`.
- Cromite shows a toast ("loading flags from a command-line file") when
  it opens, because of the command-line file `up` writes. A flow
  that opens the browser should expect it.
- Compose drops a field's focus when the same field is emitted from two
  branches of a `when`: the first keystroke after a failed connect moved
  the screen from `Failed` to `Editing`, and the field lost every later
  key. Unit tests of the state machine passed; only the emulator showed
  it. The connect screen emits its form from one call site for every
  `ConnectState.Addressing` state (item 03). Item 05's components should
  keep that shape.
- Models are held in `AppViewModel` since item 04, so a connect or a
  sign-in in flight survives a rotation. New screen models belong in
  `AppModel`, with their coroutines in its scope, not in `remember`.
- The Kotlin client cannot read a binary body with `body()`
  (`getRecipePicture`); `.response.readRawBytes()` works. The generated
  multipart upload works against the server (item 07c), but Spring reads a
  part as a file only when its `Content-Disposition` names a filename.
- `/runtime-config.json` names `mimos-web` as its OIDC client. The app takes
  the authority from it and signs in as its own `mimos-mobile`
  (`OIDC_CLIENT_ID`), so `InstanceConfig.oidcClientId` is unused.
- Cromite blocks a redirect to an app's own scheme unless "Allow forward URL
  requests to external intents" is on. The harness turns it on with a
  command-line feature switch; an AVD that predates item 04 needs `harness
  mobile up` and a force-stop of the browser. Item 06's flows depend on it.
- Driving a Keycloak page with `adb input`: tap a field or Tab between
  fields, type, and press Enter (keyevent 66). A tap at fixed coordinates
  misses when the page's zoom differs.
- AppAuth checks more than its docs say: it refuses an `http://` issuer
  when validating the ID token (`setSkipIssuerHttpsCheck`, debug only) and
  refuses `prompt` as an additional parameter.
- Compose Resources only packages a KMP library's files when its
  `android {}` block has `androidResources { enable = true }`. Without it
  the build passes and the fonts silently fall back to Roboto. Check a
  screenshot, not the build, after touching resources.
- `woff2_decompress` writes the `.ttf` next to its input, in the web's
  `src/fonts/`. Run it on copies (the README's "Mobile" does).
- Newsreader is variable in `opsz` as well as `wght`. Setting
  `FontVariation.weight` and `opticalSizing` per style renders correctly on
  API 36; the weights differ visibly (Public Sans 400 against 600).
- A Compose `Dialog` window fades in by default. Setting its window
  animations to `android.R.style.Animation` (the empty style) turns that
  off; `dumpsys window` shows `wanim=0x1030000` on the dialog's window.
  The dialog's window also reports 84px surface insets, which draw
  nothing here because the content fills the window.
- The compose stack left running from an earlier session was built before
  `/runtime-config.json` existed, so the connect screen said the address
  "does not answer as Mimos". `harness up` rebuilds; item 06 should
  start from it.
- A typed `localhost:3000` is read as `https://localhost:3000`; debug
  builds accept `http://` only when it is typed.
- The splash and first frame use the activity theme's light window colour
  before `MainActivity` sets the chosen theme's `bg`. A dark-theme user may
  see a light flash at cold start; a night-neutral splash theme would fix it
  if it matters.
- `java.text.Collator` at primary strength ignores spaces and marks, so on a
  desktop JVM "Apple tart" follows "Applesauce", which `Intl.Collator` does
  not do. `commonTest` runs on a desktop JVM, so item 07 ports the web's
  order by hand (`recipes/Collation.kt`: whitespace, then marks in the CLDR
  order, then digits, then letters, with Latin accents folded) and tests it
  against `libraries/test-cases/recipe-order.json`, which the web's tests run
  through `Intl`. It is exact for Latin text. If other scripts matter, the
  phone has ICU (`android.icu.text.Collator`), but a unit test cannot run it.
- Maestro on a Compose toggle: the tick state is on the row's `View`
  (`checked`), and the text is on a child. Assert it with `checked: true`
  and `containsChild: <text>`. `checked: false` with `containsChild` never
  matched, and an element under the tab bar is not visible to Maestro even
  when `scrollUntilVisible` stopped on it, so assert a row before scrolling
  past it.
- `AppModel` owns the connected instance's `MimosHttp` (`AppModel.http`), not
  the composition: a model's request must not die with a rotation. `App` no
  longer takes the engine.
- The recipe list's tag filter is one scrolling line, not the web's wrapped
  row. The seed library has 22 tags, which pushed every recipe off a phone's
  first screen.
- The web shows "0 min cook"
  for a recipe with `cookMinutes` 0, and so does the app.
- The generated client accepted only `application/json`. `deleteRecipe`'s
  only answers with a body are problems, so the API produces
  `application/problem+json` for it and answered every app delete with a
  406; item 07's delete never worked against a server, and no flow deleted
  anything until `create-recipe` (item 07b). `MimosHttp` now accepts problem
  JSON too. Another operation whose only bodies are problems would have
  failed the same way; the web's fetch accepts anything.
- Running Maestro from the Maestro MCP server while `harness mobile flow`
  runs kills the device's driver: the flow fails late with "Unknown error"
  or a `viewHierarchy` "Rst Stream", after minutes. Use one or the other.
- A `TextField`'s visible label is hidden from accessibility, and the field
  itself carries the name (`description`, or the label), so a flow's `tapOn:
  Title` lands in the field rather than on the label above it. Fields that
  share a visible label ("Amount" on every ingredient) take the web's
  `aria-label` as their `description` ("Ingredient 1 amount"). Maestro's
  text match is a full-string regex ignoring case: escape `+`, `(`, `?`.
- Ktor's `MockEngine` answers off the test scheduler, so `advanceUntilIdle`
  does not wait for it. Model tests wait on the state (`first { ... }`).
- Private top-level classes in one package collide across `commonTest` files
  (`CLASSIFIER_REDECLARATION`); give each file's case classes its own names.
- No font the app ships has "▾"; the fallback's taller line made a
  `SelectField` taller than the `TextField` beside it, so the arrow is drawn.
- The web's form keys its estimate on what it sends, not on which rows sent
  it, so removing an empty row above a linked one leaves each "Not counted"
  hint on the wrong row until the next change that alters what is sent. Its
  new-ingredient panel also stays on the same index when a row above it is
  removed. The app keys the estimate on both and moves the panel; the web is
  unchanged.
- A linked ingredient on a recipe's detail reads "1 serving " with a
  trailing space in its own text node; a flow matches it with `\s*`.
- `TakePicture` needs no permission only while the manifest declares no
  `CAMERA` permission. Item 11's barcode camera will declare it, and then
  "Take a photo" must ask for it first, or the camera app's launch throws.
- The system photo picker on the API 36 image labels each photo "Photo taken
  on <date>" and confirms with "Add (1)"; a flow taps those. Its thumbnails
  are cropped squares, so a flow cannot tell orientation from them.
- When the Maestro MCP server's device driver has died (after a `harness
  mobile flow`), `adb shell uiautomator dump` still reads the screen's labels.
- Compose's `selected` semantics on a `Button` never reached the
  accessibility node, so Maestro's `selected: true` never matched; the
  recipe list's tag buttons have the same gap. `Button(pressed = ...)` uses
  `toggleable` instead. Even so, `uiautomator dump` reported the diner
  chips unchecked while they were on, and plain buttons checkable, so
  `shared-week` proves the toggle by its effect (the meal leaves Mine)
  rather than by `checked: true`.
- The generated Kotlin client renames a schema property called `entries`
  to `propertyEntries` (`MealPlan`), since Kotlin enums reserve the name.
- A `label` style `Label` in an 84dp column wraps "BREAKFAST"; the plan's
  meal column is 104dp.
- With the default launch mode, a link opened from another app can start a
  second `MainActivity`, with an `AppViewModel` and models of its own, beside
  the running one: two sessions refreshing one stored token. That was
  reasoned, not reproduced. `MainActivity` is `singleTask` since item 14, and
  a link opened while the app runs arrives in `onNewIntent` (`am start`
  reports it delivered to the running instance). Sign-in and
  sign-out through the browser still return to it (`connect-and-sign-in`,
  `stay-signed-in`, and `household-invite` sign in and out).
- Maestro's `openLink` opens a custom-scheme link as `am start` does, so a
  flow builds the app link in `evalScript` from `maestro.copiedText` and
  opens it. The system share sheet on the API 36 image is titled "Sharing
  link".
- Item 14 was built on `mobile/08-plan` while item 08 waits for review, since
  both change `AppModel` and the shell. It needs nothing from 08 and can be
  rebased onto `docs/mobile-app-plan` if 08 changes.
- Item 15 is stacked on `mobile/14-household` for the same reason: both
  change the shell and `AppModel`. It needs nothing from 08 or 14.
- The app has a Settings screen the web lacks (theme, instance and its
  version, Change instance, app version, licences), so `Destination.webPath`
  is nullable. Change instance moved from the profile sheet to it.
- The licences come from the release classpath's POMs at build time
  (`generateLicences`), one row per Maven group. The Google Play Services
  ban rules out Google's own licences plugin, and a library such as
  AboutLibraries would be a new top-level dependency. The plugin fails the
  build for a dependency whose POM and parents name no licence.
- The system's file pickers (DocumentsUI on the API 36 image) open in
  Downloads, list it newest first, and save a repeated name as
  `name (1).json`. Downloads keeps every export across flow runs, so
  `export-import` picks the newest by index rather than by name.
- Maestro's `evalScript` left a variable unset when the script used a
  regex replacement with `$&`, and reported success. `export-import`
  avoids scripts.
- Once, one `scrollUntilVisible` call hung for 40 minutes in Maestro's
  driver while the screen showed the element. A rerun passed; it did not
  recur in the two runs after it.
- `RecipeFormModelTest` took the last request seen as the picture upload,
  and the form's debounced nutrition estimate sometimes came after it. It
  now finds the upload by path.
- No CI job runs a minified build. Item 16 ran every flow once on a debug
  build minified with the release rules, and all passed, but a dependency
  update could break the release APK while every job stays green. A flow
  against a minified build in `android-flows` would catch it.
- The picker's new ingredient starts at 100 g, where the recipe form's takes
  its basis from the line's unit: a picker search has no unit to go by
  (item 08b).
- Item 08b was built on `mobile/16-release`, the top of the stack, since 08
  still waits for review.
- Item 16 is stacked on `mobile/15-account`, since it changes `App.kt`,
  `AppModel.kt`, and the shell as 14 and 15 do. It needs nothing from them.
- The seed library has one cuisine a Country of the Week card can match
  (Italian: Minestrone and Spaghetti Aglio e Olio), so a random spin almost
  never brings a suggestion. A flow tagged `country-week` starts with Italy
  chosen for its account's next week through the API, the plugin turned off
  again (item 09). Next week is counted in the host's time zone, which the
  emulator takes as its own; a run across midnight on a Sunday could pick
  the wrong week.
- The web names the wheel "A wheel of 24: Peru, Japan, …" for a screen
  reader. Read aloud, 24 country names before the result is too much, so the
  app says "A wheel of 24", then where it stopped, as a polite live region
  (item 09).
- Maestro waits for the screen to settle after a tap, so the Spin tap takes
  about 4.9 s with the 3-second spin and 1.9 s with the system's animator
  duration scale at 0 (`adb shell settings put global
  animator_duration_scale 0`; `settings delete` puts it back). That is how
  item 09 checked that the wheel lands at once with animations off; no flow
  asserts it.
- The panel's toggle row carries its plugin's name and summary as its
  content description ("Country of the Week Italy", the flag left out as on
  the web), so a flow matches it by that text.
- Applying a Country of the Week card leaves a card on screen: the plugin
  then suggests the same dishes for the next free dinners, as on the web.
- The web reloads panels each time the plan page opens. The app loads them
  once per week, so turning a plugin on or off on the Plugins screen reloads
  the plan's panels and suggestions (`PlanModel.pluginsChanged`).
- The app puts "Add an item" below the aisles, where the web puts it above
  them: with the form first, a phone's first screen showed one item of the
  list (item 10).
- A ticked shopping item stays ticked on screen when the server refuses it,
  marked "Not sent" with a Retry; the web refreshes the list instead. Ticks
  go one at a time, so an item's last tap is the one the server keeps.
- Kotlin's `toDoubleOrNull` reads "2f", "2d", and hex floats, which
  JavaScript's `Number` does not, so the add-an-item form checks the typed
  amount against a plain decimal pattern first; `shopping.json` pins "2f".
- Olives come out in the "Other" aisle, from the catalog's category, not the
  app's.
