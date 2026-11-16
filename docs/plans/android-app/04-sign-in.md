# 04. Sign-in and sign-up

**Depends on:** 02, 03

A person signs in to their instance in the browser and stays signed in, as
on the web (ADR-0024).

## Build

- `deploy/keycloak/mimos-realm.json`: client `mimos-mobile`, public,
  standard flow only, PKCE S256 required, redirect URI
  `io.github.noahhhx.mimos.oauth:/oauth2redirect`, the same as the post
  logout redirect URI (`post.logout.redirect.uris`), no web origins.
- `docs/self-hosting.md`: "Add the Android app client to an existing
  instance", the admin-console steps, as for `mimos-agent`.
- `androidMain`: AppAuth-Android for authorization and refresh, using the
  authority from `runtime-config.json`.
  - The sign-in redirect has its own scheme, `io.github.noahhhx.mimos.oauth`
    (AppAuth's `appAuthRedirectScheme`). AppAuth claims the whole scheme,
    so app links such as invites (item 14) use the separate
    `io.github.noahhhx.mimos` scheme.
  - AppAuth accepts only HTTPS by default. Debug builds use a connection
    builder that also allows `http://localhost`, for the emulator and CI.
  - Check its browser selection works with no GMS and with a browser that
    lacks Custom Tabs. AppAuth-Android releases rarely; if it does not work,
    the fallback is `androidx.browser` and our own PKCE and token calls.
- Token storage behind a `commonMain` interface: the Android side
  encrypts the refresh token under an Android Keystore AES-GCM key and
  keeps it in DataStore. The file is excluded from backups
  (`dataExtractionRules`), because a restored file cannot be opened
  without its Keystore key; a token that does not decrypt reads as signed
  out. No offline tokens. A refresh that fails with `invalid_grant` signs
  the person out with a message.
- Create account sends `prompt=create`; both send `mimos_theme`. Create
  account shows only when `runtime-config.json` says sign-up is open.
- Signed-out screen: Sign in, Create account, the instance's address, and
  Change instance. Sign out ends the Keycloak session (end-session
  endpoint) and forgets the tokens.
- After sign-in the app shows the bare shell; the Kitchen home is item 13.

## Checks

- `commonTest`: the session state machine (signed out, signing in,
  signed in, refreshing, expired, unreadable token) against a fake token
  source.
- A Keycloak Testcontainers test in `apps/api` (as `AgentSignInTests`)
  that `mimos-mobile` takes Authorization Code with PKCE and refuses the
  password grant.
- On the emulator (item 02), by hand until item 06: sign in as `test`,
  stop the app, reopen it still signed in; sign out and back in.
- Gates: mobile, backend.
