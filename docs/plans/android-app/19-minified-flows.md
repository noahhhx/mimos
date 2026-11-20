# 19. Flows on a minified build

**Depends on:** 06, 16

Found along the way in item 16: no CI job ran a minified build. Release
builds are shrunk by R8 with no keep rules of the project's own, so a
dependency update that needs one (reflection, serialization, a service
loader) would break the published APK while every job stayed green.

## Decided

- CI's `android-flows` runs every flow on a minified build, in place of the
  debug build, rather than beside it: running both would double the job's
  flow time, and the minified build is the one people install. Local runs keep the debug build, whose
  stack traces read as written.
- The minified build is not debuggable. Item 16 checked the release rules
  on a debug build with `isMinifyEnabled` on, but R8 runs in its debug mode
  on a debuggable build and barely shrinks it. Measured on this branch:
  the debug build's dex is 44,200 KB unminified and 14,678 KB minified
  that way, against 4,484 KB for the release. A non-debuggable build
  minified as the release is comes to the release's 4,484 KB exactly.
- The app's local-stack allowances (plain `http://localhost`, the request
  ID log, the component gallery) followed `FLAG_DEBUGGABLE`, which a
  non-debuggable build does not set. They now follow the `dev_build`
  resource: `false` in `src/main/res`, `true` in `src/debug/res`, which the
  minified build reads as well, beside the debug network security config.
  The release build has `dev_build` false and the main network config, as
  before.

## Built

- `androidApp/build.gradle.kts`: a `minified` build type, `initWith` the
  release (R8, resource shrinking, the same ProGuard files), with the debug
  build's `.debug` id suffix and debug signing key, and `src/debug/res` as
  a resource directory.
- `harness mobile up --minified` builds `:androidApp:assembleMinified` and
  installs it under the same id, so `harness mobile flow` runs on either
  build unchanged. `--no-build` with it reuses the last minified APK.
- CI: `android-flows` boots the emulator with `--minified` and, when a
  flow fails, uploads the R8 mapping with the harness evidence, so a stack
  trace can be read back with the SDK's `retrace`.

## Checks

- The minified APK installed on the emulator has no `DEBUGGABLE` flag
  (`dumpsys package`), and its dex is the release's size.
- Every flow passes on the minified build (`harness mobile up --minified`,
  then each `harness mobile flow`).
- `aapt2 dump resources` shows `dev_build` true in the debug and minified
  APKs and false in the release APK.
- Gates: mobile (`./gradlew check assembleDebug`), harness.
