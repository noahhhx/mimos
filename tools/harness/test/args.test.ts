import assert from "node:assert/strict";
import { test } from "node:test";

import { displayCommand, parseHeader, UsageError } from "../src/args.ts";
import { parseApiArgs, requestHeaders } from "../src/commands/api.ts";
import { parseDiagArgs } from "../src/commands/diag.ts";
import { parseLoglevelArgs } from "../src/commands/loglevel.ts";
import { parseLogsArgs } from "../src/commands/logs.ts";
import { parseSqlArgs } from "../src/commands/sql.ts";
import { parseUpArgs, upEnv } from "../src/commands/stack.ts";
import { parseTokenArgs } from "../src/commands/token.ts";
import { parseUiArgs, scenarioNames } from "../src/commands/ui.ts";

test("api: method and path, defaulting to the test user", () => {
  assert.deepEqual(parseApiArgs(["get", "/api/v1/me"]), {
    method: "GET",
    target: "/api/v1/me",
    body: undefined,
    headers: [],
    user: "test",
    run: undefined,
  });
});

test("api: body, repeated headers, user, and run", () => {
  const args = parseApiArgs([
    "POST",
    "/api/v1/recipes",
    "--body",
    "-",
    "-H",
    "Content-Type: text/plain",
    "--header",
    "X-Request-Id:abc",
    "--as",
    "test2",
    "--run",
    "latest",
  ]);
  assert.equal(args.body, "-");
  assert.deepEqual(args.headers, [
    ["Content-Type", "text/plain"],
    ["X-Request-Id", "abc"],
  ]);
  assert.equal(args.user, "test2");
  assert.equal(args.run, "latest");
});

test("api: --anon sends no user, and conflicts with --as", () => {
  assert.equal(parseApiArgs(["GET", "/api/v1/public/recipes", "--anon"]).user, undefined);
  assert.throws(() => parseApiArgs(["GET", "/x", "--anon", "--as", "test"]), UsageError);
});

test("api: rejects bad invocations as usage errors", () => {
  assert.throws(() => parseApiArgs([]), UsageError);
  assert.throws(() => parseApiArgs(["GET"]), UsageError);
  assert.throws(() => parseApiArgs(["GET", "/a", "/b"]), UsageError);
  assert.throws(() => parseApiArgs(["FETCH", "/a"]), UsageError);
  assert.throws(() => parseApiArgs(["GET", "api/v1/me"]), UsageError);
  assert.throws(() => parseApiArgs(["GET", "/a", "--as", "nobody"]), UsageError);
  assert.throws(() => parseApiArgs(["GET", "/a", "--bogus"]), UsageError);
  assert.doesNotThrow(() => parseApiArgs(["GET", "http://localhost:8080/actuator/health"]));
});

test("headers: Name:Value, an empty value means remove, a missing name is an error", () => {
  assert.deepEqual(parseHeader("Accept: application/json"), ["Accept", "application/json"]);
  assert.deepEqual(parseHeader("Content-Type:"), ["Content-Type", ""]);
  assert.throws(() => parseHeader("no-colon"), UsageError);
  assert.throws(() => parseHeader(":value"), UsageError);
});

test("request headers: JSON by default with a body, overridable and removable case-insensitively", () => {
  const url = new URL("http://localhost:8080/api/v1/recipes");
  const body = Buffer.from("{}");
  assert.deepEqual(requestHeaders(url, "tok", body, [], "id-1"), {
    Host: "localhost:8080",
    "User-Agent": "mimos-harness",
    Authorization: "Bearer tok",
    "X-Request-Id": "id-1",
    "Content-Type": "application/json",
    "Content-Length": "2",
  });
  const overridden = requestHeaders(url, undefined, body, [["content-type", "text/plain"]], "id-1");
  assert.equal(overridden["content-type"], "text/plain");
  assert.equal(overridden["Content-Type"], undefined);
  assert.equal(overridden.Authorization, undefined);
  const removed = requestHeaders(url, undefined, body, [["CONTENT-TYPE", ""]], "id-1");
  assert.ok(!Object.keys(removed).some((name) => name.toLowerCase() === "content-type"));
  assert.equal(requestHeaders(url, undefined, undefined, [], "id-1")["Content-Type"], undefined);
});

test("request headers: the request ID can be replaced or dropped like any other", () => {
  const url = new URL("http://localhost:8080/api/v1/me");
  assert.equal(requestHeaders(url, undefined, undefined, [["x-request-id", "mine"]], "id-1")["x-request-id"], "mine");
  const dropped = requestHeaders(url, undefined, undefined, [["X-Request-Id", ""]], "id-1");
  assert.ok(!Object.keys(dropped).some((name) => name.toLowerCase() === "x-request-id"));
});

