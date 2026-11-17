# Step 8 — A virtual device for agents

**Status:** done (2026-11-16) · [Back to the plan](index.md) · built as item 02 of
the [Android app plan](../plans/android-app/index.md)

## Goal

An agent can boot an Android emulator on this machine, install the debug
APK, reach the compose stack from it, and read the evidence, so every
later item of the app plan checks its work on a device
([ADR-0029](../decisions/adr-0029-android-app.md)).

## Using it

```sh
devenv shell -- harness up            # the stack, as always
devenv shell -- harness mobile up     # AVD, boot, browser, ports, build, install, launch
devenv shell -- harness mobile shot   # a screenshot into the run
devenv shell -- harness mobile logs   # logcat for the app's process
devenv shell -- harness mobile down   # stop the emulator
eval "$(devenv shell -- harness user create | sed 's/^/export /')"   # MIMOS_USER, MIMOS_PASSWORD
devenv shell -- harness household join <owner> <member>
```

The [reference](usage.md#the-emulator--step-8) has every option.
`mobile up` writes `mobile/` into its run: `probe.txt`, `build.log`,
`up.png`, `emulator.log`, and `summary.md` with one line per step.

## Design notes

### The AVD is written, not made

`harness mobile up` writes `<name>.ini` and `config.ini` itself
(`src/avd.ts`) from a fixed Pixel 6 profile (1080x2400 at 420 dpi, 2 GB,
4 cores, software GPU) into `.harness/android/avd/`. The AVD is named
`mimos-pixel` and runs on console port 5554, so its serial is always
`emulator-5554`. Reasons:

- **Deterministic.** The same bytes every time; a unit test pins the
  profile.
- **No avdmanager.** It needs Java, and under a relocated
  `ANDROID_USER_HOME` it prints "Could not load devices from devices.xml"
  before writing the AVD anyway. Its output also carries dozens of
  generated keys nothing here reads.
- **Contained.** `ANDROID_AVD_HOME` and `ANDROID_USER_HOME` point under
  `.harness/android/`, so nothing lands in `~/.android`.

The image is the only one devenv installs, `system-images;android-36;default;x86_64`
(AOSP, userdebug). It boots in about 10 s to `sys.boot_completed` on this
machine, headless with `-no-window -no-audio -gpu swiftshader_indirect
-no-snapshot -no-boot-anim`. `-no-snapshot` gives a clean boot every time;
userdata still persists in the AVD folder, so the browser and the app stay
installed. To start over, delete `.harness/android/`.

### The browser: Cromite, pinned in devenv.nix

The `default` image ships no browser; only the WebView shell handles
`VIEW http`. Sign-in (item 04) runs in the device's browser. Cromite is
free software and Chromium-based, so Custom Tabs work. `devenv.nix`
fetches its APK with `pkgs.fetchurl` at a fixed release URL and hash, and
exports the path as `MIMOS_ANDROID_BROWSER_APK`. The harness installs it
from there on the first `mobile up` (it checks `pm path`, so a wiped
AVD gets it again) and never downloads anything at run time. Bump the URL
and hash together.

Chromium's first-run screens would block every flow, so `up` also writes
`_ --disable-fre --no-default-browser-check --no-first-run
--enable-features=AllowExternalIntentRequests` to
`/data/local/tmp/chrome-command-line` (mode 644; the file belongs to
`shell`, which is allowed there, so no `adb root`). Chromium reads it on a
userdebug image. It shows a one-line toast saying so. The last switch is
Cromite's "Allow forward URL requests to external intents", off by
default: without it Cromite logs "Navigation is blocked" for the redirect
to the app's own scheme (`io.github.noahhhx.mimos.oauth:`), the sign-in
tab never returns, and AppAuth waits forever (found in item 04). Cromite
reads it when it starts, so an old process needs a force-stop. Firefox (Fenix) was
rejected: its onboarding wall needs taps first.

### Reaching the stack: `adb reverse`

Tokens carry the issuer `http://localhost:8081/realms/mimos`, and the API
accepts only that issuer. A device cannot reach the host as `localhost`,
and `10.0.2.2` would give tokens a different issuer. So `up` runs
`adb reverse tcp:P tcp:P` for 8080, 8081, and 3000 (the ports from the
same variables as compose): the device's `localhost:P` is the host's.
The app and the device's browser then use the very URLs a desktop browser
does, `/runtime-config.json` needs no special case, the issuer matches,
and the debug build's cleartext permission for `localhost` alone (item 01)
is enough.

Reverse rules live in `adbd`, **tied to the adb server that made them**.
Restarting the server (`adb kill-server`, or a client of another version
making it restart) empties `adb reverse --list`, and the device then
refuses connections to `localhost`. This was reproduced: after
`kill-server`, the list was empty and a request from the device got
"Connection refused". The other failure looks the same from the device
but differs on the host: with the rules listed and nothing answering on
the host port (the web container stopped), the device connects, gets an
immediate close, and sees no HTTP response. So `up` clears and re-adds
the rules every time, and then proves them.

### Proving reachability: a real request from the device

A listed rule is not proof. `up` sends one HTTP request per service from
the device's own shell, and fails with exit 1 unless each answers below
400: the web app (`/`), the API (`/actuator/health`), and Keycloak's
realm (`/realms/mimos/.well-known/openid-configuration`). The probe is
`printf 'GET …' | nc 127.0.0.1 <port>` in `adb shell`, with a `sleep`
holding stdin open: toybox `nc` exits when its stdin ends, so a plain pipe
quits before the answer arrives (which made earlier probes look
unreliable). On failure, `summary.md` shows each probe, and
`mobile/adb.txt` has `adb version`, `adb devices -l`, and the rules as
they stood.

### Steps, not a script

`up` runs named steps (AVD, Emulator, Browser, Reverse ports, Reach the
stack, Build, Install and launch, Screenshot). Each records its outcome
and time; a failure stops the run, writes `summary.md` with the failing
step and the evidence so far, and exits 1. Every step converges: an
existing AVD is kept, a booted device is not rebooted, the browser is
installed only when missing, the rules are re-made, the app is
reinstalled (`install -r -t`) and relaunched. `down` runs `adb emu kill`
and waits for the device to leave the list; with nothing running it
succeeds. `--no-build` reuses the last APK.

The build is `./gradlew :androidApp:assembleDebug` in `apps/mobile`
(through devenv, which provides the SDK and the aapt2 override); the APK
is `androidApp/build/outputs/apk/debug/androidApp-debug.apk` and the
application id `io.github.noahhhx.mimos.debug`.

### Accounts

A flow cannot call Keycloak's admin API or the household endpoints, so two
commands do it:

- `harness user create` posts the same user representation as
  `ApiIntegrationTestSupport.createUser()` through the admin API
  (`admin-cli`, `KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD`,
  `admin` and `admin` in compose): `user-<8 hex>`, a verified email, and
  the password `mimos-test`. It prints `MIMOS_USER` and `MIMOS_PASSWORD`.
- `harness household join <owner> <member>` has the owner create an
  invite and the member join with it, through the API. It prints
  `MIMOS_OWNER`, `MIMOS_OWNER_PASSWORD`, `MIMOS_MEMBER`, and
  `MIMOS_MEMBER_PASSWORD`. The invite token is not printed or stored.

Token handling no longer stops at the fixed `USERS` map. `passwordOf` in
`src/config.ts` answers for the realm's test users and for any name that
matches `user-<8 hex>` (what `user create` makes, all with one local-only
password), so `token`, `api`, and `ui --as <user>` take them, and
`harness ui login --as user-…` signs one in on the web. Other names are
still a usage error rather than a failed login.

## Not in this step

- Maestro and `harness mobile flow` (item 06). Item 04 confirms sign-in
  works in Cromite.
- A pinned adb server port. If other tools start adb servers of another
  version on the default port, they will drop the reverse rules; `up`
  repairs that, but a flow run does not. Item 06 should check the rules
  before it runs a flow.
