import { parseCommandArgs } from "../args.ts";
import type { Command } from "../command.ts";
import { containerImages, ps } from "../compose.ts";
import { BUILD_INPUTS, composeProject, REPO_ROOT } from "../config.ts";
import { exec } from "../exec.ts";
import { freshness, gitProbe, latestSourceChange } from "../stale.ts";

/** Per-service state and health, and whether built images trail their sources. */

/**
 * The image's ID and when a build last produced it: the later of Created and
 * LastTagTime. A build whose output is identical to an existing image (say,
 * only a build stage changed) reuses that image — keeping its old Created —
 * but re-tags it.
 */
async function imageInfo(image: string): Promise<{ id: string; created: Date } | undefined> {
  const result = await exec("docker", ["image", "inspect", image]);
  if (result.code !== 0) return undefined;
  const [info] = JSON.parse(result.stdout) as { Id: string; Created: string; Metadata?: { LastTagTime?: string } }[];
  if (!info) return undefined;
  const times = [info.Created, info.Metadata?.LastTagTime ?? ""].map(Date.parse).filter((time) => !Number.isNaN(time));
  return times.length ? { id: info.Id, created: new Date(Math.max(...times)) } : undefined;
}

function age(from: Date, to: Date): string {
  const minutes = Math.round((to.getTime() - from.getTime()) / 60_000);
  if (minutes < 60) return `${minutes}m ago`;
  if (minutes < 48 * 60) return `${Math.round(minutes / 60)}h ago`;
  return `${Math.round(minutes / 1440)}d ago`;
}

function table(rows: string[][]): string {
  const widths = rows[0]!.map((_, column) => Math.max(...rows.map((row) => row[column]!.length)));
  return rows.map((row) => row.map((cell, column) => cell.padEnd(widths[column]!)).join("  ").trimEnd()).join("\n");
}

export const statusCommand: Command = {
  name: "status",
  summary: "Show service state and health, and flag stale images",
  usage: `harness status

An image is stale when a file under its service's build inputs changed after the
image was built; run harness up (it always rebuilds) to refresh.`,
  async run(argv) {
    parseCommandArgs({ args: argv, options: {} });
    const now = new Date();
    const [states, running] = await Promise.all([ps(), containerImages().catch(() => [])]);
    const probe = gitProbe(REPO_ROOT);
    const notes: string[] = [];
    const rows = [["SERVICE", "STATE", "HEALTH", "IMAGE BUILT", "IMAGE"]];

    const names = [...new Set([...states.map((state) => state.service), ...Object.keys(BUILD_INPUTS)])];
    for (const service of names) {
      const state = states.find((candidate) => candidate.service === service);
      const inputs = BUILD_INPUTS[service];
      let built = "—";
      let verdict = "pulled";
      if (inputs) {
        const image = await imageInfo(`${composeProject()}-${service}`);
        const result = freshness(image?.created, await latestSourceChange(inputs.paths, probe));
        built = image ? (result.kind === "undated" ? "unknown" : age(image.created, now)) : "never";
        verdict = result.kind === "no-image" ? "not built" : result.kind;
        if (result.kind === "undated") {
          notes.push(`${service}: the image was built with SOURCE_DATE_EPOCH set, so its build time is unknown`);
        }
        if (result.kind === "stale") {
          notes.push(`${service}: ${result.change.path} changed ${age(result.change.time, now)}, after the image was built`);
        }
        const inUse = running.find((candidate) => candidate.service === service);
        if (image && inUse && inUse.id !== image.id) {
          verdict += ", container on older image";
          notes.push(`${service}: the container runs an older image than the latest build — harness up recreates it`);
        }
      }
      rows.push([service, state?.state ?? "absent", state?.health || "—", built, verdict]);
    }

    process.stdout.write(`${table(rows)}\n`);
    if (notes.length) {
      process.stdout.write(`\n${notes.join("\n")}\n\nRun \`harness up\` to rebuild and restart.\n`);
    }
    return 0;
  },
};
