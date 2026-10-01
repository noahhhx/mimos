import assert from "node:assert/strict";
import { test } from "node:test";

import { UsageError } from "../src/args.ts";
import { formatHit, parseBreakArgs, splitWords, type Hit } from "../src/commands/debug.ts";
import { REPO_ROOT } from "../src/config.ts";
import { sourceRoots } from "../src/jdb.ts";

const ME = "io.github.noahhhx.mimos.api.identity.MeController";
const roots = sourceRoots(REPO_ROOT);

test("--then is split like a shell would: quotes and backslashes, no expansion", () => {
  assert.deepEqual(splitWords("api GET /api/v1/me"), ["api", "GET", "/api/v1/me"]);
  assert.deepEqual(splitWords(`  api POST /x -H 'Content-Type: text/plain'  --body "a b.json" `), [
    "api",
    "POST",
    "/x",
    "-H",
    "Content-Type: text/plain",
    "--body",
    "a b.json",
  ]);
  assert.deepEqual(splitWords(String.raw`a\ b "c\"d" 'e\f' "$HOME" ''`), ["a b", 'c"d', String.raw`e\f`, "$HOME", ""]);
  assert.throws(() => splitWords("api 'GET"), UsageError);
});

test("break: location resolved, defaults, trigger, prints", () => {
  const args = parseBreakArgs(["break", "MeController.getMe", "--then", "harness api GET /api/v1/me", "--print", "profile", "--print", "this.currentUser"], roots);
  assert.deepEqual(args.location, { kind: "method", className: ME, method: "getMe", signature: undefined });
  assert.deepEqual(args.then, ["api", "GET", "/api/v1/me"], "a leading `harness` is dropped");
  assert.deepEqual(args.prints, ["profile", "this.currentUser"]);
  assert.equal(args.hits, 1);
  assert.equal(args.timeoutSeconds, 60);

  const line = parseBreakArgs(["break", "MeController:27", "--hits", "3", "--timeout", "5"], roots);
  assert.deepEqual(line.location, { kind: "line", className: ME, line: 27 });
  assert.equal(line.then, undefined);
  assert.equal(line.hits, 3);
  assert.equal(line.timeoutSeconds, 5);
});

test("break: usage errors", () => {
  const bad: string[][] = [
    [],
    ["break"],
    ["watch", "MeController:27"],
    ["break", "MeController:27", "extra"],
    ["break", "MeController:27", "--then", "sql 'select 1'"],
    ["break", "MeController:27", "--then", "debug break X:1"],
    ["break", "MeController:27", "--then", "api GET /x --run latest"],
    ["break", "MeController:27", "--hits", "0"],
    ["break", "MeController:27", "--timeout", "1.5"],
    ["break", "MeController:27", "--print", " "],
    ["break", "NoSuchController:1"],
  ];
  for (const argv of bad) assert.throws(() => parseBreakArgs(argv, roots), UsageError, argv.join(" "));
});

test("a hit renders source, locals, this, expressions, own frames, and a trimmed stack", () => {
  const hit: Hit = {
    n: 1,
    threadId: "12377",
    event: {
      thread: "http-nio-8080-exec-7",
      method: `${ME}.getMe()`,
      line: 27,
      bci: 8,
      source: "27            return ResponseEntity.ok(new UserProfile()",
    },
    stack: [
      { index: 1, method: `${ME}.getMe`, location: "MeController.java:27" },
      ...Array.from({ length: 40 }, (_, i) => ({ index: i + 2, method: `org.lib.Frame${i}.run`, location: null })),
      { index: 42, method: "io.github.noahhhx.mimos.api.support.RequestLoggingFilter.doFilterInternal", location: "RequestLoggingFilter.java:74" },
    ],
    locals: {
      arguments: [{ name: "id", value: "42" }],
      locals: [{ name: "profile", value: "instance of x.UserProfileRecord(id=12404)" }],
      unavailable: undefined,
    },
    self: { ok: true, value: "{\n    currentUser: instance of x.CurrentUserService(id=12406)\n}" },
    prints: [
      { expr: "profile.displayName()", result: { ok: true, value: '"test"' } },
      { expr: "nope", result: { ok: false, error: "ParseException: Name unknown: nope" } },
    ],
  };
  const text = formatHit(hit);
  assert.match(text, /^## Hit 1 — `api\.identity\.MeController\.getMe\(\)` line 27, thread `http-nio-8080-exec-7`/);
  assert.match(text, /```java\n27 {12}return ResponseEntity/);
  assert.match(text, /id = 42 {4}\(argument\)\nprofile = instance of x\.UserProfileRecord\(id=12404\)/);
  assert.match(text, /profile\.displayName\(\) = "test"\nnope = error: ParseException: Name unknown: nope/);
  assert.match(text, /### Mimos frames\n\n```\n\[1\] api\.identity\.MeController\.getMe \(MeController\.java:27\)\n\[42\] api\.support\.RequestLoggingFilter/);
  assert.match(text, /### Stack \(25 of 42 frames\)/);
  assert.match(text, /… 17 more in jdb\.log/);

  const bare = formatHit({ ...hit, prints: [], locals: { arguments: [], locals: [], unavailable: undefined }, stack: [] });
  assert.match(bare, /\(none in scope\)/);
  assert.doesNotMatch(bare, /### Expressions|### Mimos frames/);
});
