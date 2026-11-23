import assert from "node:assert/strict";
import { test } from "node:test";

import { envLines, freshUsername, nextMonday, userRepresentation } from "../src/accounts.ts";
import { UsageError } from "../src/args.ts";
import { parseHouseholdArgs, householdEnv } from "../src/commands/household.ts";
import { parseUserArgs, userEnv } from "../src/commands/user.ts";
import { FRESH_USER, passwordOf } from "../src/config.ts";
import { passwordFor, parseTokenArgs } from "../src/commands/token.ts";

test("fresh usernames match what the harness accepts for them", () => {
  assert.match(freshUsername(), FRESH_USER);
  assert.equal(
    freshUsername(() => "0a1b2c3d"),
    "user-0a1b2c3d",
  );
});

test("next week starts on the Monday after today's week, across months and years", () => {
  assert.equal(nextMonday(new Date(2026, 10, 19)), "2026-11-23"); // a Thursday
  assert.equal(nextMonday(new Date(2026, 10, 16, 0, 5)), "2026-11-23"); // a Monday, just after midnight
  assert.equal(nextMonday(new Date(2026, 10, 22, 23, 55)), "2026-11-23"); // a Sunday, just before midnight
  assert.equal(nextMonday(new Date(2026, 11, 30)), "2027-01-04");
  assert.equal(nextMonday(new Date(2028, 1, 25)), "2028-02-28");
});

test("a new account is enabled with a verified email and a permanent password", () => {
  const user = userRepresentation("user-0a1b2c3d") as {
    username: string;
    enabled: boolean;
    email: string;
    emailVerified: boolean;
    credentials: { type: string; value: string; temporary: boolean }[];
  };
  assert.equal(user.username, "user-0a1b2c3d");
  assert.equal(user.enabled, true);
  assert.equal(user.email, "user-0a1b2c3d@example.com");
  assert.equal(user.emailVerified, true);
  assert.deepEqual(user.credentials, [{ type: "password", value: "mimos-test", temporary: false }]);
});

test("accounts: the realm's test users and made accounts have passwords; other names do not", () => {
  assert.equal(passwordOf("test"), "mimos-test");
  assert.equal(passwordOf("user-0a1b2c3d"), "mimos-test");
  assert.equal(passwordOf("user-xyz"), undefined);
  assert.equal(passwordOf("admin"), undefined);
  assert.equal(passwordFor("user-0a1b2c3d"), "mimos-test");
  assert.throws(() => passwordFor("nobody"), UsageError);
  assert.equal(parseTokenArgs(["--as", "user-0a1b2c3d"]).user, "user-0a1b2c3d");
});

test("env output is one KEY=value per line", () => {
  assert.equal(envLines({ A: "1", B: "two" }), "A=1\nB=two");
  assert.equal(envLines(userEnv("user-0a1b2c3d")), "MIMOS_USER=user-0a1b2c3d\nMIMOS_PASSWORD=mimos-test");
});

test("user: only create", () => {
  assert.equal(parseUserArgs(["create"]), "create");
  assert.throws(() => parseUserArgs([]), UsageError);
  assert.throws(() => parseUserArgs(["delete"]), UsageError);
  assert.throws(() => parseUserArgs(["create", "x"]), UsageError);
});

test("household join: an owner and a different member, each a known user", () => {
  const args = parseHouseholdArgs(["join", "user-0a1b2c3d", "test2"]);
  assert.deepEqual(args, { owner: "user-0a1b2c3d", member: "test2" });
  assert.equal(
    envLines(householdEnv(args)),
    [
      "MIMOS_OWNER=user-0a1b2c3d",
      "MIMOS_OWNER_PASSWORD=mimos-test",
      "MIMOS_MEMBER=test2",
      "MIMOS_MEMBER_PASSWORD=mimos-test",
    ].join("\n"),
  );
  for (const argv of [
    [],
    ["join"],
    ["join", "test"],
    ["join", "test", "test"],
    ["join", "test", "nobody"],
    ["leave", "test", "test2"],
    ["join", "test", "test2", "x"],
  ]) {
    assert.throws(() => parseHouseholdArgs(argv), UsageError, argv.join(" "));
  }
});
