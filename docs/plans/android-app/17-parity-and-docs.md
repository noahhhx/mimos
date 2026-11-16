# 17. Parity audit and user guide

**Depends on:** 07–16

Proof that the app does what the web does, and the pages that tell people
how to get it.

## Build

- Run item 06's parity script: every API operation the web calls is called
  by the app, or listed here with the reason. Walk the web's routes against
  the app's screens the same way. Known exclusions: the signed-out public
  library pages (`/recipes`), which serve search engines.
- User guide: `docs/guide/android.md` (install the APK, check its
  SHA-256, connect with the web address, update with Obtainium), with
  emulator screenshots at a phone size, light theme. Link it from the
  web's home page and the docs nav.
- `docs/self-hosting.md`: the `mimos-mobile` client for existing
  instances (from item 04), and what the app needs from the instance's URLs.
- NORTHSTAR, ROADMAP, and AGENTS.md's Mobile row say the app shipped.

## Checks

- The parity script's difference is empty or fully explained.
- `devenv shell -- mkdocs build --strict`.
- Every flow green in CI.
