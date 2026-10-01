import { HarnessError, parseCommandArgs, UsageError } from "../args.ts";
import type { Command } from "../command.ts";
import { DEFAULT_USER, endpoints, REALM, USERS } from "../config.ts";
import { isJson, send } from "../http.ts";

/** Access tokens via the password grant on the public `mimos-web` client (direct access grants are enabled in the realm). */

export function passwordFor(user: string): string {
  const password = USERS[user];
  if (password === undefined) {
    throw new UsageError(`unknown user ${user} — the realm's test users are: ${Object.keys(USERS).join(", ")}`);
  }
  return password;
}

export async function fetchToken(user: string): Promise<string> {
  const { keycloak, clientId } = endpoints();
  const url = new URL(`${keycloak}/realms/${REALM}/protocol/openid-connect/token`);
  const form = new URLSearchParams({ grant_type: "password", client_id: clientId, username: user, password: passwordFor(user) });
  const body = Buffer.from(form.toString());
  const response = await send("POST", url, {
    "Content-Type": "application/x-www-form-urlencoded",
    "Content-Length": String(body.length),
  }, body);
  const parsed = isJson(response.headers["content-type"])
    ? (JSON.parse(response.body) as { access_token?: string; error?: string; error_description?: string })
    : {};
  if (response.status !== 200 || !parsed.access_token) {
    const reason = parsed.error_description ?? parsed.error ?? response.body.slice(0, 200);
    throw new HarnessError(`token request for ${user} failed: ${response.status} ${reason}`);
  }
  return parsed.access_token;
}

/** Header and claims of a JWT — no signature check; a debugging aid only. */
export function decodeJwt(token: string): { header: unknown; claims: unknown } {
  const [header, claims] = token.split(".");
  if (header === undefined || claims === undefined) {
    throw new HarnessError("not a JWT");
  }
  const decode = (part: string): unknown => JSON.parse(Buffer.from(part, "base64url").toString("utf8"));
  return { header: decode(header), claims: decode(claims) };
}

export function parseTokenArgs(argv: string[]): { user: string; decode: boolean } {
  const { values } = parseCommandArgs({
    args: argv,
    options: { as: { type: "string" }, decode: { type: "boolean" } },
  });
  const user = values.as ?? DEFAULT_USER;
  passwordFor(user);
  return { user, decode: values.decode ?? false };
}

export const tokenCommand: Command = {
  name: "token",
  summary: "Print an access token for a test user (password grant)",
  usage: `harness token [--as <user>] [--decode]

  --as <user>   ${Object.keys(USERS).join(" | ")} (default ${DEFAULT_USER})
  --decode      print the JWT header and claims instead of the token (no signature check)

Prints to stdout only; tokens are never written to a run folder.
  curl -H "Authorization: Bearer $(harness token)" http://localhost:8080/api/v1/me`,
  async run(argv) {
    const { user, decode } = parseTokenArgs(argv);
    const token = await fetchToken(user);
    process.stdout.write(decode ? `${JSON.stringify(decodeJwt(token), null, 2)}\n` : `${token}\n`);
    return 0;
  },
};
