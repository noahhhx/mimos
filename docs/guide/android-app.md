# The Android app

Mimos has an app for Android phones (Android 8.0 or later). It talks to the
same Mimos server as the web, so you sign in with the same account and see
the same recipes and plans. It is not in any app store, and it needs no
Google services.

## Install it

1. On your phone, open the
   [latest release](https://github.com/noahhhx/mimos/releases/latest) and
   download `mimos-<version>.apk`.
2. Open the downloaded file. Android asks you to allow installs from your
   browser the first time; allow it, then choose **Install**.
3. Open Mimos, type the address you open Mimos at in a browser, and choose
   **Connect**. Then sign in.

To check the download, compare it with the `.sha256` file beside it in the
release: `sha256sum -c mimos-<version>.apk.sha256` on a computer.

## Keep it up to date

The app and the server share one version number and are released
together. When your server runs a newer Mimos than the app, the app says
**A newer app is available** under its header. Choose **Get the new app**
to open the release, and install it over the one you have. You stay
signed in. **Not now** hides the notice until Mimos is closed and started again.

If your server has changed in a way an older app cannot follow, the app
shows **Update the app** and nothing else until you do.

To update without checking by hand, use an updater that follows GitHub
releases, such as [Obtainium](https://obtainium.imranr.dev/). Add the app
in Obtainium with the address `https://github.com/noahhhx/mimos`, and it
tells you when a new release is out and installs it. Release candidates
are marked as prereleases on GitHub; Obtainium skips them unless you turn
on **Include prereleases**.
