# ADR-0029: Native Android app

- Status: Accepted
- Date: 2026-11-16

## Context

The web app is in daily use by the owner's family, and the request they
make most is a phone app. `NORTHSTAR.md` listed native apps as a non-goal
"once the web product has proven its shape"; the owner judges it proven.
The app must reach parity with the web: every feature the web offers.

Constraints the app inherits:

- **Self-hosted parity.** The app talks to any Mimos instance, not one
  hosted URL, and needs nothing an instance does not already run.
- **No Google.** The owner rules out Google Play Services and the Play
  Store entirely. No library may need GMS at runtime, and the app is
  distributed outside any store.
- **No Mac.** iOS builds and simulators need macOS, which the project does
  not have. iOS is not built, but the design must not close it off.
- **Agents build it.** Agents need to drive the app on a virtual device
  from this Linux machine and from CI, as they drive the web through the
  harness.

## Decision

- **Android only, iOS kept possible.** The app is a Kotlin
  Multiplatform (KMP) project with one target, Android. Everything that is
  not a platform service lives in `commonMain`: the API client, state,
  screens, navigation, and theme. Platform services sit behind interfaces
  in `commonMain` with Android implementations in `androidMain`: sign-in,
  secure token storage, the camera and barcode reader, image picking and
  shrinking, files for export and import, and sharing. Adding iOS later
  means an `iosMain` for those services and a Mac to build on, not a
  rewrite. Code in `commonMain` cannot import `android.*`, so the compiler
  holds the line.
- **Kotlin and Compose Multiplatform.** UI is Compose Multiplatform
  (Jetpack Compose on Android). HTTP is Ktor, JSON is
  kotlinx.serialization. The build is Gradle (Kotlin DSL) with its wrapper
  in `apps/mobile`, separate from the Maven reactor: Android's toolchain is
  Gradle-only, and the backend's Maven build stays untouched. This adds
  Kotlin and Gradle to the repo, approved by the owner.
- **Contract-first client.** The Kotlin API client is generated from
  `contracts/api/openapi.yaml` by openapi-generator's `kotlin` generator
  (`library=multiplatform`: Ktor and kotlinx.serialization) at build time,
  as the server stubs are, and not committed: unlike `@mimos/api-client`
  it has no consumer outside its own build. Never hand-written per feature
  (ADR-0003); the one exception is `PanelBlock`'s decoder, which needs an
  `Unknown` case.
- **The API changes additively.** Until now the API's only client shipped
  in step with it. A self-hosted server and an installed app drift apart,
  so from the first app release `/api/v1` only gains operations, fields,
  and enum values, and CI diffs the spec against the last release's. The
  app reads tolerantly: unknown fields are ignored and unknown enum values
  and panel blocks read as `Unknown`.
- **One address to connect.** A person types their instance's web
  address, the one they already open in a browser. The app reads the API
  URL, OIDC authority, and sign-up policy from the web's
  `/runtime-config.json` (a JSON twin of `/runtime-config.js`), then the
  server's version from a new public `GET /api/v1/public/instance`. The
  app keeps one instance at a time.
- **Sign-in through Keycloak.** A new public client `mimos-mobile`
  (Authorization Code + PKCE S256, no password grant) with the custom
  scheme redirect `io.github.noahhhx.mimos.oauth:/oauth2redirect`, via
  AppAuth-Android in the device's browser (Custom Tabs when the browser
  has them, which needs no GMS). The sign-in scheme is used for nothing
  else; app links such as invites use `io.github.noahhhx.mimos`. Sessions
  follow ADR-0024: the realm's lifetimes, no offline tokens. The refresh
  token is encrypted under an Android Keystore key and kept out of
  backups. Create account sends `prompt=create` (ADR-0020),
  and the `mimos_theme` parameter carries the app's theme to Keycloak's
  pages (ADR-0021). Realm import skips an existing realm, so existing
  instances add the client by hand, as they did `mimos-agent`.
