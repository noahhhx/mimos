# Releasing

How a `v*` tag becomes published images and a signed APK, how the signing
keys are set up, and how CI tests the app against what people install and
run. The decision is [ADR-0029](../decisions/adr-0029-android-app.md);
the commands that reproduce CI's Android jobs are in [Using the
harness](usage.md#in-ci).

## Versions

Server and app share one version and release on the same tag.

- Gradle takes `-Pmimos.version`, which CI sets from a `v*` tag, or else
  the root `pom.xml`'s `<version>` (`mimosVersion` in `build-logic`). A
  build that is not a tag's is the backend's snapshot.
- The API image takes the tag's version as the `MIMOS_VERSION` build
  argument and sets it with `versions-maven-plugin` before packaging.
  `getInstance` drops a `-rc.N` as it drops `-SNAPSHOT`.
- `versionCode` is `major * 1_000_000 + minor * 10_000 + patch * 100 +
  pre`, where `pre` is the `-rc.N` number, 99 for a final release, and 0
  for a snapshot, so a candidate sorts before its release and a snapshot
  before the first candidate. `ReleaseVersion` in `build-logic` refuses
  what the formula cannot hold: a minor or patch above 99, a candidate
  outside 1 to 98, a major above 2099, or any other suffix.
- Testers on a release candidate are not told when the final release is
  out: the server reports `1.2.3` for `1.2.3-rc.N`, which equals the
  app's version. `releases/latest`, where the app's update notice links,
  skips prereleases.

## Signing keys

Keys come from the environment, never the repository:
`MIMOS_RELEASE_KEYSTORE` (a path) with `MIMOS_RELEASE_KEYSTORE_PASSWORD`,
`MIMOS_RELEASE_KEY_ALIAS`, and `MIMOS_RELEASE_KEY_PASSWORD`, and the same
four as `MIMOS_DEBUG_*` for the shared debug key, which signs main's debug
APK so testers' `.debug` installs update over each other. Without a
release key `assembleRelease` builds an unsigned APK; without a debug key,
debug builds use the SDK's own. CI fills them from Actions secrets, and
pull requests never see either key.

### Setting up once

The owner does this on their own machine. Nothing here goes in the
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

3. Back up both keystores and their passwords in the owner's offline
   secrets store (sops, outside this repository), then delete the loose
   copies.
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

### If the release key is lost

Android installs an update only when it is signed by the same key as the
installed app. Without the key nobody can update their install, and a new
key cannot be made to match.

1. Restore it from the offline backup if at all possible. That is the
   only recovery that keeps installs.
2. Otherwise, make a new key and replace the secrets as above, and say in
   the next release's notes that everyone must uninstall the app and
   install the new one. Uninstalling deletes the app's data, but nothing
   lives only in the app: people sign in again and their data is on their
   server.
3. An updater such as Obtainium cannot install it over the old app
   either; it works again once the old app is removed.

A leaked key is the same: rotate it, and tell people to reinstall.

## Cutting a release

Tag the commit on main and push the tag: `git tag v0.1.0 && git push
origin v0.1.0` (or `v0.1.0-rc.1` for a candidate). The tagged commit's
`pom.xml` may still say `0.1.0-SNAPSHOT`; the tag decides. CI's
`release-apk` runs once every other job passes: it uploads
`mimos-<version>.apk`, its `.sha256`, and the R8 mapping (kept 90 days) as
artifacts, creates the tag's GitHub Release (a prerelease for `-rc.N`,
notes generated), and attaches the APK and checksum, replacing them on a
rerun. `publish` waits for it, so images never announce an app that was
not released. A `workflow_dispatch` run does the same without a release.

To release without the Android flows, set the repository variable first:
`gh variable set RELEASE_WITHOUT_ANDROID_FLOWS --body true`. A `v*` tag
then skips `android-flows`, and `release-apk` and `publish` run once the
other jobs pass. Main and the pull requests that touch what the flows run
still run them. Use it only when the app's code has passed them already (a
green run on the same tree), and delete the variable afterwards: `gh
variable delete RELEASE_WITHOUT_ANDROID_FLOWS`.

Raise `MIN_APP_VERSION` in `PublicInstanceController` only for a change an
older app cannot survive.

## What CI runs against a release

**Flows on the minified build.** Release builds are shrunk by R8 with no
keep rules of the project's own (`androidApp/proguard-rules.pro` is
empty), so a dependency that needs one (reflection, serialization, a
service loader) would break the published APK while a debug build stays
green. `android-flows` therefore runs every flow on the `minified` build
type: `initWith` the release, not debuggable, with the debug build's
`.debug` id, debug key, and `src/debug/res`. It must not be debuggable:
R8 runs in its debug mode on a debuggable build and barely shrinks it
(measured at the time: 14,678 KB of dex against the release's 4,484 KB,
which the non-debuggable minified build matches exactly). It replaces the
debug build in CI rather than running beside it, which would double the
job's time; local runs keep the debug build, whose stack traces read as
written. The local-stack allowances (plain `http://localhost`, the
`MimosRequest` log, the component gallery) follow the `dev_build`
resource, true only in `src/debug/res`, rather than `FLAG_DEBUGGABLE`,
which the minified build does not set.

**Flows against the last release.** `android-skew` runs main's app
against the last `v*` release's published images, the server people still
run, on a push to main only. It gates nothing: a red run says a server
people still run breaks main's app. "The last release" is the last `v*`
tag reachable from HEAD, so a candidate counts as soon as it is tagged on
main. It runs only the flows the tag already had: a flow added since may
test what the release lacks, and gets its first run against the next
release. A flow the tag had that main changed to test something newer goes
in the step's `KNOWN_FAILING` until then. The fakes come from main,
because main's flows expect main's fixtures.
