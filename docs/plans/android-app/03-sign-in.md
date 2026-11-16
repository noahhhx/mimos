# 03. Sign-in and sign-up

**Depends on:** 02

A person signs in to their instance in the browser and stays signed in, as
on the web (ADR-0024).

## Build

- `deploy/keycloak/mimos-realm.json`: client `mimos-mobile`, public,
  standard flow only, PKCE S256 required, redirect URI
  `io.github.noahhhx.mimos:/oauth2redirect`, no web origins.
- `docs/self-hosting.md`: "Add the Android app client to an existing
  instance", the admin-console steps, as for `mimos-agent`.
- `androidMain`: AppAuth-Android for authorization (discovery from the
  issuer found in item 02), the redirect activity for the scheme, and
  token refresh. Check that its browser selection works with no GMS and
  with a browser that lacks Custom Tabs.
- Token storage behind a `commonMain` interface; the Android side
  encrypts the refresh token under an Android Keystore AES-GCM key and
  keeps it in DataStore. No offline tokens. A refresh that fails with
  `invalid_grant` signs the person out with a message, never a crash loop.
- Create account sends `prompt=create`; both send `mimos_theme`.
- Signed-out screen: Sign in and Create account, the instance's address,
  and Change instance. Sign out ends the Keycloak session
  (end-session endpoint) and forgets tokens.
- The first signed-in screen is a stand-in Kitchen home that calls
  `GET /api/v1/me` and greets the person.

## Checks

- `commonTest`: the session state machine (signed out, signing in,
  signed in, refreshing, expired) against a fake token source.
- `AgentSignInTests`-style test in `apps/api` or a Keycloak
  Testcontainers test that the realm's `mimos-mobile` client accepts
  Authorization Code with PKCE and refuses the password grant.
- Manual on the emulator until item 05: sign in as `test`, kill the app,
  reopen it still signed in.
- Gates: mobile, backend.
