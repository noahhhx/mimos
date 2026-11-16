# 15. Release: signing, versions, GitHub Releases

**Depends on:** 02, 05

Signed APKs people can install and update, with no store.

## Build

- Signing: a release keystore created once by the owner, kept as GitHub
  Actions secrets (base64 keystore, store and key passwords, alias) and
  backed up in sops. Never in the repo. Document recovery and what losing
  it costs (every install reinstalled).
- Versions: `versionName` is the tag without `v`; `versionCode` is
  `major * 1_000_000 + minor * 1_000 + patch`, so it always increases.
  Main builds use the next version with a `-dev` suffix and the run
  number.
- CI `publish`: on a `v*` tag, after every other job passes, build the
  release APK (minified, with the ProGuard mapping kept as an artifact),
  sign it, and attach `mimos-<version>.apk` and its `.sha256` to the
  tag's GitHub Release. Pushes to main upload an unsigned debug APK as a
  workflow artifact only.
- Version handshake: `getInstance` (item 02) answers the server's
  version; the app shows "A newer app is available" when the server is
  newer, linking to the project's releases page, and refuses to run below
  `minAppVersion` with that link. The app never calls GitHub itself.
- Updates: document Obtainium as the updater that follows GitHub Releases.
- Reproducibility for a later F-Droid build: pin every dependency, keep
  builds free of timestamps, and add Gradle dependency verification
  (`verification-metadata.xml`). Check that two CI builds of one tag give
  the same unsigned APK, and record the result here.

## Checks

- A dry-run tag on a fork or a `workflow_dispatch` produces a signed APK
  that `apksigner verify` accepts and that installs over the previous
  build without uninstalling.
- `commonTest`: version comparison and the handshake's three outcomes.
- Gates: mobile.
