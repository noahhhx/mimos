import assert from "node:assert/strict";
import { test } from "node:test";

import { displayCommand, parseHeader, UsageError } from "../src/args.ts";
import { parseApiArgs, requestHeaders } from "../src/commands/api.ts";
import { parseLogsArgs } from "../src/commands/logs.ts";
import { parseUpArgs } from "../src/commands/stack.ts";
import { parseTokenArgs } from "../src/commands/token.ts";

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
  assert.deepEqual(requestHeaders(url, "tok", body, []), {
    Host: "localhost:8080",
    "User-Agent": "mimos-harness",
    Authorization: "Bearer tok",
    "Content-Type": "application/json",
    "Content-Length": "2",
  });
  const overridden = requestHeaders(url, undefined, body, [["content-type", "text/plain"]]);
  assert.equal(overridden["content-type"], "text/plain");
  assert.equal(overridden["Content-Type"], undefined);
  assert.equal(overridden.Authorization, undefined);
  const removed = requestHeaders(url, undefined, body, [["CONTENT-TYPE", ""]]);
  assert.ok(!Object.keys(removed).some((name) => name.toLowerCase() === "content-type"));
  assert.equal(requestHeaders(url, undefined, undefined, [])["Content-Type"], undefined);
});

test("token, up, and logs options", () => {
  assert.deepEqual(parseTokenArgs([]), { user: "test", decode: false });
  assert.deepEqual(parseTokenArgs(["--as", "test2", "--decode"]), { user: "test2", decode: true });
  assert.throws(() => parseTokenArgs(["--as", "root"]), UsageError);
  assert.deepEqual(parseUpArgs([]), { build: true, debug: false, run: undefined });
  assert.deepEqual(parseUpArgs(["--no-build", "--debug"]), { build: false, debug: true, run: undefined });
  assert.deepEqual(parseLogsArgs(["--service", "api", "--service", "web", "--since", "10m"]), {
    services: ["api", "web"],
    since: "10m",
    run: undefined,
  });
  assert.throws(() => parseUpArgs(["extra"]), UsageError);
});

test("displayCommand quotes only what needs quoting", () => {
  assert.equal(
    displayCommand(["api", "POST", "/api/v1/recipes", "-H", "Content-Type: text/plain"]),
    "harness api POST /api/v1/recipes -H 'Content-Type: text/plain'",
  );
  assert.equal(displayCommand(["x", "it's"]), `harness x 'it'\\''s'`);
});
