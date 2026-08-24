# ADR-0004: Frontend authentication in the browser

- Status: accepted
- Date: 2026-08-24

## Context

Roadmap step 6 needs a Next.js frontend with a public (SSG) page and an
authenticated app shell that logs in through Keycloak and talks to the API
only through the generated client. The API (ADR step 4) is a pure bearer-token
resource server with CORS — it has no session, no server-side rendering of
authenticated views.

Two viable browser-OIDC shapes:

1. **Server-side sessions** (Auth.js/next-auth, or a BFF): the Next server
   holds tokens in an encrypted cookie and proxies API calls.
2. **Client-side public client** (`oidc-client-ts`): the browser runs the
   Authorization Code + PKCE flow directly against Keycloak, holds the tokens
   (session storage), and calls the API with `Authorization: Bearer`.

## Decision

The web app uses the **client-side public client** (`oidc-client-ts`,
client `mimos-web` from the realm export):

- **Shared client, no proxy.** The generated TypeScript client
  (`@mimos/api-client`, ADR-0003) runs in the browser and is exactly what a
  future mobile app would reuse — same public client, same PKCE flow, same
  bearer-token API. A BFF would fork that path.
- **No Next-magic.** No server session, no middleware auth, nothing that
  makes the app harder to host as a plain Node container or to reason about
  for a self-hoster. The app is a static-ish shell plus API calls; the API
  already exposes CORS for the web origin.
- **Self-host parity.** Keycloak's URL is injected at build time
  (`NEXT_PUBLIC_KEYCLOAK_*` build args in compose); tokens never touch the
  web server.

Concretely: `src/lib/auth.ts` configures the `UserManager` (code + PKCE,
automatic silent renewal via refresh token), `src/lib/api.ts` creates the one
generated-client instance with a `fetch` wrapper that attaches the current
access token, and `/app/*` sits behind an `AuthProvider` that prompts for
sign-in.

## Consequences

- Tokens live in the browser (session storage). That is standard for public
  clients, but it means the web app is not the place for long-lived or
  high-privilege sessions; anything sensitive later (admin surfaces, the
  plugin API) should get its own confidential-client/BFF treatment.
- The API must keep CORS configured for the web origin — it is part of the
  contract now (`MIMOS_SECURITY_CORS_ALLOWED_ORIGINS`).
- Logout is a Keycloak `end_session` redirect, not just clearing local state.
- `NEXT_PUBLIC_*` values are inlined at build time; changing the public
  Keycloak/API URLs means rebuilding the web image with different build args.
  Runtime injection can be added later if self-hosters need it without
  rebuilds.
- Server-side rendering of authenticated content (SEO for personal pages is
  not a product goal; library recipe pages stay public and SSG-able).
