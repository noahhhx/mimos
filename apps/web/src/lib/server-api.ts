import { createClient, createConfig } from "@mimos/api-client";

import { serverApiBaseUrl } from "./config";
import { newRequestId, REQUEST_ID_HEADER } from "./request-id";

/**
 * A client instance for server components (the public SEO pages). It only
 * ever calls unauthenticated endpoints, so no token plumbing is needed.
 * Fetch caching: browsing the library is cached (ISR-ish); searches with a
 * `q` param stay fresh so results reflect the library immediately.
 *
 * Request IDs: fresh (uncached) fetches send one. Cached fetches don't —
 * Next's fetch cache keys on request headers, so a per-call ID would make
 * every one a miss; the API generates their ID instead. A failed call is
 * logged with the ID either way, to match the API's log lines.
 */
export const publicApi = createClient(
  createConfig({
    baseUrl: serverApiBaseUrl,
    fetch: async (input, init) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      const method = init?.method ?? (input instanceof Request ? input.method : "GET");
      const isSearch = url.includes("q=");
      let headers: Headers | undefined;
      if (isSearch) {
        headers = new Headers(input instanceof Request ? input.headers : init?.headers);
        headers.set(REQUEST_ID_HEADER, newRequestId());
      }
      try {
        const response = await globalThis.fetch(input, {
          ...init,
          ...(headers ? { headers } : {}),
          cache: isSearch ? "no-store" : undefined,
          next: isSearch ? undefined : { revalidate: 600 },
        });
        if (!response.ok) {
          const id = response.headers.get(REQUEST_ID_HEADER) ?? headers?.get(REQUEST_ID_HEADER) ?? "none";
          console.warn(`[mimos-api] ${method} ${url} -> ${response.status} request-id=${id}`);
        }
        return response;
      } catch (error) {
        const id = headers?.get(REQUEST_ID_HEADER) ?? "none";
        console.error(`[mimos-api] ${method} ${url} -> no response request-id=${id}: ${String(error)}`);
        throw error;
      }
    },
  }),
);
