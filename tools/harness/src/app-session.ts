import { createHash, randomBytes } from "node:crypto";

import { HarnessError } from "./args.ts";
import { endpoints, REALM } from "./config.ts";

/**
 * Signs a person in as the Android app would, without a browser: Authorization Code + PKCE as the
 * public `mimos-mobile` client through Keycloak's own login form, so a flow can start signed in
 * (the app's `HarnessSession`). The realm is unchanged; nothing here works without the password.
 */

export const APP_CLIENT_ID = "mimos-mobile";
export const APP_REDIRECT_URI = "io.github.noahhhx.mimos.oauth:/oauth2redirect";

export interface AppSession {
  refreshToken: string;
  idToken: string;
}

export function pkcePair(verifier = randomBytes(32).toString("base64url")): { verifier: string; challenge: string } {
  return { verifier, challenge: createHash("sha256").update(verifier).digest("base64url") };
}

const HTML_ENTITIES: Record<string, string> = { amp: "&", quot: '"', apos: "'", lt: "<", gt: ">", "#39": "'" };

/** The action of Keycloak's username-and-password form (`kc-form-login`, kept by the mimos theme). */
export function loginFormAction(html: string): string | undefined {
  const form = /<form\b[^>]*\bid="kc-form-login"[^>]*>/i.exec(html)?.[0];
  const action = form === undefined ? undefined : /\baction="([^"]+)"/i.exec(form)?.[1];
  return action?.replace(/&(amp|quot|apos|lt|gt|#39);/g, (_, name: string) => HTML_ENTITIES[name] ?? "");
}

/** The code in the redirect to the app, checked against the state the sign-in began with. */
export function codeFromRedirect(location: string | null, state: string): string {
  if (location === null || !location.startsWith(APP_REDIRECT_URI)) {
    throw new HarnessError(`Keycloak did not redirect to the app after the password: ${location ?? "no Location"}`);
  }
  const query = new URLSearchParams(location.slice(location.indexOf("?") + 1));
  if (query.get("state") !== state) throw new HarnessError("Keycloak's redirect to the app carried another state");
  const code = query.get("code");
  if (code === null) throw new HarnessError(`Keycloak's redirect to the app had no code: ${query.get("error") ?? location}`);
  return code;
}

const cookieHeader = (response: Response, jar: Map<string, string>): string => {
  for (const cookie of response.headers.getSetCookie()) {
    const [pair] = cookie.split(";");
    const at = pair?.indexOf("=") ?? -1;
    if (pair !== undefined && at > 0) jar.set(pair.slice(0, at).trim(), pair.slice(at + 1));
  }
  return [...jar].map(([name, value]) => `${name}=${value}`).join("; ");
};

export async function signInAsApp(username: string, password: string): Promise<AppSession> {
  const realm = `${endpoints().keycloak}/realms/${REALM}/protocol/openid-connect`;
  const { verifier, challenge } = pkcePair();
  const state = randomBytes(16).toString("base64url");
  const authorize = new URL(`${realm}/auth`);
  for (const [name, value] of Object.entries({
    client_id: APP_CLIENT_ID,
    redirect_uri: APP_REDIRECT_URI,
    response_type: "code",
    scope: "openid",
    state,
    code_challenge: challenge,
    code_challenge_method: "S256",
  })) {
    authorize.searchParams.set(name, value);
  }

  const jar = new Map<string, string>();
  const page = await fetch(authorize, { redirect: "manual" });
  const cookies = cookieHeader(page, jar);
  const action = loginFormAction(await page.text());
  if (page.status !== 200 || action === undefined) throw new HarnessError(`Keycloak's sign-in page for ${APP_CLIENT_ID} answered ${page.status} with no login form`);

  const login = await fetch(action, {
    method: "POST",
    redirect: "manual",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Cookie: cookies },
    body: new URLSearchParams({ username, password, credentialId: "" }),
  });
  const code = codeFromRedirect(login.headers.get("location"), state);

  const token = await fetch(`${realm}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "authorization_code", client_id: APP_CLIENT_ID, redirect_uri: APP_REDIRECT_URI, code, code_verifier: verifier }),
  });
  const body = (await token.json().catch(() => ({}))) as { refresh_token?: string; id_token?: string; error_description?: string; error?: string };
  if (!token.ok || body.refresh_token === undefined || body.id_token === undefined) {
    throw new HarnessError(`code exchange as ${APP_CLIENT_ID} failed: ${token.status} ${body.error_description ?? body.error ?? ""}`.trim());
  }
  return { refreshToken: body.refresh_token, idToken: body.id_token };
}
