import assert from "node:assert/strict";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  rmSync,
  symlinkSync,
  utimesSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, test } from "node:test";

import { UsageError } from "../src/args.ts";
import { parsePruneArgs } from "../src/commands/prune.ts";
import { diskUsage, formatBytes, latestRun, listRuns, planPrune, removeRuns, type RunFolder } from "../src/prune.ts";
import { linkLatest, RUN_NAME, runName } from "../src/run.ts";

const scratch = mkdtempSync(join(tmpdir(), "harness-prune-"));
after(() => rmSync(scratch, { recursive: true, force: true }));

const NOW = new Date("2026-11-23T12:00:00Z");
const DAY = 86_400_000;

function daysAgo(days: number): Date {
  return new Date(NOW.getTime() - days * DAY);
}

/** A runs directory with one run per entry, each last used `days` ago. */
function runsDir(runs: Record<string, number>): string {
  const dir = mkdtempSync(join(scratch, "runs-"));
  for (const [name, days] of Object.entries(runs)) {
    mkdirSync(join(dir, name));
    writeFileSync(join(dir, name, "summary.md"), `# Run ${name}\n`);
    writeFileSync(join(dir, name, "trace.zip"), Buffer.alloc(8192, 1));
    utimesSync(join(dir, name, "summary.md"), daysAgo(days), daysAgo(days));
  }
  return dir;
}

function run(name: string, days: number): RunFolder {
  return { name, dir: `/runs/${name}`, lastUsed: daysAgo(days) };
}

const names = (runs: readonly RunFolder[]) => runs.map((each) => each.name).sort();

test("run names match RUN_NAME, and nothing else in the runs directory does", () => {
  assert.match(runName(NOW, "mobile flow"), RUN_NAME);
  assert.match(`${runName(NOW, "up")}-2`, RUN_NAME);
  assert.match(runName(NOW, "///"), RUN_NAME);
  for (const other of ["latest", ".latest-1234", "notes", "2026-11-23"]) assert.doesNotMatch(other, RUN_NAME);
});

test("a run is kept when it is among the newest, used recently, or latest; the rest go", () => {
  const runs = [run("a-old", 30), run("b-old", 20), run("c-latest", 40), run("d-week", 3), run("e-new", 10)];
  const plan = planPrune(runs, "c-latest", { keepNewest: 1, keepDays: 7 }, NOW);
  assert.deepEqual(names(plan.keep), ["c-latest", "d-week"]);
  assert.deepEqual(names(plan.remove), ["a-old", "b-old", "e-new"]);

  const wider = planPrune(runs, "c-latest", { keepNewest: 3, keepDays: 7 }, NOW);
  assert.deepEqual(names(wider.remove), ["a-old"]);
});

test("newest is by last use, not by the name's timestamp", () => {
  const runs = [run("2026-10-01T00-00-00Z-appended", 1), run("2026-11-01T00-00-00Z-untouched", 15)];
  const plan = planPrune(runs, undefined, { keepNewest: 1, keepDays: 0 }, NOW);
  assert.deepEqual(names(plan.keep), ["2026-10-01T00-00-00Z-appended"]);
});

test("keep 0 and days 0 still keep latest", () => {
  const plan = planPrune([run("x", 9), run("y", 9)], "y", { keepNewest: 0, keepDays: 0 }, NOW);
  assert.deepEqual(names(plan.keep), ["y"]);
  assert.deepEqual(names(plan.remove), ["x"]);
});

test("listing reads run folders only, aged by summary.md, and finds latest through its link", () => {
  const dir = runsDir({ "2026-10-01T15-24-18Z-up": 50, "2026-11-22T12-37-01Z-api": 1 });
  mkdirSync(join(dir, "keep-me"));
  writeFileSync(join(dir, "2026-10-02T00-00-00Z-file"), "not a folder");
  linkLatest(dir, "2026-10-01T15-24-18Z-up");
  symlinkSync("2026-11-22T12-37-01Z-api", join(dir, ".latest-999"));

  const runs = listRuns(dir);
  assert.deepEqual(names(runs), ["2026-10-01T15-24-18Z-up", "2026-11-22T12-37-01Z-api"]);
  assert.equal(runs.find((each) => each.name === "2026-11-22T12-37-01Z-api")?.lastUsed.getTime(), daysAgo(1).getTime());
  assert.equal(latestRun(dir), "2026-10-01T15-24-18Z-up");
  assert.deepEqual(listRuns(join(scratch, "missing")), []);
  assert.equal(latestRun(join(scratch, "missing")), undefined);
});

test("pruning deletes only the planned runs, and a second prune converges with nothing to do", () => {
  const dir = runsDir({
    "2026-09-01T00-00-00Z-a": 80,
    "2026-09-02T00-00-00Z-b": 79,
    "2026-09-03T00-00-00Z-latest": 78,
    "2026-11-20T00-00-00Z-c": 3,
  });
  mkdirSync(join(dir, "keep-me"));
  linkLatest(dir, "2026-09-03T00-00-00Z-latest");
  const policy = { keepNewest: 0, keepDays: 7 };

  const first = planPrune(listRuns(dir), latestRun(dir), policy, NOW);
  assert.deepEqual(names(first.remove), ["2026-09-01T00-00-00Z-a", "2026-09-02T00-00-00Z-b"]);
  removeRuns(first.remove);
  assert.deepEqual(readdirSync(dir).sort(), [
    "2026-09-03T00-00-00Z-latest",
    "2026-11-20T00-00-00Z-c",
    "keep-me",
    "latest",
  ]);

  removeRuns(first.remove);
  assert.deepEqual(planPrune(listRuns(dir), latestRun(dir), policy, NOW).remove, []);
  assert.ok(existsSync(join(dir, "latest", "summary.md")));
});

test("disk usage counts a run's files, and a missing path is 0", () => {
  const dir = runsDir({ "2026-09-01T00-00-00Z-a": 1 });
  assert.ok(diskUsage(join(dir, "2026-09-01T00-00-00Z-a")) >= 8192);
  assert.equal(diskUsage(join(dir, "gone")), 0);
});

test("sizes read in binary units", () => {
  assert.equal(formatBytes(512), "512 B");
  assert.equal(formatBytes(1536), "1.5 KB");
  assert.equal(formatBytes(7.9 * 1024 ** 3), "7.9 GB");
});

test("prune's options default to 50 runs and 7 days, and take whole numbers only", () => {
  assert.deepEqual(parsePruneArgs([]), { policy: { keepNewest: 50, keepDays: 7 }, dryRun: false });
  assert.deepEqual(parsePruneArgs(["--keep", "0", "--days", "30", "--dry-run"]), {
    policy: { keepNewest: 0, keepDays: 30 },
    dryRun: true,
  });
  assert.throws(() => parsePruneArgs(["--keep", "-1"]), UsageError);
  assert.throws(() => parsePruneArgs(["--days", "1.5"]), UsageError);
  assert.throws(() => parsePruneArgs(["--all"]), UsageError);
});
