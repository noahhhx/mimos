import { createClient, createConfig } from "@mimos/api-client";

import { authorizedFetch } from "./api-fetch";
import { currentUser, userManager } from "./auth";
import { apiBaseUrl } from "./config";

/**
 * The one configured instance of the generated API client: every call goes
 * through it, with the current OIDC access token attached (ADR-0004) and a
 * fresh request ID. A token the API rejects ends the session.
 */
export const apiClient = createClient(
  createConfig({
    baseUrl: apiBaseUrl,
    fetch: authorizedFetch(
      async () => (await currentUser())?.access_token,
      () => userManager.removeUser(),
    ),
  }),
);
