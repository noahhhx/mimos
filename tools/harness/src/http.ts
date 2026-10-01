import { request as httpRequest, STATUS_CODES } from "node:http";
import { request as httpsRequest } from "node:https";

import { HarnessError } from "./args.ts";

/**
 * A minimal HTTP client over node:http rather than fetch: fetch adds its own
 * headers (Accept, Accept-Language, Sec-Fetch-Mode, …), and the harness must
 * send — and record — exactly the headers it was asked to, so header bugs can
 * be reproduced.
 */

export interface HttpResponse {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: string;
}

export function send(
  method: string,
  url: URL,
  headers: Readonly<Record<string, string>>,
  body?: Buffer,
): Promise<HttpResponse> {
  const request = url.protocol === "https:" ? httpsRequest : httpRequest;
  return new Promise((resolve, reject) => {
    const req = request(url, { method, headers, agent: false }, (res) => {
      const chunks: Buffer[] = [];
      res.on("data", (chunk: Buffer) => chunks.push(chunk));
      res.on("end", () => {
        const responseHeaders: Record<string, string> = {};
        for (const [name, value] of Object.entries(res.headers)) {
          if (value !== undefined) responseHeaders[name] = Array.isArray(value) ? value.join(", ") : value;
        }
        resolve({
          status: res.statusCode ?? 0,
          // HTTP/1.1 servers may omit the reason phrase (Tomcat does); use the standard one.
          statusText: res.statusMessage || (STATUS_CODES[res.statusCode ?? 0] ?? ""),
          headers: responseHeaders,
          body: Buffer.concat(chunks).toString("utf8"),
        });
      });
      res.on("error", reject);
    });
    req.on("error", (error: NodeJS.ErrnoException) => {
      reject(
        error.code === "ECONNREFUSED"
          ? new HarnessError(`cannot reach ${url.origin} — is the stack up? (harness status / harness up)`)
          : error,
      );
    });
    req.end(body);
  });
}

/** Media type without parameters, lowercased (`application/problem+json`). */
export function mediaType(contentType: string | undefined): string {
  return (contentType ?? "").split(";")[0]?.trim().toLowerCase() ?? "";
}

export function isJson(contentType: string | undefined): boolean {
  const type = mediaType(contentType);
  return type === "application/json" || type.endsWith("+json");
}
