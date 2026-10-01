import assert from "node:assert/strict";
import { test } from "node:test";

import type { CapturedLog } from "../src/compose.ts";
import { describeRequestLines, formatLogLine, matchesRequestId, parseLogLine, requestLines } from "../src/logs.ts";

const ID = "3f2b9c1e-0d4a-4b7e-9a51-6c2f8e7d1a90";

/** A text line as the default stack logs it: the ID in Boot's correlation slot. */
const TEXT =
  `2026-10-01T15:56:11.194000000Z 2026-10-01T15:56:11.194Z  WARN 1 --- [mimos-api] [nio-8080-exec-2] [${ID}] ` +
  "i.g.n.m.a.support.RequestLoggingFilter   : POST /api/v1/recipes -> 415 (4 ms) content-type=<none> accept=*/*";

/** The same line under the debug overlay's ECS format (Boot nests dotted names). */
const ECS =
  "2026-10-01T15:56:11.194000000Z " +
  JSON.stringify({
    "@timestamp": "2026-10-01T15:56:11.194Z",
    log: { level: "WARN", logger: "io.github.noahhhx.mimos.api.support.RequestLoggingFilter" },
    message: "POST /api/v1/recipes -> 415 (4 ms) content-type=<none> accept=*/*",
    requestId: ID,
    status: 415,
  });

test("log lines: the compose timestamp is split off and JSON bodies parsed", () => {
  const text = parseLogLine(TEXT);
  assert.equal(text.stamp, "2026-10-01T15:56:11.194000000Z");
  assert.equal(text.json, undefined);
  assert.equal(parseLogLine(ECS).json?.requestId, ID);
  assert.deepEqual(parseLogLine("no stamp {"), { stamp: undefined, body: "no stamp {", json: undefined });
  assert.equal(parseLogLine("2026-10-01T15:56:11Z {not json").json, undefined);
});

test("request IDs match the JSON field exactly, and text by substring", () => {
  assert.ok(matchesRequestId(TEXT, ID));
  assert.ok(matchesRequestId(ECS, ID));
  assert.ok(!matchesRequestId(ECS, ID.slice(0, 8)), "a JSON line's requestId must equal the ID");
  assert.ok(
    !matchesRequestId(`2026-10-01T15:56:11Z ${JSON.stringify({ message: "other", requestId: "x", note: ID })}`, ID),
    "another request's line that happens to mention the ID is not a match",
  );
  assert.ok(matchesRequestId(`2026-10-01T15:56:11Z [mimos-api] GET /x -> 404 request-id=${ID}`, ID));
  assert.ok(!matchesRequestId(TEXT, "a-different-id"));
});

test("ECS lines are formatted for reading; text lines pass through", () => {
  assert.equal(
    formatLogLine(ECS),
    "2026-10-01T15:56:11.194000000Z WARN  RequestLoggingFilter: POST /api/v1/recipes -> 415 (4 ms) content-type=<none> accept=*/*",
  );
  const flat = JSON.stringify({
    "@timestamp": "2026-10-01T15:56:11.194Z",
    "log.level": "ERROR",
    "log.logger": "org.apache.catalina.core.ContainerBase",
    message: "Servlet.service() threw exception",
    "error.type": "java.lang.IllegalStateException",
    "error.message": "boom",
  });
  assert.equal(
    formatLogLine(flat),
    "2026-10-01T15:56:11.194Z ERROR ContainerBase: Servlet.service() threw exception [java.lang.IllegalStateException: boom]",
  );
  assert.equal(formatLogLine(TEXT), TEXT);
});

test("a request's lines are gathered across services", () => {
  const logs: CapturedLog[] = [
    { service: "api", file: "logs/api.log", lines: 3, text: [TEXT, "2026-10-01T15:56:10Z unrelated", ECS, ""].join("\n") },
    { service: "postgres", file: "logs/postgres.log", lines: 1, text: "2026-10-01T15:56:10Z checkpoint\n" },
    { service: "web", file: "logs/web.log", lines: 1, text: `2026-10-01T15:56:12Z [mimos-api] GET /x -> 404 request-id=${ID}\n` },
  ];
  const matches = requestLines(logs, ID);
  assert.deepEqual(
    matches.map((match) => [match.service, match.lines.length]),
    [
      ["api", 2],
      ["web", 1],
    ],
  );
  const described = describeRequestLines(matches);
  assert.equal(described.length, 3);
  assert.match(described[1]!, /^api \| 2026-10-01T15:56:11.194000000Z WARN  RequestLoggingFilter: POST/);
  assert.match(described[2]!, /^web \| 2026-10-01T15:56:12Z \[mimos-api\] GET \/x -> 404/);
});
