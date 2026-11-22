import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

import { flowNames, FLOWS_DIR } from "./maestro.ts";

/**
 * Splits the Android flows across CI's shards so each takes about as long, by each flow's seconds in
 * apps/mobile/flows/durations.json (CI's medians). A flow missing from it weighs the table's median, so a
 * new flow still runs; a stale table costs balance, never coverage.
 */

export interface WeightedFlow {
  name: string;
  seconds: number;
}

export const DURATIONS_FILE = "durations.json";

export function weighFlows(names: readonly string[], durations: Readonly<Record<string, number>>): WeightedFlow[] {
  const known = Object.values(durations).sort((a, b) => a - b);
  const fallback = known[Math.floor(known.length / 2)] ?? 1;
  return names.map((name) => ({ name, seconds: durations[name] ?? fallback }));
}

/** Longest first, each to the shard with least so far; ties go by name, so every shard computes the same split. */
export function assignShards(flows: readonly WeightedFlow[], count: number): string[][] {
  if (!Number.isInteger(count) || count < 1) throw new RangeError(`shard count must be a positive integer, got ${count}`);
  const shards = Array.from({ length: count }, () => ({ load: 0, names: [] as string[] }));
  const ordered = [...flows].sort((a, b) => b.seconds - a.seconds || a.name.localeCompare(b.name));
  for (const flow of ordered) {
    const lightest = shards.reduce((best, shard) => (shard.load < best.load ? shard : best));
    lightest.load += flow.seconds;
    lightest.names.push(flow.name);
  }
  return shards.map((shard) => shard.names.sort());
}

export function flowsOfShard(shard: number, count: number, dir = FLOWS_DIR): string[] {
  if (!Number.isInteger(shard) || shard < 1 || shard > count) throw new RangeError(`shard must be 1 to ${count}, got ${shard}`);
  const file = join(dir, DURATIONS_FILE);
  const durations = existsSync(file) ? (JSON.parse(readFileSync(file, "utf8")) as Record<string, number>) : {};
  return assignShards(weighFlows(flowNames(dir), durations), count)[shard - 1] ?? [];
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [shard, count] = process.argv.slice(2).map(Number);
  if (shard === undefined || count === undefined) {
    process.stderr.write("usage: node tools/harness/src/shards.ts <shard> <count>\n");
    process.exit(2);
  }
  process.stdout.write(`${flowsOfShard(shard, count).join("\n")}\n`);
}
