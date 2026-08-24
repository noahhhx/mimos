import { UserManager } from "oidc-client-ts";

import { appBaseUrl, oidcAuthority, oidcClientId } from "./config";

/**
 * The browser talks to Keycloak directly as the public `mimos-web` client
 * (Authorization Code + PKCE — see ADR-0004). Tokens live in the browser
 * (session storage) and are sent to the API as bearer tokens.
 */
export const userManager = new UserManager({
  authority: oidcAuthority,
  client_id: oidcClientId,
  redirect_uri: `${appBaseUrl}/app/callback`,
  post_logout_redirect_uri: `${appBaseUrl}/`,
  scope: "openid profile",
  automaticSilentRenew: true,
});
