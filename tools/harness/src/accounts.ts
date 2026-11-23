import { randomBytes } from "node:crypto";

import { HarnessError } from "./args.ts";
import { endpoints, FRESH_PASSWORD, keycloakAdmin, REALM } from "./config.ts";
import { isJson, send } from "./http.ts";

/**
 * Accounts for flows that cannot create their own: a Maestro flow has no way
 * to call Keycloak's admin API or the household endpoints. Keycloak admin
 * calls follow `ApiIntegrationTestSupport.createUser()`.
 */

export function freshUsername(random: () => string = () => randomBytes(4).toString("hex")): string {
  return `user-${random()}`;
}

/** The Keycloak user representation `createUser()` posts: verified email, a permanent password. */
export function userRepresentation(username: string): Record<string, unknown> {
  return {
    username,
    enabled: true,
    email: `${username}@example.com`,
    emailVerified: true,
    firstName: "Fresh",
    lastName: "Cook",
    credentials: [{ type: "password", value: FRESH_PASSWORD, temporary: false }],
  };
}

/** `KEY=value` lines, for `maestro test -e KEY=value` and for `export`. */
export function envLines(values: Readonly<Record<string, string>>): string {
  return Object.entries(values)
    .map(([key, value]) => `${key}=${value}`)
    .join("\n");
}

function jsonBody(value: unknown): { body: Buffer; headers: Record<string, string> } {
  const body = Buffer.from(JSON.stringify(value));
  return { body, headers: { "Content-Type": "application/json", "Content-Length": String(body.length) } };
}

async function adminToken(): Promise<string> {
  const { keycloak } = endpoints();
  const { username, password } = keycloakAdmin();
  const body = Buffer.from(
    new URLSearchParams({ grant_type: "password", client_id: "admin-cli", username, password }).toString(),
  );
  const response = await send(
    "POST",
    new URL(`${keycloak}/realms/master/protocol/openid-connect/token`),
    { "Content-Type": "application/x-www-form-urlencoded", "Content-Length": String(body.length) },
    body,
  );
  const parsed = isJson(response.headers["content-type"])
    ? (JSON.parse(response.body) as { access_token?: string })
    : {};
  if (response.status !== 200 || !parsed.access_token) {
    throw new HarnessError(
      `Keycloak admin token request failed: ${response.status} (KEYCLOAK_ADMIN_USERNAME and KEYCLOAK_ADMIN_PASSWORD must match the stack's)`,
    );
  }
  return parsed.access_token;
}

/** Creates a realm user with a fresh name; returns the name (its password is FRESH_PASSWORD). */
export async function createUser(): Promise<string> {
  const username = freshUsername();
  const token = await adminToken();
  const { body, headers } = jsonBody(userRepresentation(username));
  const response = await send(
    "POST",
    new URL(`${endpoints().keycloak}/admin/realms/${REALM}/users`),
    { ...headers, Authorization: `Bearer ${token}` },
    body,
  );
  if (response.status !== 201) {
    throw new HarnessError(`creating ${username} failed: ${response.status} ${response.body.slice(0, 200)}`);
  }
  return username;
}

async function apiCall(token: string, method: string, path: string, value?: unknown): Promise<unknown> {
  const { body, headers } = value === undefined ? { body: undefined, headers: {} } : jsonBody(value);
  const response = await send(
    method,
    new URL(path, endpoints().api),
    { ...headers, Authorization: `Bearer ${token}` },
    body,
  );
  if (response.status >= 400) {
    throw new HarnessError(`${method} ${path} failed: ${response.status} ${response.body.slice(0, 300)}`);
  }
  return isJson(response.headers["content-type"]) ? JSON.parse(response.body) : undefined;
}

/**
 * The Monday after the week `today` is in, as the API names a week (YYYY-MM-DD).
 * In this machine's time zone, which the emulator takes as its own.
 */
export function nextMonday(today: Date): string {
  const date = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7) + 7);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * Has Country of the Week choose `country` for the week starting `monday`,
 * then turns the plugin off again. Off keeps the household's pseudonym, so
 * the plugin keeps the choice for when the flow turns it on (ADR-0013,
 * ADR-0017).
 */
export async function chooseCountry(token: string, monday: string, country: string): Promise<void> {
  const setting = "/api/v1/me/plugins/country-week";
  await apiCall(token, "PUT", setting, { enabled: true });
  await apiCall(token, "POST", `/api/v1/plans/${monday}/panels/country-week/actions`, { id: "choose", value: country });
  await apiCall(token, "PUT", setting, { enabled: false });
}

/** The owner invites and the member joins, both through the API. */
export async function joinHousehold(ownerToken: string, memberToken: string): Promise<void> {
  const invite = (await apiCall(ownerToken, "POST", "/api/v1/household/invites")) as { token?: string } | undefined;
  if (!invite?.token) throw new HarnessError("the API created no invite token");
  await apiCall(memberToken, "POST", "/api/v1/household/join", { token: invite.token });
}
