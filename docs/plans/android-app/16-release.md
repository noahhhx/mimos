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

## As built

- The version has one source. Gradle takes `-Pmimos.version`, which CI
  sets from a `v*` tag, or else the root `pom.xml`'s own `<version>`
  (`mimosVersion` in `build-logic`), so a build that is not a tag's is the
  backend's snapshot, `0.1.0-SNAPSHOT` today. The API image takes the
  tag's version as the `MIMOS_VERSION` build argument and sets it with
  `versions-maven-plugin` before packaging; `getInstance` drops the
  `-rc.N`, as it drops `-SNAPSHOT`.
- `ReleaseVersion` in `build-logic` reads a version and makes its
  `versionCode`. A snapshot's `pre` is 0, so it sorts before the first
  candidate. It refuses anything the formula cannot hold: a minor or patch
  above 99, a candidate outside 1 to 98, a major above 2099, or any other
  suffix. Its tests are in `build-logic`, which `./gradlew check` runs,
  rather than `commonTest`, because the build computes the code.
- Release builds are minified and resource-shrunk by R8 with no keep rules
  of the project's own (`androidApp/proguard-rules.pro` is empty): every
  flow passed on a debug build minified with the same rules. That proved
  little: R8 barely shrinks a debuggable build. Item 19 runs CI's flows on
  a non-debuggable minified build instead.
- Keys come from the environment, never the repository:
  `MIMOS_RELEASE_KEYSTORE` (a path) with `MIMOS_RELEASE_KEYSTORE_PASSWORD`,
  `MIMOS_RELEASE_KEY_ALIAS`, and `MIMOS_RELEASE_KEY_PASSWORD`, and the same
  four as `MIMOS_DEBUG_*` for the shared debug key. Without a release key,
  `assembleRelease` builds an unsigned APK; without a debug key, debug
  builds use the SDK's own.
- CI: `workflow_dispatch` runs every job, then `release-apk`, which uploads
  `mimos-<version>.apk`, its `.sha256`, and the R8 mapping (kept 90 days)
  as artifacts. On a `v*` tag it also creates the tag's GitHub Release
  (a prerelease for `-rc.N`, notes generated) and attaches the APK and
  checksum, replacing them on a rerun. On a push to main the `android` job
  signs the debug APK with the shared debug key. Pull requests never see
  either key.
- The handshake: each time the app opens a connected instance it asks
  `getInstance` again. A server newer than the app shows "A newer app is
  available" under the header until the person chooses Not now, which
  lasts until the app's process ends. The check runs when the instance's
  screens are first shown, not while the app sits in the background. An app below `minAppVersion` shows
  "Update the app" instead of anything else, with Get the new app and
  Change instance; the connect screen links to the same page when the
  server refuses the app at connect. The link is
  `https://github.com/noahhhx/mimos/releases/latest`.
- On a tag, `publish` waits for `release-apk`, so images never announce
  an app that was not released.
- Testers on a release candidate are not told when the final release is
  out: the server reports `1.2.3` for `1.2.3-rc.N`, which equals the app's
  version. `releases/latest` also skips prereleases, so before the first
  final release it has nothing to show.
- Version skew in CI is item [16b](16b-release-skew.md), split out
  because no release included `mimos-mobile` when 16 was built.

## Keys: setting up once

The owner does this once, on their own machine. Nothing here goes in the
repository.

1. Create the release key, then the shared debug key. Keytool writes
   PKCS12, which has one password for the store and the key, so the two
   password secrets get the same value.

   ```sh
   keytool -genkeypair -keystore mimos-release.jks -alias mimos \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Mimos"
   keytool -genkeypair -keystore mimos-debug.jks -alias mimos-debug \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Mimos debug"
   ```

2. Add them to the repository's Actions secrets:

   ```sh
   base64 -w0 mimos-release.jks | gh secret set MIMOS_RELEASE_KEYSTORE_BASE64
   gh secret set MIMOS_RELEASE_KEYSTORE_PASSWORD   # prompts
   gh secret set MIMOS_RELEASE_KEY_PASSWORD        # the same value
   gh secret set MIMOS_RELEASE_KEY_ALIAS --body mimos
   base64 -w0 mimos-debug.jks | gh secret set MIMOS_DEBUG_KEYSTORE_BASE64
   gh secret set MIMOS_DEBUG_KEYSTORE_PASSWORD
   gh secret set MIMOS_DEBUG_KEY_PASSWORD
   gh secret set MIMOS_DEBUG_KEY_ALIAS --body mimos-debug
   ```

3. Back up both keystores and their passwords in the sops store in
   `~/nixos`, then delete the loose copies.
4. Run the dry run: Actions, CI, Run workflow on `main`. Download the
   `mimos-<version>-apk` artifact and check it:

   ```sh
   sha256sum -c mimos-*.apk.sha256
   apksigner verify --print-certs mimos-*.apk
   adb install mimos-*.apk
   ```

   A dry run builds the root pom's snapshot version, signed with the
   release key, so install it on a test device only and uninstall it
   after: its code sorts below every release, and a phone with a real
   release refuses it as a downgrade.

## Cutting a release

Tag the commit on main and push the tag: `git tag v0.1.0 && git push
origin v0.1.0` (or `v0.1.0-rc.1` for a candidate). CI builds and
publishes the images and the APK only once every job passes. The tagged
commit's `pom.xml` may still say `0.1.0-SNAPSHOT`; the tag decides.

To release without the Android flows, set the repository variable first:
`gh variable set RELEASE_WITHOUT_ANDROID_FLOWS --body true`. A `v*` tag then
skips `android-flows`, and `release-apk` and `publish` run once the other
jobs pass. Pull requests and main still run the flows. Use it only when the
app's code has passed them already (a green run on the same tree), and
delete the variable afterwards: `gh variable delete
RELEASE_WITHOUT_ANDROID_FLOWS`.

Raise
`MIN_APP_VERSION` in `PublicInstanceController` only for a change an older
app cannot survive.

## If the release key is lost

Android installs an update only when it is signed by the same key as the
installed app. Without the key nobody can update their install, and a new
key cannot be made to match.

1. Restore it from the sops backup if at all possible. That is the only
   recovery that keeps installs.
2. Otherwise, make a new key and replace the secrets as above, and say in
   the next release's notes that everyone must uninstall the app and
   install the new one. Uninstalling deletes the app's data, but nothing
   lives only in the app: people sign in again and their data is on their
   server.
3. An updater such as Obtainium cannot install it over the old app
   either; it works again once the old app is removed.

A leaked key is the same: rotate it, and tell people to reinstall.
