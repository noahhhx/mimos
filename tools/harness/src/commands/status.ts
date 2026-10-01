import { parseCommandArgs } from "../args.ts";
import type { Command } from "../command.ts";
import { containerImageLabels, ps } from "../compose.ts";
import { BUILD_INPUTS, composeProject, REPO_ROOT } from "../config.ts";
import { exec } from "../exec.ts";
import { freshness, gitProbe, latestSourceChange } from "../stale.ts";

/** Per-service state and health, and whether built images trail their sources. */

/** The daemon's platform (`linux/amd64`), or undefined when docker cannot say. */
async function daemonPlatform(): Promise<string | undefined> {
  const result = await exec("docker", ["version", "--format", "{{.Server.Os}}/{{.Server.Arch}}"]);
  return result.code === 0 ? result.stdout.trim() || undefined : undefined;
}

/**
 * The image's ID and when a build last produced it: the later of Created and
 * LastTagTime. A build whose output is identical to an existing image (say,
 * only a build stage changed) reuses that image — keeping its old Created —
 * but re-tags it.
 *
 * The ID is the one compose labels containers with (compose.ts IMAGE_LABEL),
 * so comparing the two answers "would harness up recreate this container":
 * inspected for the daemon's platform, which on the containerd image store
 * is the platform manifest's digest rather than the index's. Where
 * `--platform` is unsupported, the plain ID — the classic store's, which
 * compose uses there.
 */
async function imageInfo(image: string, platform: string | undefined): Promise<{ id: string; created: Date } | undefined> {
  let result = await exec("docker", ["image", "inspect", ...(platform ? ["--platform", platform] : []), image]);
  if (result.code !== 0 && platform) result = await exec("docker", ["image", "inspect", image]);
  if (result.code !== 0) return undefined;
  const [info] = JSON.parse(result.stdout) as { Id: string; Created: string; Metadata?: { LastTagTime?: string } }[];
  if (!info) return undefined;
  const times = [info.Created, info.Metadata?.LastTagTime ?? ""].map(Date.parse).filter((time) => !Number.isNaN(time));
  return times.length ? { id: info.Id, created: new Date(Math.max(...times)) } : undefined;
}

export function age(from: Date, to: Date): string {
  const minutes = Math.round((to.getTime() - from.getTime()) / 60_000);
  if (minutes < 60) return `${minutes}m ago`;
  if (minutes < 48 * 60) return `${Math.round(minutes / 60)}h ago`;
  return `${Math.round(minutes / 1440)}d ago`;
}

function table(rows: string[][]): string {
  const widths = rows[0]!.map((_, column) => Math.max(...rows.map((row) => row[column]!.length)));
  return rows.map((row) => row.map((cell, column) => cell.padEnd(widths[column]!)).join("  ").trimEnd()).join("\n");
}

export interface ServiceReport {
  service: string;
  state: string;
  health: string;
  /** When a build last produced the image; undefined for pulled images and unknown build times. */
  built: Date | undefined;
  /** `fresh`, `stale`, `not built`, `undated`, `pulled` — plus `, container on older image`. */
  verdict: string;
}

/** Every service's state and image freshness, plus one note per problem (stale, undated, outdated container). */
export async function serviceReports(now: Date): Promise<{ reports: ServiceReport[]; notes: string[] }> {
  const [states, labels, platform] = await Promise.all([
    ps(),
    containerImageLabels().catch(() => new Map<string, string>()),
    daemonPlatform(),
  ]);
  const probe = gitProbe(REPO_ROOT);
  const notes: string[] = [];
  const reports: ServiceReport[] = [];

  const names = [...new Set([...states.map((state) => state.service), ...Object.keys(BUILD_INPUTS)])];
  for (const service of names) {
    const state = states.find((candidate) => candidate.service === service);
    const inputs = BUILD_INPUTS[service];
    let built: Date | undefined;
    let verdict = "pulled";
    if (inputs) {
      const image = await imageInfo(`${composeProject()}-${service}`, platform);
      const result = freshness(image?.created, await latestSourceChange(inputs.paths, probe));
      built = result.kind === "undated" ? undefined : image?.created;
      verdict = result.kind === "no-image" ? "not built" : result.kind;
      if (result.kind === "undated") {
        notes.push(`${service}: the image was built with SOURCE_DATE_EPOCH set, so its build time is unknown`);
      }
      if (result.kind === "stale") {
        notes.push(`${service}: ${result.change.path} changed ${age(result.change.time, now)}, after the image was built`);
      }
      const inUse = labels.get(service);
      if (image && inUse && inUse !== image.id) {
        verdict += ", container on older image";
        notes.push(`${service}: the container runs an older image than the latest build — harness up recreates it`);
      }
    }
    reports.push({ service, state: state?.state ?? "absent", health: state?.health ?? "", built, verdict });
  }
  return { reports, notes };
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
    const { reports, notes } = await serviceReports(now);
    const rows = [["SERVICE", "STATE", "HEALTH", "IMAGE BUILT", "IMAGE"]];
    for (const report of reports) {
      const built = report.built ? age(report.built, now) : report.verdict === "pulled" ? "—" : report.verdict.startsWith("undated") ? "unknown" : "never";
      rows.push([report.service, report.state, report.health || "—", built, report.verdict]);
    }

    process.stdout.write(`${table(rows)}\n`);
    if (notes.length) {
      process.stdout.write(`\n${notes.join("\n")}\n\nRun \`harness up\` to rebuild and restart.\n`);
    }
    return 0;
  },
};
