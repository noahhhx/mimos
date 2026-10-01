import assert from "node:assert/strict";
import { test } from "node:test";

import { composeEnv, describeLogs, isHealthy, linesSince, parseJsonOutput } from "../src/compose.ts";

test("compose JSON output: a single array or one object per line", () => {
  assert.deepEqual(parseJsonOutput('[{"Service":"api"},{"Service":"web"}]\n'), [{ Service: "api" }, { Service: "web" }]);
  assert.deepEqual(parseJsonOutput('{"Service":"api"}\n{"Service":"web"}\n'), [{ Service: "api" }, { Service: "web" }]);
  assert.deepEqual(parseJsonOutput("\n"), []);
});

test("healthy means running and healthy, or running without a healthcheck", () => {
  const base = { service: "api", container: "mimos-api-1", exitCode: 0 };
  assert.ok(isHealthy({ ...base, state: "running", health: "healthy" }));
  assert.ok(isHealthy({ ...base, state: "running", health: "" }));
  assert.ok(!isHealthy({ ...base, state: "running", health: "starting" }));
  assert.ok(!isHealthy({ ...base, state: "exited", health: "" }));
});

test("log lines are selected by their compose timestamp", () => {
  const text = [
    "2026-10-01T15:25:09.900000000Z before",
    "2026-10-01T15:25:10.314614000Z WARN Resolved [HttpMediaTypeNotSupportedException]",
    "  continuation line without a timestamp",
    "",
  ].join("\n");
  assert.deepEqual(linesSince(text, new Date("2026-10-01T15:25:10.000Z")), [
    "2026-10-01T15:25:10.314614000Z WARN Resolved [HttpMediaTypeNotSupportedException]",
  ]);
});

test("log listing names files with output and folds the empty ones", () => {
  assert.equal(
    describeLogs([
      { service: "api", file: "logs/api.log", lines: 3, text: "" },
      { service: "web", file: "logs/web.log", lines: 0, text: "" },
      { service: "postgres", file: "logs/postgres.log", lines: 0, text: "" },
    ]),
    "`logs/api.log` (3 lines); no output from web, postgres",
  );
});

test("compose runs without SOURCE_DATE_EPOCH, so images carry their real build time", () => {
  assert.deepEqual(composeEnv({ PATH: "/bin", SOURCE_DATE_EPOCH: "315532800" }), { PATH: "/bin" });
});
