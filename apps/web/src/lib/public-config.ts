/**
 * The browser-facing URLs the web app needs, read from the container's
 * environment at run time (ADR-0012) so one image serves any deployment.
 * The server reads them here; the browser gets them from
 * `/runtime-config.js`, which the root layout loads before any app code.
 */
export type PublicConfig = {
  /** The API as the browser reaches it. */
  apiUrl: string;
  /** The Keycloak realm as the browser reaches it (OIDC issuer). */
  oidcAuthority: string;
  /** The public OIDC client the browser signs in as. */
  oidcClientId: string;
  /** This app's own origin, for OIDC redirects. */
  appUrl: string;
};

/** The global `/runtime-config.js` sets in the browser. */
export const PUBLIC_CONFIG_GLOBAL = "__MIMOS_CONFIG__";

/** Defaults match the local compose stack. */
export const DEFAULT_PUBLIC_CONFIG: PublicConfig = {
  apiUrl: "http://localhost:8080",
  oidcAuthority: "http://localhost:8081/realms/mimos",
  oidcClientId: "mimos-web",
  appUrl: "http://localhost:3000",
};

export function publicConfigFromEnv(env: Record<string, string | undefined>): PublicConfig {
  return {
    apiUrl: url(env.MIMOS_API_URL) ?? DEFAULT_PUBLIC_CONFIG.apiUrl,
    oidcAuthority: url(env.MIMOS_OIDC_AUTHORITY) ?? DEFAULT_PUBLIC_CONFIG.oidcAuthority,
    oidcClientId: env.MIMOS_OIDC_CLIENT_ID?.trim() || DEFAULT_PUBLIC_CONFIG.oidcClientId,
    appUrl: url(env.MIMOS_APP_URL) ?? DEFAULT_PUBLIC_CONFIG.appUrl,
  };
}

/** The body of `/runtime-config.js`. */
export function runtimeConfigScript(config: PublicConfig): string {
  // JSON is valid JavaScript; escaping "<" keeps it inert even if inlined.
  const json = JSON.stringify(config).replace(/</g, "\\u003c");
  return `window.${PUBLIC_CONFIG_GLOBAL} = ${json};\n`;
}

/** Blank means unset; a trailing slash is dropped so paths join cleanly. */
function url(value: string | undefined): string | undefined {
  const trimmed = value?.trim().replace(/\/+$/, "");
  return trimmed || undefined;
}
