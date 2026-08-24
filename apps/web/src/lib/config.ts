/**
 * Public runtime configuration. NEXT_PUBLIC_* values are inlined at build
 * time; the compose stack passes them as build args (see apps/web/Dockerfile
 * and deploy/docker/compose.yml). Defaults match the local compose stack.
 */
export const apiBaseUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export const oidcAuthority = process.env.NEXT_PUBLIC_KEYCLOAK_AUTHORITY ?? "http://localhost:8081/realms/mimos";

export const oidcClientId = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? "mimos-web";

export const appBaseUrl = process.env.NEXT_PUBLIC_APP_URL ?? "http://localhost:3000";
