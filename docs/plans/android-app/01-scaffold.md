# 01. Scaffold, toolchain, and build gates

**Depends on:** –

An empty app that builds, from devenv and in CI, with every gate the later
items rely on already enforced.

## Build

- `apps/mobile/`: Gradle (Kotlin DSL) with the wrapper committed, a
  version catalog (`gradle/libs.versions.toml`), and two modules:
  - `shared`: KMP, Android target only, `commonMain` and `commonTest`.
    Compose Multiplatform. Package `io.github.noahhhx.mimos`.
  - `androidApp`: the application (`applicationId
    io.github.noahhhx.mimos`, `minSdk` 26, current `targetSdk`), one
    activity hosting the shared UI. Debug builds allow cleartext HTTP to
    `10.0.2.2` and `localhost` only (network security config); release
    builds allow none. Item 05 settles whether the device reaches the stack
  as `localhost` (through `adb reverse`) or `10.0.2.2`.
- The Google Play Services ban: a Gradle task over the release runtime
  classpath that fails on any `com.google.android.gms`,
  `com.google.firebase`, `com.google.mlkit`, `com.google.android.play`,
  or `com.android.billingclient` group, wired into `check`. A unit test of
  the task adds a banned artifact to a test configuration and expects it to
  fail.
- Formatting: Spotless with ktfmt (kotlinlang style) on `check`, matching
  the backend's "formats on build, CI gates" convention.
- Android lint on `check`, warnings as errors for new code.
- `devenv.nix`: an `androidenv` composition (platform tools, build tools,
  the compile SDK, emulator, one x86_64 system image without Google APIs,
  the `default` AOSP image), `ANDROID_HOME` and `ANDROID_SDK_ROOT`
  pointing at it, the Android licence accepted in Nix config, and
  `pkgs.maestro`. JDK 21 already present.
- CI: an `android` job running `./gradlew check assembleDebug` with the
  same SDK versions, uploading the debug APK as an artifact.
- AGENTS.md: the layout tree gains `apps/mobile`, the verification list
  gains the gradle command, and "Containers" notes the APK is a client
  artifact with no Dockerfile (ADR-0029).

## Checks

- `devenv shell -- bash -c 'cd apps/mobile && ./gradlew check assembleDebug'`
  passes on NixOS; the APK installs on an emulator and opens a blank
  Evening Kitchen-coloured screen.
- The ban task fails when `com.google.android.gms:play-services-base` is
  added, and passes once it is removed.
- CI's `android` job is green.
