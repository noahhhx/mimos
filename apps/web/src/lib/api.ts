import { createClient, createConfig } from "@mimos/api-client";

import { authorizedFetch } from "./api-fetch";
import { userManager } from "./auth";
import { apiBaseUrl } from "./config";

/**
 * The one configured instance of the generated API client: every call goes
 * through it, with the current OIDC access token attached (ADR-0004) and a
 * fresh request ID.
 */
export const apiClient = createClient(
  createConfig({
    baseUrl: apiBaseUrl,
    fetch: authorizedFetch(async () => (await userManager.getUser())?.access_token),
  }),
);
