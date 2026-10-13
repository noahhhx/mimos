# ADR-0024: Stay signed in

- Status: Accepted
- Date: 2026-10-13
- Amends: [ADR-0004](adr-0004-frontend-auth-in-the-browser.md) (tokens
  in session storage)

## Context

Someone running Mimos on a home server reported signing in on almost
every visit, where most sites keep them signed in for months. Two things
caused it:

- `oidc-client-ts` keeps its user in `sessionStorage` by default
  (ADR-0004), which belongs to one tab. A new tab, or the browser opened
  again, started signed out.
- The realm used Keycloak's session defaults: a session ends after 30
  minutes unused (SSO Session Idle) and 10 hours at most (SSO Session
  Max). The refresh token lives as long as the session, so an hour away
  ended it even in a tab left open.

## Decision

- **The web app keeps its user in `localStorage`.** `src/lib/auth.ts`
  gives the `UserManager` a `WebStorageStateStore` over
  `window.localStorage`, so every tab and a reopened browser share one
  sign-in. On the server, where nothing signs in, the library's in-memory
  default stays.
- **A realm session lasts 30 days unused and a year at most.** The realm
  export sets `ssoSessionIdleTimeout` to 2592000 and
  `ssoSessionMaxLifespan` to 31536000. A person signs in once per device;
  each visit renews the refresh token and restarts the 30 days.
- **Everyone gets it.** There is no "Remember me" choice. Staying signed in
  is what people expect of an app they use daily, and **Sign out** ends
  the session on a shared computer.

Alternatives considered:

- **Keycloak's Remember me.** It keeps short sessions unless the box is
  ticked, which is the behavior that was reported as the bug, and adds a
  choice to the sign-in form that most people would have to learn to make.
- **Offline tokens (`offline_access`).** They outlive the browser session
  without changing the realm's session lengths, but Keycloak does not end
  them on sign-out, so Sign out would leave a working token in the
  browser. Agents (ADR-0014) use them; a browser should not.
- **A server-side session (BFF).** ADR-0004 rejected it, and nothing here
  changes that: the problem was lifetimes and storage, not where tokens
  live.

## Consequences

- A refresh token stolen from the browser, by a script running on the web
  origin, works for up to 30 days unused instead of 30 minutes. Session
  storage was no defense against such a script either, since it could
  read the token there too; the longer lifetime is the real cost. Sign
  out, or ending the session in the Keycloak admin console, revokes it.
- Keycloak imports the realm only on the first start, so an existing
  instance keeps its old lengths until an admin sets them; the
  self-hosting guide says how. Everyone signs in once more after the
  upgrade, since the app no longer reads `sessionStorage`.
- The `stay-signed-in` harness scenario checks a new tab, a reopened
  browser (a new context with the old one's storage), and a refresh token
  that lives at least 30 days.
