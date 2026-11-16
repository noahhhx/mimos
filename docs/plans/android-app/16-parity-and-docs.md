# 16. Parity audit and user guide

**Depends on:** 06–15

Proof that the app does what the web does, and the pages that tell people
how to get it.

## Build

- Run item 05's parity script: every API operation the web calls is called
  by the app, or listed here with the reason it is not (for example
  operations only agents use). Walk the web's routes against the app's
  screens the same way.
- User guide: `docs/guide/android.md` (install the APK, verify its
  checksum, connect with the QR code, update with Obtainium), screenshots
  from the emulator in `docs/assets/screenshots/` at a phone size, light
  theme. Link it from the web's home page and the docs nav.
- `docs/self-hosting.md`: the `mimos-mobile` client for existing
  instances (from item 03), and that an instance needs HTTPS for the app
  in release builds.
- NORTHSTAR and ROADMAP updated to say the Android app shipped.

## Checks

- The parity script's difference is empty or fully explained.
- `devenv shell -- mkdocs build --strict`.
- Every flow green in CI.
