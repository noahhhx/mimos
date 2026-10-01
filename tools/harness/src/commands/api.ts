import { readFileSync } from "node:fs";

import { displayCommand, parseCommandArgs, parseHeader, UsageError } from "../args.ts";
import { fenced, finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { captureLogs, describeLogs, linesSince } from "../compose.ts";
import { DEFAULT_USER, endpoints, USERS } from "../config.ts";
import { isJson, mediaType, send } from "../http.ts";
import { redactBody, redactHeaders } from "../redact.ts";
import { appendJsonLine, appendSummary, runStartedAt } from "../run.ts";
import { fetchToken, passwordFor } from "./token.ts";

/** One recorded HTTP call to the API: request and response land in api/exchanges.jsonl, redacted. */

const METHODS = new Set(["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]);

export interface ApiArgs {
  method: string;
  /** A path on the API (`/api/v1/me`) or an absolute URL. */
  target: string;
  /** A file path, `-` for stdin, or undefined for no body. */
  body: string | undefined;
  /** In order; a later header with the same name (case-insensitive) wins, an empty value removes it. */
  headers: [string, string][];
  /** Undefined means anonymous. */
  user: string | undefined;
  run: string | undefined;
}

export function parseApiArgs(argv: string[]): ApiArgs {
  const { values, positionals } = parseCommandArgs({
    args: argv,
    allowPositionals: true,
    options: {
      body: { type: "string" },
      header: { type: "string", multiple: true, short: "H" },
      as: { type: "string" },
      anon: { type: "boolean" },
      ...RUN_OPTION,
    },
  });
  const [method, target, ...extra] = positionals;
  if (method === undefined || target === undefined || extra.length > 0) {
    throw new UsageError("expected <METHOD> <path>");
  }
  const upper = method.toUpperCase();
  if (!METHODS.has(upper)) {
    throw new UsageError(`unknown method ${method}`);
  }
  if (!target.startsWith("/") && !/^https?:\/\//.test(target)) {
    throw new UsageError(`path must start with / (or be an http(s) URL), got: ${target}`);
  }
  if (values.anon && values.as !== undefined) {
    throw new UsageError("--as and --anon are mutually exclusive");
  }
  const user = values.anon ? undefined : (values.as ?? DEFAULT_USER);
  if (user !== undefined) passwordFor(user);
  return {
    method: upper,
    target,
    body: values.body,
    headers: (values.header ?? []).map(parseHeader),
    user,
    run: values.run,
  };
}

/**
 * The headers actually sent: Host, auth, and (with a body) a JSON
 * Content-Type and Content-Length — then `--header` overrides, matched
 * case-insensitively, so content-type bugs can be reproduced exactly.
 */
export function requestHeaders(
  url: URL,
  token: string | undefined,
  body: Buffer | undefined,
  overrides: readonly [string, string][],
): Record<string, string> {
  const headers = new Map<string, [string, string]>();
  const set = (name: string, value: string): void => {
    if (value === "") headers.delete(name.toLowerCase());
    else headers.set(name.toLowerCase(), [name, value]);
  };
  set("Host", url.host);
  set("User-Agent", "mimos-harness");
  if (token !== undefined) set("Authorization", `Bearer ${token}`);
  if (body !== undefined) {
    set("Content-Type", "application/json");
    set("Content-Length", String(body.length));
  }
  for (const [name, value] of overrides) set(name, value);
  return Object.fromEntries(headers.values());
}

function readBody(source: string | undefined): Buffer | undefined {
  if (source === undefined) return undefined;
  try {
    return readFileSync(source === "-" ? 0 : source);
  } catch (error) {
    throw new UsageError(`--body ${source}: ${(error as Error).message}`);
  }
}

function prettyBody(body: string, contentType: string | undefined): string {
  if (isJson(contentType) && body.trim() !== "") {
    try {
      return JSON.stringify(JSON.parse(body), null, 2);
    } catch {
      // Claimed JSON but isn't — show it raw.
    }
  }
  return body;
}

export const apiCommand: Command = {
  name: "api",
  summary: "Send a recorded HTTP request to the API",
  usage: `harness api <METHOD> <path> [--body <file>|-] [--header Name:Value]... [--as <user>|--anon]

  <path>               /api/v1/... on the API (${endpoints().api}), or an absolute URL
  --body <file>|-      request body from a file or stdin; sets Content-Type: application/json
  -H, --header K:V     add or override a header (repeatable); "K:" removes one, e.g. -H 'Content-Type:'
  --as <user>          authenticate as ${Object.keys(USERS).join(" | ")} (default ${DEFAULT_USER})
  --anon               send no Authorization header
${RUN_USAGE}

Prints the status and body. Records the redacted exchange in api/exchanges.jsonl,
the service logs for the run's time window in logs/, and a section in summary.md.
Exit code: 0 for a status below 400, 1 otherwise.`,
  async run(argv) {
    const args = parseApiArgs(argv);
    const startedAt = new Date();
    const url = new URL(args.target, endpoints().api);
    const body = readBody(args.body);
    const run = startRun("api", startedAt, args.run);
    const token = args.user === undefined ? undefined : await fetchToken(args.user);
    const headers = requestHeaders(url, token, body, args.headers);

    const sentAt = performance.now();
    const response = await send(args.method, url, headers, body);
    const durationMs = Math.round(performance.now() - sentAt);
    const failed = response.status >= 400;

    const requestBody = body?.toString("utf8");
    const requestType = Object.entries(headers).find(([name]) => name.toLowerCase() === "content-type")?.[1];
    const line = appendJsonLine(run, "api/exchanges.jsonl", {
      at: startedAt.toISOString(),
      user: args.user ?? null,
      durationMs,
      request: {
        method: args.method,
        url: url.href,
        headers: redactHeaders(headers),
        body: requestBody === undefined ? null : redactBody(requestBody, requestType),
      },
      response: {
        status: response.status,
        statusText: response.statusText,
        headers: redactHeaders(response.headers),
        body: redactBody(response.body, response.headers["content-type"]),
      },
    });

    // Let the services flush the lines this request produced, then capture the run's whole window.
    await new Promise((resolve) => setTimeout(resolve, 300));
    const since = new Date(runStartedAt(run, startedAt).getTime() - 1000).toISOString();
    const logs = await captureLogs(run, { since }).catch((error: Error) => error);

    const contentType = response.headers["content-type"];
    const section = [
      `## \`${displayCommand(["api", ...argv])}\` — ${response.status} ${response.statusText}`,
      "",
      `- **Request:** \`${args.method} ${url.href}\` as ${args.user ?? "anonymous"}` +
        (body ? ` · \`Content-Type: ${requestType ?? "(none)"}\` · ${body.length} bytes` : ""),
      `- **Response:** ${response.status} ${response.statusText} · \`${mediaType(contentType) || "(no content type)"}\` · ${durationMs} ms`,
      `- **Exchange:** \`api/exchanges.jsonl\` line ${line}`,
      logs instanceof Error
        ? `- **Logs:** not captured — ${logs.message}`
        : `- **Logs (run window):** ${describeLogs(logs)}`,
    ];
    if (failed && response.body.trim() !== "") {
      const problem = mediaType(contentType) === "application/problem+json";
      section.push(
        "",
        problem ? "Problem details:" : "Response body:",
        "",
        fenced(prettyBody(redactBody(response.body, contentType), contentType).slice(0, 4000), problem ? "json" : ""),
      );
    }
    const apiLog = logs instanceof Error ? undefined : logs.find((log) => log.service === "api");
    const apiLines = linesSince(apiLog?.text ?? "", startedAt);
    if (failed && apiLines.length > 0) {
      section.push("", "API log lines since this request was sent:", "", fenced(apiLines.slice(-20).join("\n")));
    }
    appendSummary(run, section.join("\n"));
    const exitCode = failed ? 1 : 0;
    await finishRun(run, ["api", ...argv], startedAt, exitCode);

    process.stdout.write(`HTTP ${response.status} ${response.statusText} (${durationMs} ms)\n`);
    if (contentType) process.stdout.write(`Content-Type: ${contentType}\n`);
    if (response.body) process.stdout.write(`\n${prettyBody(response.body, contentType)}\n`);
    return exitCode;
  },
};
