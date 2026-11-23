import { join } from "node:path";

import { parseCommandArgs, UsageError } from "../args.ts";
import type { Command } from "../command.ts";
import { REPO_ROOT, RUNS_DIR } from "../config.ts";
import {
  DEFAULT_RETENTION,
  diskUsage,
  formatBytes,
  latestRun,
  listRuns,
  planPrune,
  removeRuns,
  type RetentionPolicy,
} from "../prune.ts";

export interface PruneArgs {
  policy: RetentionPolicy;
  dryRun: boolean;
}

function count(flag: string, raw: string | undefined, fallback: number): number {
  if (raw === undefined) return fallback;
  if (!/^\d+$/.test(raw)) throw new UsageError(`--${flag} expects a whole number, got: ${raw}`);
  return Number(raw);
}

export function parsePruneArgs(argv: string[]): PruneArgs {
  const { values } = parseCommandArgs({
    args: argv,
    options: { keep: { type: "string" }, days: { type: "string" }, "dry-run": { type: "boolean" } },
  });
  return {
    policy: {
      keepNewest: count("keep", values.keep, DEFAULT_RETENTION.keepNewest),
      keepDays: count("days", values.days, DEFAULT_RETENTION.keepDays),
    },
    dryRun: values["dry-run"] ?? false,
  };
}

export const pruneCommand: Command = {
  name: "prune",
  summary: "Delete old run folders under .harness/runs/",
  usage: `harness prune [--keep <n>] [--days <n>] [--dry-run]

  --keep <n>   keep the <n> most recently used runs (default ${DEFAULT_RETENTION.keepNewest})
  --days <n>   keep every run used in the last <n> days (default ${DEFAULT_RETENTION.keepDays})
  --dry-run    list what would be deleted and the space it would free; delete nothing

A run survives if any rule keeps it, and the run \`latest\` points at always does. A run's
age is its summary.md's last change, so one appended to with --run counts as new. Only
folders named like runs (<UTC timestamp>-<name>) are considered; nothing else under
.harness/ is touched, the emulator in .harness/android/ included. Prints each deleted run
and its size, then the total. Running it again deletes nothing new. Writes no run folder.`,
  async run(argv) {
    const { policy, dryRun } = parsePruneArgs(argv);
    const runsDir = join(REPO_ROOT, RUNS_DIR);
    const plan = planPrune(listRuns(runsDir), latestRun(runsDir), policy, new Date());
    let total = 0;
    for (const run of plan.remove) {
      const bytes = diskUsage(run.dir);
      total += bytes;
      process.stdout.write(`${run.name}  ${formatBytes(bytes)}\n`);
    }
    if (!dryRun) removeRuns(plan.remove);
    const verb = dryRun ? "would delete" : "deleted";
    process.stdout.write(
      `${verb} ${plan.remove.length} runs, ${formatBytes(total)}; kept ${plan.keep.length} ` +
        `(newest ${policy.keepNewest}, last ${policy.keepDays} days, and latest)\n`,
    );
    return 0;
  },
};
