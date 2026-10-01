import { STATUS_CODES } from "node:http";

import { fenced } from "./command.ts";
import { isJson, mediaType } from "./http.ts";

/**
 * Reads the browser's HAR (already redacted — see evidence.ts) into the
 * evidence summary.md needs: every call the page made to the API, and every
 * failed request in full (method, URL, headers, bodies). For a bug like the
 * recipe 415, that table is the evidence.
 */

interface HarHeader {
  name: string;
  value: string;
}

/** The subset of HAR 1.2 (plus Playwright's `_failureText`) the summary reads. */
export interface Har {
  log: { entries: HarEntry[] };
}

export interface HarEntry {
  startedDateTime: string;
  time: number;
  request: {
    method: string;
    url: string;
    headers: HarHeader[];
    bodySize?: number;
    postData?: { mimeType?: string; text?: string };
  };
  response: {
    status: number;
    statusText: string;
    headers: HarHeader[];
    content?: { mimeType?: string; text?: string; encoding?: string; size?: number };
    _failureText?: string;
  };
}

export interface Exchange {
  at: string;
  method: string;
  url: string;
  /** 0 when the request never got a response (network error, CORS, aborted). */
  status: number;
  statusText: string;
  durationMs: number;
  requestHeaders: HarHeader[];
  /** The Content-Type header actually sent (not the HAR's postData.mimeType, which Playwright defaults). */
  requestType: string | undefined;
  /** Undefined without a body — or when the browser sent one but did not expose it (see requestBodySize). */
  requestBody: string | undefined;
  /** Bytes sent as the body; 0 when none. */
  requestBodySize: number;
  responseType: string | undefined;
  responseBody: string | undefined;
  failureText: string | undefined;
}

function header(headers: readonly HarHeader[], name: string): string | undefined {
  return headers.find((candidate) => candidate.name.toLowerCase() === name)?.value;
}

export function exchanges(har: Har): Exchange[] {
  return har.log.entries.map((entry) => {
    const content = entry.response.content;
    const body =
      content?.text === undefined
        ? undefined
        : content.encoding === "base64"
          ? `(${content.size ?? Buffer.from(content.text, "base64").length} bytes, binary)`
          : content.text;
    return {
      at: entry.startedDateTime,
      method: entry.request.method,
      url: entry.request.url,
      status: Math.max(entry.response.status, 0),
      // HTTP/1.1 servers may omit the reason phrase (Tomcat does); use the standard one.
      statusText: entry.response.statusText || (STATUS_CODES[entry.response.status] ?? ""),
      durationMs: Math.round(Math.max(entry.time, 0)),
      requestHeaders: entry.request.headers,
      requestType: header(entry.request.headers, "content-type"),
      requestBody: entry.request.postData?.text || undefined,
      requestBodySize: Math.max(entry.request.bodySize ?? 0, entry.request.postData?.text?.length ?? 0, 0),
      responseType: header(entry.response.headers, "content-type") ?? (content?.mimeType || undefined),
      responseBody: body,
      failureText: entry.response._failureText,
    };
  });
}

/**
 * Failed: an error status or no response at all. Redirects are not failures —
 * the OIDC login is a chain of them.
 */
export function isFailure(exchange: Exchange): boolean {
  return exchange.status === 0 || exchange.status >= 400;
}

function pretty(body: string, contentType: string | undefined): string {
  if (isJson(contentType)) {
    try {
      return JSON.stringify(JSON.parse(body), null, 2);
    } catch {
      // Claimed JSON but isn't — show it raw.
    }
  }
  return body;
}

const BODY_LIMIT = 4000;

function clip(text: string): string {
  return text.length > BODY_LIMIT ? `${text.slice(0, BODY_LIMIT)}\n… (${text.length - BODY_LIMIT} more characters)` : text;
}

function statusOf(exchange: Exchange): string {
  return exchange.status === 0 ? `no response${exchange.failureText ? ` (${exchange.failureText})` : ""}` : `${exchange.status} ${exchange.statusText}`.trim();
}

/** `| # | Method | Path | Status | Sent | Received |` for every call to the API, in order. */
export function apiCallTable(all: readonly Exchange[], apiOrigin: string): string[] {
  const calls = all.filter((exchange) => new URL(exchange.url).origin === new URL(apiOrigin).origin);
  if (calls.length === 0) return ["No requests reached the API."];
  return [
    "| # | Method | Path | Status | Sent | Received |",
    "| --- | --- | --- | --- | --- | --- |",
    ...calls.map((call, index) => {
      const url = new URL(call.url);
      const sent =
        call.requestBodySize === 0 ? "—" : call.requestType ? `\`${mediaType(call.requestType)}\`` : "**no Content-Type**";
      const received = call.responseType ? `\`${mediaType(call.responseType)}\`` : "—";
      const mark = isFailure(call) ? "**" : "";
      return `| ${index + 1} | ${call.method} | \`${url.pathname}${url.search}\` | ${mark}${statusOf(call)}${mark} | ${sent} | ${received} |`;
    }),
  ];
}

/** One section per failed request: everything needed to reproduce it with `harness api`. */
export function failureDetails(exchange: Exchange): string[] {
  const lines = [
    `#### \`${exchange.method} ${exchange.url}\` → ${statusOf(exchange)}`,
    "",
    "Request headers:",
    "",
    fenced(exchange.requestHeaders.map(({ name, value }) => `${name}: ${value}`).join("\n") || "(none)"),
  ];
  if (exchange.requestBody !== undefined) {
    lines.push("", "Request body:", "", fenced(clip(pretty(exchange.requestBody, exchange.requestType)), isJson(exchange.requestType) ? "json" : ""));
  } else if (exchange.requestBodySize > 0) {
    lines.push("", `Request body: ${exchange.requestBodySize} bytes sent, but the browser did not expose them to the HAR.`);
  }
  if (exchange.responseBody !== undefined && exchange.responseBody.trim() !== "") {
    const problem = mediaType(exchange.responseType) === "application/problem+json";
    lines.push(
      "",
      `${problem ? "Problem details" : "Response body"} (\`${mediaType(exchange.responseType) || "no content type"}\`):`,
      "",
      fenced(clip(pretty(exchange.responseBody, exchange.responseType)), isJson(exchange.responseType) ? "json" : ""),
    );
  }
  return lines;
}
