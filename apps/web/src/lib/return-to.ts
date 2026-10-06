/** Where a page asks sign-in to come back to; it travels through Keycloak as the OIDC state. */
export type SignInState = { returnTo: string };

/**
 * The in-app path sign-in returns to: the one the state names when it is
 * a page of the app, otherwise the Kitchen. The state comes back from the
 * browser's storage, so it is checked rather than trusted.
 */
export function returnPath(state: unknown): string {
  const returnTo =
    typeof state === "object" && state !== null && "returnTo" in state ? (state as SignInState).returnTo : null;
  if (typeof returnTo !== "string" || !/^\/app(\/|$)/.test(returnTo) || returnTo.startsWith("/app/callback")) {
    return "/app";
  }
  return returnTo;
}
