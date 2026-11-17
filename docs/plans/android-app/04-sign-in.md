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

## Done

Landed on `docs/mobile-app-plan`. A person signs in or creates an account
in the phone's browser and stays signed in across restarts. Verified on
the emulator against the compose stack (screenshots under
`.harness/runs/*-mobile-shot/mobile/`): connect to `http://localhost:3000`,
Sign in as `test`, "Signed in as test" (a `getMe` call with the bearer
token), force-stop and relaunch (still signed in), Sign out (the browser
session ends: signing in again asks for the password), sign in again, and
Create account reaching Keycloak's Register page. A back press from the
browser returns to the signed-out screen, also after a rotation.

### What was built

- **Realm and docs.** Client `mimos-mobile` in `mimos-realm.json`, and
  `MobileSignInTests` (Testcontainers Keycloak, as `AgentSignInTests`):
  Authorization Code with PKCE and the custom-scheme redirect, a refresh
  that renews, the end-session endpoint revoking the session, every other
  redirect URI refused, PKCE required, and the password grant refused.
  `docs/self-hosting.md` has "Add the Android app client to an existing
  instance".
- **`commonMain` `session` package.** `SessionModel` is the one state
  machine (`SignedOut(notice)`, `SigningIn`, `Refreshing`, `Unreachable`,
  `SignedIn`), over an `Authorizer` and a `TokenStore`. It runs its work in
  a scope it is given, so a sign-in in flight outlives the screen, and a
  result applies only if the state it began from still holds (compare and
  set), so a sign-out during a refresh wins. `accessToken()` is the bearer
  source for `MimosHttp`: it refreshes 30 seconds before expiry, one
  refresh at a time (a `Mutex`, re-checked inside it). 21 `commonTest`
  cases cover it against fakes.
- **`AppModel`** holds the `ConnectModel` and the connected instance's
  `SessionModel`, and `AppViewModel` (androidApp) holds the `AppModel`, so
  models survive a rotation (the "Found along the way" item below, now
  done). Connecting launches in that scope too. The screens
  (`SessionScreen`) are plain foundation, as `ConnectScreen`; item 05
  restyles them.
- **androidApp.** `AppAuthAuthorizer` (AppAuth-Android 0.11.1: discovery,
  the authorization request, the code exchange, refresh, the end-session
  request), `BrowserLauncher` (carries AppAuth's intents to whichever
  activity instance is current and the browser's answer back),
  `KeystoreTokenStore` (an Android Keystore AES-GCM key, non-exportable,
  one sealed value per token with its name as associated data, in
  DataStore), `LocalhostHttpConnectionBuilder` (debug builds only),
  `data_extraction_rules.xml` and `backup_rules.xml` excluding `datastore/`.
  `checkNoPlayServices` passes with AppAuth, DataStore and `androidx.browser`
  on the classpath.

### Choices beyond the brief

- **`Unreachable`.** When the launch refresh gets no answer, there is no
  access token to show a signed-in screen with, so the model keeps the
  stored session and shows "Try again" and "Sign out". Only
  `invalid_grant` forgets it. During a session, an unreachable server keeps
  the person signed in and the call goes out with the old token.
- **Sign-out order.** The state becomes `SignedOut` and the store is cleared
  first, then the end-session request opens the browser. A kill during the
  browser step then cannot leave a refresh token behind. A person with no
  id token to hint with gets a local sign-out and no browser step (Keycloak
  would show a confirmation page).
- **Change instance** signs out (ending the session) and disconnects.
- **A refresh response without a refresh token** keeps the old one, and an
  access token with no expiry reads as already expired.

### What broke, and the fixes

1. **Cromite blocked the redirect to the app.** The sign-in page submitted
   and sat there; the browser's console said "Navigation is blocked:
   io.github.noahhhx.mimos.oauth:/oauth2redirect?…". Cromite's "Allow
   forward URL requests to external intents" is off by default. The harness
   now writes `--enable-features=AllowExternalIntentRequests` to the
   browser's command-line file (step 8 notes it). Cromite reads it at start,
   so a running browser needs a force-stop. A person who signs in with Cromite
   as their browser must turn that setting on.
2. **"Invalid ID Token".** AppAuth refuses an `http://` issuer when it
   validates the ID token. Debug builds set `setSkipIssuerHttpsCheck`
   alongside the localhost-only connection builder; release builds keep the
   check.
3. **`prompt` cannot be an additional parameter** (AppAuth throws, and the
   exception killed the app from the sign-in coroutine). Create account uses
   `setPrompt("create")`, and `AppAuthAuthorizer` now turns any exception
   into `Failed` (sign-in), `Unavailable` (refresh), or nothing (end
   session), so a failure shows a notice, never a crash.

### Browser selection

Cromite is the one browser on the emulator. Its sign-in tab showed an
address bar and a growing tab count, so AppAuth opened it as an ordinary
tab, not a Custom Tab (inferred from the screenshots, not from AppAuth's
own record). That is the "browser without Custom Tabs" path, and it works.
No Google Play Services were involved at any point. A browser with Custom
Tabs was not tried: the emulator has none.

### Left for later

- Each sign-in opens a new Cromite tab and leaves it open (nine after this
  session's runs). A Custom Tab would close it. Not a problem for people.
- If Android kills the app while the browser is open, the answer finds no
  one waiting and the person taps Sign in again.
- The theme sent as `mimos_theme` is the `AUTH_THEME` constant (`light`)
  until item 05's toggle replaces it.
