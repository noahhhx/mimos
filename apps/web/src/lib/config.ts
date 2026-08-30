/**
 * Public runtime configuration. NEXT_PUBLIC_* values are inlined at build
 * time; the compose stack passes them as build args (see apps/web/Dockerfile
 * and deploy/docker/compose.yml). Defaults match the local compose stack.
 */
export const apiBaseUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export const oidcAuthority = process.env.NEXT_PUBLIC_KEYCLOAK_AUTHORITY ?? "http://localhost:8081/realms/mimos";

export const oidcClientId = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? "mimos-web";

export const appBaseUrl = process.env.NEXT_PUBLIC_APP_URL ?? "http://localhost:3000";

/**
 * Base URL for server-side fetches (public SEO pages). Inside compose the
 * web container reaches the API on the internal network; a runtime env var
 * (API_SERVER_URL) — not a build arg — so a self-hoster can retarget it
 * without rebuilding. Falls back to the public URL for local dev.
 */
export const serverApiBaseUrl = process.env.API_SERVER_URL ?? apiBaseUrl;
