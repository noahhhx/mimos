import { HarnessError } from "./args.ts";
import { AVD_SERIAL } from "./avd.ts";
import type { Endpoints } from "./config.ts";
import { exec, type ExecResult } from "./exec.ts";

/** The emulator over adb, plus the pure parsers for what adb prints. */

export interface Adb {
  run(args: readonly string[]): Promise<ExecResult>;
  /** Stdout of a command that must succeed. */
  ok(args: readonly string[]): Promise<string>;
  /** Stdout of `adb shell <command>`, with the carriage returns adb adds on a tty stripped. */
  shell(command: string): Promise<string>;
}

export function adb(serial = AVD_SERIAL, env: NodeJS.ProcessEnv = process.env): Adb {
  const run = (args: readonly string[]) => exec("adb", ["-s", serial, ...args], { env });
  const ok = async (args: readonly string[]): Promise<string> => {
    const result = await run(args);
    if (result.code !== 0) {
      throw new HarnessError(`adb ${args.join(" ")} failed (exit ${result.code}):\n${(result.stderr || result.stdout).trim()}`);
    }
    return result.stdout;
  };
  return { run, ok, shell: async (command) => (await ok(["shell", command])).replaceAll("\r", "") };
}

/** `adb devices` → serial to state (`device`, `offline`, `unauthorized`, …). */
export function parseDevices(output: string): Map<string, string> {
  const devices = new Map<string, string>();
  for (const line of output.split("\n").slice(1)) {
    const [serial, state] = line.trim().split(/\s+/);
    if (serial && state) devices.set(serial, state);
  }
  return devices;
}

/** `adb reverse --list` → the device-side ports being forwarded, sorted. */
export function parseReverseList(output: string): number[] {
  return output
    .split("\n")
    .flatMap((line) => {
      const match = /\btcp:(\d+)\s+tcp:\d+\s*$/.exec(line.trim());
      return match ? [Number(match[1])] : [];
    })
    .sort((a, b) => a - b);
}

export interface ProbeTarget {
  name: string;
  port: number;
  path: string;
}

function portOf(url: string): number {
  const parsed = new URL(url);
  return Number(parsed.port || (parsed.protocol === "https:" ? 443 : 80));
}

/** What the device must reach, one URL each: the web app, the API, and Keycloak's realm. Ports come from the same variables as compose. */
export function probeTargets(urls: Pick<Endpoints, "api" | "keycloak" | "web">, realm: string): ProbeTarget[] {
  return [
    { name: "web", port: portOf(urls.web), path: "/" },
    { name: "api", port: portOf(urls.api), path: "/actuator/health" },
    { name: "keycloak", port: portOf(urls.keycloak), path: `/realms/${realm}/.well-known/openid-configuration` },
  ];
}

/**
 * A real HTTP request from the device's own userspace, through adbd's reverse
 * listener. toybox nc quits when stdin ends, so the request is followed by a
 * sleep that keeps stdin open until the response has arrived.
 */
export function probeCommand({ port, path }: ProbeTarget): string {
  return `(printf 'GET ${path} HTTP/1.0\\r\\nHost: localhost:${port}\\r\\nConnection: close\\r\\n\\r\\n'; sleep 3) | nc -w 5 127.0.0.1 ${port} | head -n 1`;
}

/** The status code in a response's first line; undefined when there was no HTTP response (a refused or closed connection). */
export function parseProbeStatus(output: string): number | undefined {
  const match = /^HTTP\/\d(?:\.\d)?\s+(\d{3})\b/.exec(output.trim());
  return match ? Number(match[1]) : undefined;
}

export interface ProbeResult extends ProbeTarget {
  status: number | undefined;
}

export const probeOk = (result: ProbeResult): boolean => result.status !== undefined && result.status >= 200 && result.status < 400;

export async function probe(device: Adb, target: ProbeTarget): Promise<ProbeResult> {
  return { ...target, status: parseProbeStatus(await device.shell(probeCommand(target))) };
}

/** The package whose "isn't responding" dialog has the focus, from `dumpsys window`; undefined when none has. */
export function notRespondingApp(dumpsysWindow: string): string | undefined {
  return /mCurrentFocus=Window\{\S+ u\d+ Application Not Responding: ([\w.]+)\}/.exec(dumpsysWindow)?.[1];
}

/** The centre of the dialog's Close app button (`android:id/aerr_close`, in any language) in a `uiautomator dump`. */
export function closeAppButton(hierarchy: string): { x: number; y: number } | undefined {
  for (const node of hierarchy.match(/<node\b[^>]*>/g) ?? []) {
    if (!/\bresource-id="android:id\/aerr_close"/.test(node)) continue;
    const bounds = /\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/.exec(node);
    if (bounds === null) continue;
    const [left, top, right, bottom] = bounds.slice(1).map(Number) as [number, number, number, number];
    return { x: Math.round((left + right) / 2), y: Math.round((top + bottom) / 2) };
  }
  return undefined;
}
