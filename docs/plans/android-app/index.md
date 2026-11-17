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
| 04 | [Sign-in and sign-up](04-sign-in.md) | 02, 03 | todo |
| 05 | [Evening Kitchen in Compose](05-design-system.md) | 01 | todo |
| 06 | [Maestro flows and CI](06-flows-and-ci.md) | 04, 05 | todo |
| 07 | [Recipes and pictures](07-recipes.md) | 06 | todo |
| 08 | [The week's plan](08-plan.md) **Review** | 07 | todo |
| 09 | [Plugins: page, suggestions, week panels](09-plugins.md) | 08 | todo |
| 10 | [Shopping list](10-shopping-list.md) | 08 | todo |
| 11 | [Log and barcode scanning](11-log-and-barcodes.md) | 08 | todo |
| 12 | [Fuel and intervals.icu](12-fuel.md) | 11 | todo |
| 13 | [Kitchen home](13-kitchen.md) | 08, 09, 10 | todo |
| 14 | [Household and invites](14-household.md) | 06 | todo |
| 15 | [Account, settings, export and import](15-account.md) | 06 | todo |
| 16 | [Release: versions, signing, GitHub Releases](16-release.md) | 03, 06 | todo |
| 17 | [Parity audit and user guide](17-parity-and-docs.md) | 07–16 | todo |
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
  measures it), they move to main and tags only.

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
- The connect screen keeps its model in `remember`, so a connect in
  flight is lost when the phone rotates; the stored instance survives.
  Item 04 or 05 should hold screen models across configuration changes.
- The Kotlin client cannot read a binary body with `body()`
  (`getRecipePicture`); `.response.readRawBytes()` works. Multipart upload
  compiles but has not run against a server. Both are item 07's.
- `/runtime-config.json` names `mimos-web` as its OIDC client. Item 04
  signs in as `mimos-mobile`, so the app takes the authority from it and
  its own client id.
