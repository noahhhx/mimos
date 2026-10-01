import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readFileSync, readlinkSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, test } from "node:test";

import { HarnessError } from "../src/args.ts";
import { appendJsonLine, appendSummary, openRun, recordInvocation, runName, runStartedAt } from "../src/run.ts";

const runsDir = mkdtempSync(join(tmpdir(), "harness-runs-"));
after(() => rmSync(runsDir, { recursive: true, force: true }));

const NOW = new Date("2026-10-01T15:04:12.345Z");

test("run names are a UTC timestamp plus a slug of the label", () => {
  assert.equal(runName(NOW, "api"), "2026-10-01T15-04-12Z-api");
  assert.equal(runName(NOW, "Create Recipe!"), "2026-10-01T15-04-12Z-create-recipe");
  assert.equal(runName(NOW, "///"), "2026-10-01T15-04-12Z");
});

test("a new run gets a folder, a summary, and the latest link; same-second runs don't collide", () => {
  const first = openRun(runsDir, "up", NOW);
  assert.equal(first.name, "2026-10-01T15-04-12Z-up");
  assert.ok(first.created);
  assert.match(readFileSync(join(first.dir, "summary.md"), "utf8"), /^# Run 2026-10-01T15-04-12Z-up/);
  assert.equal(readlinkSync(join(runsDir, "latest")), first.name);

  const second = openRun(runsDir, "up", NOW);
  assert.equal(second.name, "2026-10-01T15-04-12Z-up-2");
  assert.equal(readlinkSync(join(runsDir, "latest")), second.name);
});

test("--run appends to an existing run by name, by path, or via latest", () => {
  const original = openRun(runsDir, "api", new Date("2026-10-01T16:00:00Z"));
  openRun(runsDir, "other", new Date("2026-10-01T16:00:01Z"));

  const byName = openRun(runsDir, "api", NOW, original.name);
  assert.equal(byName.dir, original.dir);
  assert.ok(!byName.created);
  assert.equal(readlinkSync(join(runsDir, "latest")), original.name);

  const viaLatest = openRun(runsDir, "api", NOW, "latest");
  assert.equal(viaLatest.name, original.name);

  assert.equal(openRun(runsDir, "api", NOW, original.dir).name, original.name);
  assert.throws(() => openRun(runsDir, "api", NOW, "no-such-run"), HarnessError);
});

test("evidence files: numbered JSON lines, appended summary, invocations in order", () => {
  const run = openRun(runsDir, "evidence", NOW);
  assert.equal(appendJsonLine(run, "api/exchanges.jsonl", { n: 1 }), 1);
  assert.equal(appendJsonLine(run, "api/exchanges.jsonl", { n: 2 }), 2);
  assert.equal(readFileSync(join(run.dir, "api/exchanges.jsonl"), "utf8"), '{"n":1}\n{"n":2}\n');

  appendSummary(run, "## first\n");
  assert.match(readFileSync(join(run.dir, "summary.md"), "utf8"), /\n## first\n$/);

  assert.equal(runStartedAt(run, NOW), NOW);
  const invocation = {
    argv: ["api", "GET", "/api/v1/me"],
    startedAt: "2026-10-01T15:00:00.000Z",
    finishedAt: "2026-10-01T15:00:01.000Z",
    exitCode: 0,
    git: { sha: "abc", dirty: false },
    images: [],
  };
  recordInvocation(run, invocation);
  recordInvocation(run, { ...invocation, startedAt: "2026-10-01T15:05:00.000Z" });
  const recorded = JSON.parse(readFileSync(join(run.dir, "command.json"), "utf8")) as unknown[];
  assert.equal(recorded.length, 2);
  assert.deepEqual(runStartedAt(run, NOW), new Date("2026-10-01T15:00:00.000Z"));
  assert.ok(existsSync(join(runsDir, "latest")));
});