test("token, up, and logs options", () => {
  assert.deepEqual(parseTokenArgs([]), { user: "test", decode: false });
  assert.deepEqual(parseTokenArgs(["--as", "test2", "--decode"]), { user: "test2", decode: true });
  assert.throws(() => parseTokenArgs(["--as", "root"]), UsageError);
  assert.deepEqual(parseUpArgs([]), { build: true, debug: false, sqlLog: false, run: undefined });
  assert.deepEqual(parseUpArgs(["--no-build", "--debug"]), { build: false, debug: true, sqlLog: false, run: undefined });
  assert.deepEqual(parseLogsArgs(["--service", "api", "--service", "web", "--since", "10m"]), {
    services: ["api", "web"],
    since: "10m",
    requestId: undefined,
    run: undefined,
  });
  assert.equal(parseLogsArgs(["--request-id", "3f2b9c1e-0d4a-4b7e-9a51-6c2f8e7d1a90"]).requestId, "3f2b9c1e-0d4a-4b7e-9a51-6c2f8e7d1a90");
  assert.throws(() => parseLogsArgs(["--request-id", "not an id"]), UsageError);
  assert.throws(() => parseUpArgs(["extra"]), UsageError);
});

test("ui: one known scenario, headless as the test user by default", () => {
  const known = ["create-recipe", "login"];
  assert.deepEqual(parseUiArgs(["login"], known), { scenario: "login", headed: false, user: "test", run: undefined });
  assert.deepEqual(parseUiArgs(["create-recipe", "--headed", "--as", "test2", "--run", "latest"], known), {
    scenario: "create-recipe",
    headed: true,
    user: "test2",
    run: "latest",
  });
  assert.throws(() => parseUiArgs([], known), UsageError);
  assert.throws(() => parseUiArgs(["login", "create-recipe"], known), UsageError);
  assert.throws(() => parseUiArgs(["nope"], known), /scenarios: create-recipe, login/);
  assert.throws(() => parseUiArgs(["login", "--as", "admin"], known), UsageError);
});

test("ui: scenarios are the spec files in tools/harness/scenarios", () => {
  assert.deepEqual(scenarioNames(), ["account-data", "app-nav", "app-pages", "create-recipe", "edit-recipe", "keycloak-theme", "kitchen-home", "login", "plugin-opt-in", "recipe-form-errors", "session-expiry"]);
});

test("displayCommand quotes only what needs quoting", () => {
  assert.equal(
    displayCommand(["api", "POST", "/api/v1/recipes", "-H", "Content-Type: text/plain"]),
    "harness api POST /api/v1/recipes -H 'Content-Type: text/plain'",
  );
  assert.equal(displayCommand(["x", "it's"]), `harness x 'it'\\''s'`);
});

test("up: --sql-log needs the debug overlay, and is the only way to turn statement logging on", () => {
  assert.equal(parseUpArgs(["--debug", "--sql-log"]).sqlLog, true);
  assert.throws(() => parseUpArgs(["--sql-log"]), UsageError);
  const saved = process.env.POSTGRES_LOG_MIN_DURATION_STATEMENT;
  process.env.POSTGRES_LOG_MIN_DURATION_STATEMENT = "0";
  try {
    assert.equal(upEnv(false).POSTGRES_LOG_MIN_DURATION_STATEMENT, undefined, "an exported value does not leak into a plain --debug");
    assert.equal(upEnv(true).POSTGRES_LOG_MIN_DURATION_STATEMENT, "0");
  } finally {
    if (saved === undefined) delete process.env.POSTGRES_LOG_MIN_DURATION_STATEMENT;
    else process.env.POSTGRES_LOG_MIN_DURATION_STATEMENT = saved;
  }
});

test("diag: recent logs default to 15 minutes", () => {
  assert.deepEqual(parseDiagArgs([]), { since: "15m", run: undefined });
  assert.deepEqual(parseDiagArgs(["--since", "1h", "--run", "latest"]), { since: "1h", run: "latest" });
  assert.throws(() => parseDiagArgs(["api"]), UsageError);
});

test("loglevel: show, set (level case-insensitive), or reset", () => {
  assert.deepEqual(parseLoglevelArgs(["org.springframework.web"]), { kind: "show", logger: "org.springframework.web", run: undefined });
  assert.deepEqual(parseLoglevelArgs(["org.springframework.web", "debug"]), {
    kind: "set",
    logger: "org.springframework.web",
    level: "DEBUG",
    run: undefined,
  });
  assert.equal(parseLoglevelArgs(["ROOT", "WARN"]).kind, "set");
  assert.deepEqual(parseLoglevelArgs(["--reset"]), { kind: "reset", run: undefined });
  assert.throws(() => parseLoglevelArgs([]), UsageError);
  assert.throws(() => parseLoglevelArgs(["org.springframework.web", "VERBOSE"]), UsageError);
  assert.throws(() => parseLoglevelArgs(["--reset", "ROOT"]), UsageError);
  assert.throws(() => parseLoglevelArgs(["../env", "DEBUG"]), UsageError, "a logger name never reaches the URL unchecked");
  assert.throws(() => parseLoglevelArgs(["a", "DEBUG", "extra"]), UsageError);
});

test("sql: one quoted query, read-only unless --write", () => {
  assert.deepEqual(parseSqlArgs(["select 1"]), { query: "select 1", write: false, csv: false, run: undefined });
  assert.deepEqual(parseSqlArgs(["delete from t", "--write", "--csv"]), { query: "delete from t", write: true, csv: true, run: undefined });
  assert.throws(() => parseSqlArgs([]), UsageError);
  assert.throws(() => parseSqlArgs(["  "]), UsageError);
  assert.throws(() => parseSqlArgs(["select", "1"]), UsageError, "an unquoted query is two arguments");
});
