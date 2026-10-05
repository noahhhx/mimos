import { newRequestId, REQUEST_ID_HEADER } from "./request-id.ts";

/**
 * The `fetch` the browser's API client sends through: it adds the current
 * OIDC access token (ADR-0004) and a fresh request ID to each call.
 *
 * The generated client calls `fetch(request)` with a fully built `Request`
 * (its `Content-Type` included) and no `init`. Headers passed in `init`
 * replace the Request's own, so they must start from the Request's, or every
 * call with a body loses its media type and the API answers 415
 * (docs/harness/step-7-recipe-415.md).
 *
 * A 401 for a call that carried a token means the API no longer accepts
 * it (the session was revoked, or Keycloak's keys changed): `onUnauthorized`
 * runs so the app can sign out instead of failing every later call.
 */
export function authorizedFetch(
  accessToken: () => Promise<string | undefined>,
  onUnauthorized: () => Promise<void> = async () => {},
): typeof fetch {
  return async (input, init) => {
    const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined));
    const token = await accessToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
    if (!headers.has(REQUEST_ID_HEADER)) {
      headers.set(REQUEST_ID_HEADER, newRequestId());
    }
    const response = await globalThis.fetch(input, { ...init, headers });
    if (token && response.status === 401) {
      await onUnauthorized();
    }
    return response;
  };
}
