# 16. Release: versions, signing, GitHub Releases

**Depends on:** 03, 06

Signed APKs people install and update, with no store. Can land as soon as
its dependencies have, so testers get builds while features arrive.

## Build

- One version for server and app. A `v*` tag sets the Maven project
  version (`pom.xml` is `0.1.0-SNAPSHOT` and `application.yml`
  reports it) in the API image build, so `getInstance` answers the
  release's version, and sets the app's `versionName`. Main builds stay
  snapshots.
- `versionCode` = `major * 1_000_000 + minor * 10_000 + patch * 100 +
  pre`, where `pre` is the `-rc.N` number, or 99 for a final release,
  so a release candidate sorts before its release.
- Signing. A release keystore the owner creates once, kept as GitHub
  Actions secrets (base64 keystore, passwords, alias), with an offline
  backup in the owner's own secrets store (sops in `~/nixos`, never this
  repo). Losing it means every install is removed and reinstalled; this
  file documents recovery. A second, debug keystore, also a secret, signs
  main builds, so testers' `.debug` installs update over each other.
- CI `publish`: on a `v*` tag, after every other job passes, build the
  release APK (minified, the R8 mapping kept as an artifact), sign it, and
  attach `mimos-<version>.apk` and its `.sha256` to the tag's GitHub
  Release. Pushes to main attach a debug-signed APK to the workflow run.
- Version handshake, using item 03's `getInstance`: the app shows "A
  newer app is available" when the server is newer, linking to the
  releases page, and stops with that link below `minAppVersion`. The app
  never calls GitHub itself.
- Updates: document Obtainium as the updater that follows GitHub Releases.
- Version skew in CI: once a release includes `mimos-mobile`, a step on
  main runs the flows against that release's published images, creating
  users through the admin API (published Keycloak images have none).

## Checks

- A `workflow_dispatch` dry run produces a signed APK that `apksigner
  verify` accepts and that installs over the previous build.
- `commonTest`: version parsing, `versionCode` order, and the
  handshake's outcomes.
- Gates: mobile.
