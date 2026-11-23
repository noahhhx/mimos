import { existsSync, lstatSync, readdirSync, readlinkSync, rmSync, statSync } from "node:fs";
import { basename, join } from "node:path";

import { RUN_NAME } from "./run.ts";

/**
 * Old run folders are removed by a retention policy; nothing else under
 * .harness/ is touched. Only entries named as `runName` names them count as
 * runs, so `latest`, its temporary links, and anything a person put there stay.
 */

export interface RunFolder {
  name: string;
  dir: string;
  /** When the run was last written: summary.md's mtime, which every command appends to (`--run` included). */
  lastUsed: Date;
}

export interface RetentionPolicy {
  /** Keep this many of the most recently used runs. */
  keepNewest: number;
  /** Keep every run used within this many days. */
  keepDays: number;
}

export const DEFAULT_RETENTION: RetentionPolicy = { keepNewest: 50, keepDays: 7 };

export interface PrunePlan {
  keep: RunFolder[];
  remove: RunFolder[];
}

export function listRuns(runsDir: string): RunFolder[] {
  if (!existsSync(runsDir)) return [];
  return readdirSync(runsDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && RUN_NAME.test(entry.name))
    .map((entry) => {
      const dir = join(runsDir, entry.name);
      const summary = join(dir, "summary.md");
      return { name: entry.name, dir, lastUsed: statSync(existsSync(summary) ? summary : dir).mtime };
    });
}

/** The run `latest` points at, if it is a link into this directory. */
export function latestRun(runsDir: string): string | undefined {
  try {
    return basename(readlinkSync(join(runsDir, "latest")));
  } catch {
    return undefined;
  }
}

/** A run survives if `latest` names it, it is among the newest `keepNewest`, or it was used within `keepDays`. */
export function planPrune(
  runs: readonly RunFolder[],
  latest: string | undefined,
  policy: RetentionPolicy,
  now: Date,
): PrunePlan {
  const cutoff = now.getTime() - policy.keepDays * 86_400_000;
  const newestFirst = [...runs].sort(
    (a, b) => b.lastUsed.getTime() - a.lastUsed.getTime() || b.name.localeCompare(a.name),
  );
  const plan: PrunePlan = { keep: [], remove: [] };
  newestFirst.forEach((run, index) => {
    const kept = run.name === latest || index < policy.keepNewest || run.lastUsed.getTime() >= cutoff;
    (kept ? plan.keep : plan.remove).push(run);
  });
  return plan;
}

/** Bytes on disk under `path` (allocated blocks, as du counts them); links are counted, never followed. */
export function diskUsage(path: string): number {
  const stat = lstatSync(path, { throwIfNoEntry: false });
  if (!stat) return 0;
  let bytes = stat.blocks * 512;
  if (stat.isDirectory()) {
    for (const entry of readdirSync(path)) bytes += diskUsage(join(path, entry));
  }
  return bytes;
}

/** Removes each run; one already gone (an earlier, interrupted prune) is not an error. */
export function removeRuns(runs: readonly RunFolder[]): void {
  for (const run of runs) rmSync(run.dir, { recursive: true, force: true });
}

export function formatBytes(bytes: number): string {
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return unit === 0 ? `${value} B` : `${value.toFixed(1)} ${units[unit]}`;
}
