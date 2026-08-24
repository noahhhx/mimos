import { createClient, createConfig } from "@mimos/api-client";

import { userManager } from "./auth";
import { apiBaseUrl } from "./config";

/**
 * The one configured instance of the generated API client: every call goes
 * through it, with the current OIDC access token attached (ADR-0004).
 */
export const apiClient = createClient(
  createConfig({
    baseUrl: apiBaseUrl,
    fetch: async (input, init) => {
      const user = await userManager.getUser();
      const headers = new Headers(init?.headers);
      if (user?.access_token) {
        headers.set("Authorization", `Bearer ${user.access_token}`);
      }
      return globalThis.fetch(input, { ...init, headers });
    },
  }),
);
