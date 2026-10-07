# ADR-0020: Sign-up

- Status: Accepted
- Date: 2026-10-07
- Builds on: [ADR-0004](adr-0004-frontend-auth-in-the-browser.md)
  (sign-in in the browser), [ADR-0012](adr-0012-published-images.md)
  (runtime web config), [ADR-0019](adr-0019-households.md) (households
  own the data)

## Context

The realm export has had self-registration on (`registrationAllowed:
true`) from the start, but the web app only offered **Sign in**. A new
visitor had to find Keycloak's "New user? Register" link under the
sign-in form to create an account. The owner wants sign-up visible and
free now, and wants it shaped so it can become pay-to-sign-up later.

Keycloak 26 shows its registration form directly when an authorization
request carries `prompt=create` (the OIDC "Initiating User Registration"
extension). When the realm has registration turned off, the same request
returns 400 and a "Registration not allowed" page, with no way back to
the app. So the web app has to know when an instance closed sign-up.

## Decision

### Account creation is Keycloak self-registration

Every **Create account** button calls one function, `signUp(returnTo?)`
in `AuthProvider`. It is `signIn` plus `prompt=create`: oidc-client-ts
redirects to Keycloak's registration form, and after it Keycloak returns
through `/app/callback` to `returnTo` or the Kitchen, as sign-in does.
Keycloak creates the account. Mimos creates the profile and its household
of one on the account's first authenticated API request, as before
(ADR-0019).

The home page shows Create account first in its button row. Every page
under `/app` that a signed-out visitor can open shows one `SignInPrompt`:
the page's message, Sign in, and Create account. The invite page passes
its own path as `returnTo`, so someone who creates an account from an
invite lands back on it.

### Mimos decides whether to offer it; Keycloak enforces it

`MIMOS_SIGNUP` on the web container sets the `SignupPolicy`, `open` or
`closed`. It is runtime config like the URLs (ADR-0012): the server reads
it into `/runtime-config.js` and the browser reads it from there. Unset,
blank, or `open` in any case means open; any other value means closed,
because an operator who writes `off` or `false` means closed and the
wrong guess leads to the dead end above. Compose passes it as `SIGNUP`.

Closed only hides the button. Keycloak's realm setting is what stops
anyone registering, so an operator who closes sign-up sets both. Pages
are prerendered at build time, so `CreateAccountButton` renders nothing
on the server and reads the policy in the browser after hydration
(`useSyncExternalStore` with a server snapshot of "not offered").

### Identity and access stay separate

Keycloak answers "who is this?"; Mimos answers "what may they use?".
Pay-to-sign-up later keeps Keycloak registration as the way an account is
created and adds two things:

- A paid value of `SignupPolicy`, whose `signUp` goes through a Mimos
  checkout page as well as Keycloak's registration form. The buttons and
  pages do not change, because they all call `signUp`.
- An entitlement on the household, checked by the API. Households own
  recipes, plans, and lists (ADR-0019), so one subscription covers
  everyone in a household.

Payment is never a gate inside Keycloak (a custom registration flow or
SPI). That would put billing in the identity provider, tie it to
Keycloak's extension API, and leave the API unable to tell a lapsed
subscription from an active one.

## Alternatives considered

- **Always show Create account.** On an instance that turned
  registration off, the button leads to Keycloak's "Registration not
  allowed" page with no way back. Observed on Keycloak 26.5.
- **Link to Keycloak's `/protocol/openid-connect/registrations`
  endpoint.** It shows the same form, but it is Keycloak-specific and
  skips oidc-client-ts, so the app would build the authorization request
  and its PKCE state by hand. `prompt=create` is the OIDC standard and
  goes through the same `signinRedirect` as sign-in.
- **A `/signup` page in Mimos.** It would duplicate Keycloak's form and
  its validation. A page of our own makes sense when sign-up gains a
  checkout step, and `signUp` is where it goes.

## Consequences

- New visitors see Create account on the home page and on every
  signed-out page. Existing behavior for Sign in does not change.
- Self-hosters close sign-up with `SIGNUP=closed` and the realm's
  **User registration** switch together; `deploy/selfhost/.env.example`
  and the self-hosting guide say so.
- The harness's `register()` signs up through the home page's Create
  account, and the `household` scenario's guest signs up from the
  invite page, so CI covers both entry points.
- Not built: payment, entitlements, and any API change. A paid mode
  adds them as described above.
