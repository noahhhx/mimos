import { createClient, createConfig } from "@mimos/api-client";

import { serverApiBaseUrl } from "./config";

/**
 * A client instance for server components (the public SEO pages). It only
 * ever calls unauthenticated endpoints, so no token plumbing is needed.
 * Fetch caching: browsing the library is cached (ISR-ish); searches with a
 * `q` param stay fresh so results reflect the library immediately.
 */
export const publicApi = createClient(
  createConfig({
    baseUrl: serverApiBaseUrl,
    fetch: async (input, init) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      const isSearch = url.includes("q=");
      return globalThis.fetch(input, {
        ...init,
        cache: isSearch ? "no-store" : undefined,
        next: isSearch ? undefined : { revalidate: 600 },
      });
    },
  }),
);
