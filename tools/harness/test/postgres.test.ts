import assert from "node:assert/strict";
import { test } from "node:test";

import { migrationReport, migrationScripts, parseCsv, psqlArgs, READ_ONLY } from "../src/postgres.ts";

test("psql is read-only unless asked to write", () => {
  const readOnly = psqlArgs("select 1");
  assert.deepEqual(readOnly.slice(0, 5), ["exec", "-T", "-e", `PGOPTIONS=${READ_ONLY}`, "postgres"]);
  assert.equal(READ_ONLY, "-c default_transaction_read_only=on");
  assert.deepEqual(readOnly.slice(-2), ["-c", "select 1"]);
  assert.ok(readOnly.includes("ON_ERROR_STOP=1"), "a failing statement must fail the call");
  assert.ok(!readOnly.includes("--csv"));

  const write = psqlArgs("delete from t", { write: true, csv: true });
  assert.ok(!write.some((arg) => arg.startsWith("PGOPTIONS")));
  assert.ok(write.includes("--csv"));
});

test("psql connects as compose's database user", () => {
  const args = psqlArgs("select 1");
  assert.deepEqual(args.slice(args.indexOf("-U"), args.indexOf("-U") + 4), ["-U", "mimos", "-d", "mimos"]);
});

test("CSV: quoted commas, quotes, and line breaks; trailing newline optional", () => {
  assert.deepEqual(parseCsv('a,b\n1,"x,""y"""\n2,"two\nlines"\n'), [
    { a: "1", b: 'x,"y"' },
    { a: "2", b: "two\nlines" },
  ]);
  assert.deepEqual(parseCsv("a,b\r\n3,\r\n"), [{ a: "3", b: "" }]);
  assert.deepEqual(parseCsv("a,b\n4,5"), [{ a: "4", b: "5" }]);
  assert.deepEqual(parseCsv("a,b\n"), []);
  assert.deepEqual(parseCsv(""), []);
});

test("migrations: failed rows and repo scripts never applied are reported", () => {
  const history = [
    { version: "1", script: "V1__baseline.sql", success: "t" },
    { version: "2", script: "V2__user_profiles.sql", success: "f" },
  ];
  assert.deepEqual(migrationReport(history, ["V1__baseline.sql", "V2__user_profiles.sql", "V3__recipes.sql"]), {
    applied: 1,
    failed: ["V2__user_profiles.sql"],
    pending: ["V2__user_profiles.sql", "V3__recipes.sql"],
  });
  assert.deepEqual(migrationReport(history.slice(0, 1), ["V1__baseline.sql"]), { applied: 1, failed: [], pending: [] });
});

test("migration scripts are the repo's versioned migrations, in version order", () => {
  const scripts = migrationScripts();
  assert.equal(scripts[0], "V1__baseline.sql");
  assert.ok(scripts.every((script) => /^V\d+__.+\.sql$/.test(script)));
  const versions = scripts.map((script) => Number(/^V(\d+)/.exec(script)?.[1]));
  assert.deepEqual(versions, [...versions].sort((a, b) => a - b));
});
