# 01. Scaffold, toolchain, and build gates

**Depends on:** –

An empty app that builds on NixOS and in CI, with every gate the later
items rely on already enforced.

## Build

- `apps/mobile/`: Gradle (Kotlin DSL) with the wrapper committed, a
  version catalog (`gradle/libs.versions.toml`), and two modules:
  - `shared`: KMP, Android target only, `commonMain` and `commonTest`.
    Compose Multiplatform. Package `io.github.noahhhx.mimos`.
  - `androidApp`: the application (`applicationId
    io.github.noahhhx.mimos`, `minSdk` 26, current `targetSdk`), one
    activity hosting the shared UI. Debug builds add
    `applicationIdSuffix ".debug"`, so a debug and a release install live
    side by side, and allow cleartext HTTP to `localhost` only (network
    security config). Whether release builds allow cleartext is an open
    decision in the index; until it is settled they allow none.
- The Google Play Services ban: a Gradle task over the release runtime
  classpath that fails on any `com.google.android.gms`,
  `com.google.firebase`, `com.google.mlkit`, `com.google.android.play`,
  or `com.android.billingclient` group, wired into `check`. A test of the
  task adds a banned artifact to a test configuration and expects failure.
- Formatting: Spotless with ktfmt (kotlinlang style) on `check`, matching
  the backend's "formats on build, CI gates" convention.
- Android lint on `check`, warnings as errors.
- devenv, for agents and local work:
  - `devenv.yaml`: `allowUnfree: true` and the Android SDK licence
    accepted (`android_sdk.accept_license`), which the SDK needs.
  - `devenv.nix`: an `androidenv` composition (or devenv's `android`
    integration, if it can be pinned to an image without Google APIs):
    platform tools, build tools, the compile SDK, the emulator, and one
    x86_64 `default` AOSP system image. `ANDROID_HOME` and
    `ANDROID_SDK_ROOT` point at it. Plus `pkgs.maestro`.
  - `compileSdk` and build tools in Gradle equal the Nix SDK's exactly:
    Gradle cannot install into the store. One place states the versions,
    and AGENTS.md's "Dev environment" names them.
  - `android.aapt2FromMavenOverride` points at the SDK's `aapt2` (set by
    devenv in `gradle.properties` or an env var, never committed as a
    store path): the aapt2 AGP downloads from Maven does not run on NixOS.
- CI `android` job, not through devenv (AGENTS.md: product paths must not
  depend on it): `actions/setup-java` with JDK 21 and the runner's
  Android SDK, with the same compile SDK and build tools as devenv. It runs
  `./gradlew check assembleDebug` and uploads the APK.
- AGENTS.md: the layout tree, the verification list gains the Gradle
  command, "Containers" notes the APK is a client artifact with no
  Dockerfile, and "User-facing copy" covers the app's strings.

## Checks

- `devenv shell -- bash -c 'cd apps/mobile && ./gradlew check assembleDebug'`
  passes on NixOS.
- The ban task fails when `com.google.android.gms:play-services-base` is
  added, and passes once it is removed.
- CI's `android` job is green.
