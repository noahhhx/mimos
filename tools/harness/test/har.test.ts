import assert from "node:assert/strict";
import { test } from "node:test";

import { apiCallTable, exchanges, failureDetails, isFailure, type Har, type HarEntry } from "../src/har.ts";

const API = "http://localhost:8080";

function entry(overrides: {
  method?: string;
  url: string;
  status?: number;
  statusText?: string;
  requestHeaders?: { name: string; value: string }[];
  bodySize?: number;
  postData?: HarEntry["request"]["postData"];
  responseType?: string;
  responseBody?: string;
  failureText?: string;
  responseHeaders?: { name: string; value: string }[];
}): HarEntry {
  return {
    startedDateTime: "2026-10-01T15:40:06.900Z",
    time: 12.6,
    request: {
      method: overrides.method ?? "GET",
      url: overrides.url,
      headers: overrides.requestHeaders ?? [],
      bodySize: overrides.bodySize ?? 0,
      ...(overrides.postData ? { postData: overrides.postData } : {}),
    },
    response: {
      status: overrides.status ?? 200,
      statusText: overrides.statusText ?? "",
      headers: [
        ...(overrides.responseType ? [{ name: "Content-Type", value: overrides.responseType }] : []),
        ...(overrides.responseHeaders ?? []),
      ],
      content: { mimeType: overrides.responseType ?? "", text: overrides.responseBody },
      ...(overrides.failureText ? { _failureText: overrides.failureText } : {}),
    },
  };
}

/** Shaped like the recipe 415 as Chromium recorded it: no Content-Type, body not exposed. */
const HAR: Har = {
  log: {
    entries: [
      entry({ url: "http://localhost:3000/app", responseType: "text/html", responseBody: "<html></html>" }),
      entry({ url: `${API}/api/v1/me`, statusText: "OK", responseType: "application/json", responseBody: "{}" }),
      // A Next.js prefetch the router aborted: not a failure.
      entry({ url: "http://localhost:3000/app/plan?_rsc=1", failureText: "net::ERR_ABORTED" }),
      entry({
        method: "POST",
        url: `${API}/api/v1/recipes`,
        status: 415,
        requestHeaders: [
          { name: "authorization", value: "Bearer [REDACTED]" },
          { name: "x-request-id", value: "browser-415" },
          { name: "Content-Length", value: "315" },
        ],
        responseHeaders: [{ name: "X-Request-Id", value: "browser-415" }],
        bodySize: 315,
        postData: { mimeType: "application/octet-stream", text: "" },
        responseType: "application/json",
        responseBody: '{"status":415,"error":"Unsupported Media Type"}',
      }),
      entry({
        method: "PUT",
        url: `${API}/api/v1/recipes/1`,
        status: 400,
        statusText: "Bad Request",
        requestHeaders: [
          { name: "Content-Type", value: "application/json" },
          { name: "X-Request-Id", value: "not a valid id" },
        ],
        responseHeaders: [{ name: "x-request-id", value: "replaced-by-api" }],
        bodySize: 13,
        postData: { mimeType: "application/json", text: '{"title":""}' },
        responseType: "application/problem+json",
        responseBody: '{"title":"Bad Request","detail":"title must not be blank"}',
      }),
      entry({ url: `${API}/api/v1/plans/x`, status: -1, failureText: "net::ERR_FAILED" }),
    ],
  },
};

test("exchanges read what was sent, not Playwright's defaults", () => {
  const [, me, aborted, create, replace, unreachable] = exchanges(HAR);
  assert.equal(me?.status, 200);
  assert.equal(me?.durationMs, 13);
  assert.equal(create?.statusText, "Unsupported Media Type", "standard reason phrase when the server sent none");
  assert.equal(create?.requestType, undefined, "no Content-Type header was sent");
  assert.equal(create?.requestBody, undefined);
  assert.equal(create?.requestBodySize, 315);
  assert.equal(replace?.requestBody, '{"title":""}');
  assert.equal(unreachable?.status, 0);
  assert.equal(aborted?.failureText, "net::ERR_ABORTED");
  assert.equal(create?.requestId, "browser-415");
  assert.equal(replace?.requestId, "replaced-by-api", "the API's echo wins over what was sent");
  assert.equal(me?.requestId, undefined);
});

test("failures are error statuses and requests with no response — not redirects or aborted prefetches", () => {
  assert.deepEqual(
    exchanges(HAR).filter(isFailure).map((exchange) => exchange.url),
    [`${API}/api/v1/recipes`, `${API}/api/v1/recipes/1`, `${API}/api/v1/plans/x`],
  );
  assert.equal(isFailure({ ...exchanges(HAR)[0]!, status: 302 }), false);
});

test("the API call table lists only API calls, flagging failures and a missing Content-Type", () => {
  const table = apiCallTable(exchanges(HAR), API);
  assert.equal(table.length, 2 + 4);
  assert.equal(table[2], "| 1 | GET | `/api/v1/me` | 200 OK | — | `application/json` | — |");
  assert.equal(
    table[3],
    "| 2 | POST | `/api/v1/recipes` | **415 Unsupported Media Type** | **no Content-Type** | `application/json` | `browser-415` |",
  );
  assert.equal(
    table[4],
    "| 3 | PUT | `/api/v1/recipes/1` | **400 Bad Request** | `application/json` | `application/problem+json` | `replaced-by-api` |",
  );
  assert.equal(table[5], "| 4 | GET | `/api/v1/plans/x` | **no response (net::ERR_FAILED)** | — | — | — |");
  assert.deepEqual(apiCallTable([], API), ["No requests reached the API."]);
});

test("failure details carry headers, bodies, and say when the browser hid the body", () => {
  const [, , , create, replace] = exchanges(HAR);
  const created = failureDetails(create!, ["api | WARN RequestLoggingFilter: POST /api/v1/recipes -> 415"]).join("\n");
  assert.match(created, /^#### `POST http:\/\/localhost:8080\/api\/v1\/recipes` → 415 Unsupported Media Type/);
  assert.match(created, /Request ID: `browser-415` — `harness logs --request-id browser-415`/);
  assert.match(created, /authorization: Bearer \[REDACTED\]\nx-request-id: browser-415\nContent-Length: 315/);
  assert.match(created, /Log lines for this request:\n\n```\napi \| WARN RequestLoggingFilter: POST \/api\/v1\/recipes -> 415\n```$/);
  assert.match(created, /Request body: 315 bytes sent, but the browser did not expose them to the HAR\./);
  assert.match(created, /Response body \(`application\/json`\):\n\n```json\n\{\n {2}"status": 415/);

  const replaced = failureDetails(replace!).join("\n");
  assert.match(replaced, /Request body:\n\n```json\n\{\n {2}"title": ""\n\}\n```/);
  assert.match(replaced, /Problem details \(`application\/problem\+json`\):/);
  assert.match(replaced, /"detail": "title must not be blank"/);
  assert.doesNotMatch(replaced, /Log lines for this request/, "no section without lines");
});
