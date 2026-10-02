import { DEFAULT_PUBLIC_CONFIG, PUBLIC_CONFIG_GLOBAL, publicConfigFromEnv, type PublicConfig } from "./public-config";

/**
 * Public runtime configuration (ADR-0012). The server reads the container's
 * environment (MIMOS_API_URL, MIMOS_OIDC_AUTHORITY, MIMOS_OIDC_CLIENT_ID,
 * MIMOS_APP_URL); the browser reads what `/runtime-config.js` set, which
 * the root layout loads before any app code. Changing a URL means
 * restarting the container, not rebuilding the image.
 */
function readPublicConfig(): PublicConfig {
  if (typeof window === "undefined") {
    return publicConfigFromEnv(process.env);
  }
  const fromServer = (window as unknown as Record<string, PublicConfig | undefined>)[PUBLIC_CONFIG_GLOBAL];
  return fromServer ?? DEFAULT_PUBLIC_CONFIG;
}

const config = readPublicConfig();

export const apiBaseUrl = config.apiUrl;

export const oidcAuthority = config.oidcAuthority;

export const oidcClientId = config.oidcClientId;

export const appBaseUrl = config.appUrl;

/**
 * Base URL for server-side fetches (public SEO pages). Inside compose the
 * web container reaches the API on the internal network (API_SERVER_URL),
 * which may differ from the browser-facing URL. Falls back to the public
 * URL for local dev.
 */
export const serverApiBaseUrl = process.env.API_SERVER_URL ?? apiBaseUrl;