- **No Google Play Services, enforced.** A Gradle check fails the build
  when the release runtime classpath holds any `com.google.android.gms`,
  `com.google.firebase`, `com.google.mlkit`, `com.google.android.play`, or
  `com.android.billingclient` artifact, and CI runs it. Barcodes are read
  by zxing-cpp (`io.github.zxing-cpp:android`, Apache-2.0) over CameraX,
  the engine the web's fallback already uses (ADR-0025). No push
  notifications, analytics, or crash reporting. Every dependency is free
  software, so an F-Droid build stays possible.
- **Evening Kitchen, faithfully.** The app mirrors the web's tokens
  (light by default, dark when chosen), straight edges, no animation except
  the week panel's wheel, and the web's committed fonts (ADR-0008). Tokens
  change in the web, Keycloak's theme, the docs, and the app together.
  User-facing copy follows the same rules (no em-dashes).
- **Phones first.** The plan is a list of days with an inspector, since
  the ledger (ADR-0028) does not fit a phone; tablets use the phone layout
  until a later item gives them the ledger. `minSdk` 26 (Android 8.0) so
  older family phones work.
- **Verification on a virtual device.** Logic is unit tested on the JVM
  from `commonTest`. Flows are Maestro YAML flows run on an Android emulator
  against the compose stack and its fakes. The emulator, `adb`, the Android
  SDK, and Maestro come from devenv and nixpkgs (`androidenv`, `maestro`),
  never ad-hoc installs. `harness mobile …` boots the emulator headless,
  installs the build, runs flows, and records screenshots, logcat, the
  Maestro report, and each API call's request ID in `.harness/runs/`.
  Agents explore with Maestro's MCP server (`maestro mcp`, in
  `.mcp.json`). CI builds and checks the APK without devenv, as every
  product path does, and runs every flow on an emulator (KVM on Linux
  runners) through devenv on every PR, as it runs the web scenarios.
- **Version skew is handled, not hoped away.** The instance endpoint
  answers the server's version (the release's, set from the tag in the
  image build) and the oldest app version it supports; the app tells the
  person when either side is too old, rather than failing on a call. Once a
  release includes the app's Keycloak client, CI also runs the flows
  against that release's published images.
- **Released as a signed APK on GitHub Releases.** A `v*` tag builds a
  release APK, signs it with the project's key (GitHub secrets, with an
  offline backup outside this repo), and attaches it and its SHA-256 to
  the tag's GitHub Release. The app's version is the tag's version, so
  app and server release together. Main builds attach a debug-signed APK
  (`.debug` application id, its own shared key) to the CI run for
  testers. No Play Store. People update by hand or with an updater that
  follows GitHub Releases (Obtainium). The app learns about a newer
  version from its own server, never by calling GitHub.
- **No Dockerfile for the app.** The APK is a client artifact like a
  browser, not a deployable; the "every deployable ships a Dockerfile"
  rule does not apply. `deploy/docker` and `deploy/selfhost` are
  unchanged, and the app needs nothing from them beyond what the web uses.

## Consequences

- The repo gains Kotlin, Gradle, and the Android SDK. devenv grows an
  `androidenv` composition (SDK, build tools, emulator, an x86_64 system
  image) and Maestro; CI gains an `android` job and an emulator job.
- The plugin boundary is unchanged: the app renders suggestion cards and
  week panels from the same declarative JSON the web does, so plugins work
  in the app without changing.
- Online only at first. Every screen needs the server, as the web does.
  An offline shopping list (ticks queued while the store has no signal) is
  its own later decision and plan item.
- Rules both clients apply (units, nutrition estimates, sorting) are tested
  from shared JSON cases in `libraries/test-cases/`, read by `node --test`
  and `commonTest`, so the two copies cannot drift unnoticed. Where the
  API can answer the value instead, it does (each meal's share).
- Invite links are web URLs on each instance's domain, which an app
  cannot claim without each instance hosting Android App Links
  verification. The web's join page offers "Open in the app" through the
  app's scheme instead, naming the instance and the token.
- Losing the signing key means every install must be removed and
  reinstalled. The key's backup is part of the release step.
- iOS later needs a Mac, an `iosMain` for the platform services, an Apple
  developer account, and a distribution decision; a new ADR.
- The plan is in `docs/plans/android-app/`.
