import { existsSync } from "node:fs";
import { join } from "node:path";

import { HarnessError } from "./args.ts";
import { COMPOSE_FILE, DEBUG_OVERLAY, REPO_ROOT } from "./config.ts";
import { exec, execOk, type ExecOptions, type ExecResult } from "./exec.ts";
import { redactText } from "./redact.ts";
import { writeRunFile, type Run } from "./run.ts";

/** Thin wrapper over `docker compose` for the product stack (plus the debug overlay when asked). */

export function composeFiles(debug: boolean): string[] {
  const files = [join(REPO_ROOT, COMPOSE_FILE)];
  if (debug) {
    const overlay = join(REPO_ROOT, DEBUG_OVERLAY);
    if (!existsSync(overlay)) {
      throw new HarnessError(`--debug needs ${DEBUG_OVERLAY}, which arrives in harness step 3`);
    }
    files.push(overlay);
  }
  return files;
}

/**
 * The environment for compose, minus SOURCE_DATE_EPOCH: Nix shells (devenv
 * included) export it for reproducible builds, and BuildKit then stamps every
 * image as created in 1980 — which would defeat harness status.
 */
export function composeEnv(env: NodeJS.ProcessEnv = process.env): NodeJS.ProcessEnv {
  const { SOURCE_DATE_EPOCH: _ignored, ...rest } = env;
  return rest;
}

export function compose(args: readonly string[], options: ExecOptions & { debug?: boolean } = {}): Promise<ExecResult> {
  const files = composeFiles(options.debug ?? false).flatMap((file) => ["-f", file]);
  return exec("docker", ["compose", ...files, ...args], { cwd: REPO_ROOT, env: composeEnv(), ...options });
}

async function composeOk(args: readonly string[]): Promise<string> {
  const files = composeFiles(false).flatMap((file) => ["-f", file]);
  return execOk("docker", ["compose", ...files, ...args], { cwd: REPO_ROOT, env: composeEnv() });
}

/** Compose prints JSON as one array (older releases) or one object per line (newer). */
export function parseJsonOutput(text: string): Record<string, unknown>[] {
  const trimmed = text.trim();
  if (!trimmed) return [];
  if (trimmed.startsWith("[")) return JSON.parse(trimmed) as Record<string, unknown>[];
  return trimmed.split("\n").map((line) => JSON.parse(line) as Record<string, unknown>);
}

export interface ServiceState {
  service: string;
  container: string;
  state: string;
  /** Empty when the service has no healthcheck. */
  health: string;
  exitCode: number;
}

export async function services(): Promise<string[]> {
  return (await composeOk(["config", "--services"])).split("\n").filter(Boolean);
}

export async function ps(): Promise<ServiceState[]> {
  return parseJsonOutput(await composeOk(["ps", "--all", "--format", "json"])).map((row) => ({
    service: String(row.Service ?? ""),
    container: String(row.Name ?? ""),
    state: String(row.State ?? ""),
    health: String(row.Health ?? ""),
    exitCode: Number(row.ExitCode ?? 0),
  }));
}

/** Healthy, or running without a healthcheck. */
export function isHealthy(state: ServiceState): boolean {
  return state.state === "running" && (state.health === "" || state.health === "healthy");
}

/** The images the stack's containers actually run, by service. */
export async function containerImages(): Promise<{ service: string; image: string; id: string }[]> {
  const [states, rows] = await Promise.all([ps(), composeOk(["images", "--format", "json"])]);
  const serviceOf = new Map(states.map((state) => [state.container, state.service]));
  return parseJsonOutput(rows).map((row) => ({
    service: serviceOf.get(String(row.ContainerName)) ?? String(row.ContainerName),
    image: `${String(row.Repository)}:${String(row.Tag)}`,
    id: String(row.ID),
  }));
}

export interface CapturedLog {
  service: string;
  file: string;
  lines: number;
  /** The redacted log text as written. */
  text: string;
}

/** Writes each service's logs to `logs/<service>.log` (redacted, timestamped) and returns what was written. */
export async function captureLogs(
  run: Run,
  options: { services?: readonly string[]; since?: string } = {},
): Promise<CapturedLog[]> {
  const targets = [...(options.services?.length ? options.services : await services())].sort();
  return Promise.all(
    targets.map(async (service) => {
      const args = ["logs", "--no-color", "--no-log-prefix", "--timestamps"];
      if (options.since) args.push("--since", options.since);
      const result = await compose([...args, service]);
      if (result.code !== 0) {
        throw new HarnessError(`could not read logs for ${service}: ${result.stderr.trim()}`);
      }
      const text = redactText(result.stdout + result.stderr);
      const file = `logs/${service}.log`;
      writeRunFile(run, file, text);
      return { service, file, lines: text.split("\n").filter(Boolean).length, text };
    }),
  );
}

/** `lines` captured, `empty` — compact enough for one summary.md bullet. */
export function describeLogs(logs: readonly CapturedLog[]): string {
  const written = logs.filter((log) => log.lines > 0).map((log) => `\`${log.file}\` (${log.lines} ${log.lines === 1 ? "line" : "lines"})`);
  const empty = logs.filter((log) => log.lines === 0).map((log) => log.service);
  return [written.join(", "), empty.length ? `no output from ${empty.join(", ")}` : ""].filter(Boolean).join("; ");
}

/** Lines of a `--timestamps` log at or after `from` (compose prefixes each line with an RFC 3339 time). */
export function linesSince(text: string, from: Date): string[] {
  return text.split("\n").filter((line) => {
    const stamp = line.slice(0, line.indexOf(" "));
    const time = Date.parse(stamp);
    return !Number.isNaN(time) && time >= from.getTime();
  });
}

/** Git SHA and dirty flag for command.json; null when git is unavailable. */
export async function gitState(): Promise<{ sha: string | null; dirty: boolean | null }> {
  const [sha, status] = await Promise.all([
    exec("git", ["rev-parse", "HEAD"], { cwd: REPO_ROOT }).catch(() => undefined),
    exec("git", ["status", "--porcelain"], { cwd: REPO_ROOT }).catch(() => undefined),
  ]);
  return {
    sha: sha?.code === 0 ? sha.stdout.trim() : null,
    dirty: status?.code === 0 ? status.stdout.trim() !== "" : null,
  };
}
