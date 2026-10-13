import { UserManager, WebStorageStateStore } from "oidc-client-ts";

import { appBaseUrl, oidcAuthority, oidcClientId } from "./config";
import { createSession } from "./session";

/**
 * The browser talks to Keycloak directly as the public `mimos-web` client
 * (Authorization Code + PKCE — see ADR-0004). Tokens live in localStorage,
 * so a new tab or a reopened browser stays signed in (ADR-0024), and are
 * sent to the API as bearer tokens.
 */
export const userManager = new UserManager({
  authority: oidcAuthority,
  client_id: oidcClientId,
  redirect_uri: `${appBaseUrl}/app/callback`,
  post_logout_redirect_uri: `${appBaseUrl}/`,
  scope: "openid profile",
  automaticSilentRenew: true,
  // Absent on the server, where nothing signs in; oidc-client-ts then keeps users in memory.
  userStore: typeof window === "undefined" ? undefined : new WebStorageStateStore({ store: window.localStorage }),
});

/** The signed-in user with a usable token, renewed if needed; null once the session is over. */
export const currentUser = createSession(userManager);
