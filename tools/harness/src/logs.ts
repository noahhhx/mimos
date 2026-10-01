import type { CapturedLog } from "./compose.ts";

/**
 * Request correlation over captured compose logs. The API puts each
 * request's `X-Request-Id` on every line the request logs: inside the
 * correlation brackets of a text line, or as the `requestId` field of a JSON
 * line (the debug overlay's ECS format). Other services mention it in text
 * (the web server's `request-id=<id>`), so text lines match by substring.
 */

/** The API's own rule for a well-formed ID (RequestLoggingFilter); anything else it replaces. */
export const REQUEST_ID = /^[A-Za-z0-9._-]{1,64}$/;

export const REQUEST_ID_HEADER = "X-Request-Id";

interface LogLine {
  /** The compose `--timestamps` prefix, when present. */
  stamp: string | undefined;
  body: string;
  /** The body parsed, when it is a JSON object (structured logging). */
  json: Record<string, unknown> | undefined;
}

export function parseLogLine(line: string): LogLine {
  const space = line.indexOf(" ");
  const prefix = space > 0 ? line.slice(0, space) : "";
  const stamped = !Number.isNaN(Date.parse(prefix)) && /^\d{4}-\d{2}-\d{2}T/.test(prefix);
  const body = stamped ? line.slice(space + 1) : line;
  let json: Record<string, unknown> | undefined;
  if (body.startsWith("{")) {
    try {
      const parsed: unknown = JSON.parse(body);
      if (parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)) json = parsed as Record<string, unknown>;
    } catch {
      // Not JSON after all — treat it as text.
    }
  }
  return { stamp: stamped ? prefix : undefined, body, json };
}

/** A field by dotted name, flat (`"log.level": …`) or nested (`"log": {"level": …}`). */
function field(json: Record<string, unknown>, name: string): unknown {
  if (name in json) return json[name];
  let value: unknown = json;
  for (const part of name.split(".")) {
    if (value === null || typeof value !== "object") return undefined;
    value = (value as Record<string, unknown>)[part];
  }
  return value;
}

export function matchesRequestId(line: string, id: string): boolean {
  const { body, json } = parseLogLine(line);
  if (json !== undefined && typeof json.requestId === "string") return json.requestId === id;
  return body.includes(id);
}

/** A JSON (ECS) line as `<time> <LEVEL> <Logger>: <message> [<error>]`; text lines as they are. */
export function formatLogLine(line: string): string {
  const { stamp, json } = parseLogLine(line);
  if (json === undefined || typeof field(json, "message") !== "string") return line;
  const time = stamp ?? String(field(json, "@timestamp") ?? "");
  const level = String(field(json, "log.level") ?? "").padEnd(5);
  const logger = String(field(json, "log.logger") ?? "").split(".").pop();
  const errorType = field(json, "error.type");
  const errorMessage = field(json, "error.message");
  const error = typeof errorType === "string" ? ` [${errorType}${typeof errorMessage === "string" ? `: ${errorMessage}` : ""}]` : "";
  return `${time} ${level} ${logger}: ${String(field(json, "message"))}${error}`.trim();
}

export interface RequestLines {
  service: string;
  /** Raw lines, as captured. */
  lines: string[];
}

/** Every captured line mentioning the request, grouped by service (services without any are left out). */
export function requestLines(logs: readonly CapturedLog[], id: string): RequestLines[] {
  return logs
    .map((log) => ({ service: log.service, lines: log.text.split("\n").filter((line) => line !== "" && matchesRequestId(line, id)) }))
    .filter((match) => match.lines.length > 0);
}

/** `<service> | <formatted line>` — one block across services, for summaries and stdout. */
export function describeRequestLines(matches: readonly RequestLines[]): string[] {
  const width = Math.max(0, ...matches.map((match) => match.service.length));
  return matches.flatMap((match) => match.lines.map((line) => `${match.service.padEnd(width)} | ${formatLogLine(line)}`));
}
