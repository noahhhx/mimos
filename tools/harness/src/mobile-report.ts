import { displayCommand } from "./args.ts";
import { fenced } from "./command.ts";
import { probeOk, type ProbeResult } from "./adb.ts";

/** What `harness mobile` writes into summary.md. Pure: the commands gather the facts, these render them. */

export interface StepRecord {
  name: string;
  ok: boolean;
  detail: string;
  ms: number;
}

const seconds = (ms: number): string => `${(ms / 1000).toFixed(1)} s`;

export function renderProbes(probes: readonly ProbeResult[]): string[] {
  return probes.map((probe) => {
    const outcome = probe.status === undefined ? "no HTTP response" : String(probe.status);
    return `- ${probeOk(probe) ? "✓" : "✗"} \`localhost:${probe.port}${probe.path}\` (${probe.name}) → ${outcome}`;
  });
}

export interface UpReport {
  argv: string[];
  steps: StepRecord[];
  probes: ProbeResult[];
  /** Run-relative paths of the evidence written. */
  files: string[];
  failure: string | undefined;
  ms: number;
}

export function renderUpSection(report: UpReport): string {
  const failed = report.failure !== undefined;
  const lines = [
    `## \`${displayCommand(["mobile", ...report.argv])}\` — ${failed ? "failed" : "passed"} (${seconds(report.ms)})`,
    "",
    ...report.steps.map((step) => `- ${step.ok ? "✓" : "✗"} **${step.name}** (${seconds(step.ms)})${step.detail ? `: ${step.detail}` : ""}`),
  ];
  if (report.probes.length > 0) {
    lines.push("", "Requests from the device, through `adb reverse`:", "", ...renderProbes(report.probes));
  }
  if (failed) lines.push("", "Failure:", "", fenced(report.failure ?? ""));
  if (report.files.length > 0) lines.push("", "Evidence:", "", ...report.files.map((file) => `- \`${file}\``));
  return lines.join("\n");
}

const LOGCAT_LINE = /^\S+\s+\S+\s+\d+\s+\d+\s+([VDIWEF])\s/;

export interface LogcatSummary {
  lines: number;
  /** Lines at level E or F, oldest first. */
  errors: string[];
}

export function summarizeLogcat(text: string): LogcatSummary {
  const lines = text.split("\n").filter((line) => line.trim() !== "" && !line.startsWith("-----"));
  return {
    lines: lines.length,
    errors: lines.filter((line) => {
      const level = LOGCAT_LINE.exec(line)?.[1];
      return level === "E" || level === "F";
    }),
  };
}

export interface LogcatReport {
  argv: string[];
  file: string;
  /** The app's process id; undefined when it was not running and lines were matched by package name instead. */
  pid: string | undefined;
  appId: string;
  summary: LogcatSummary;
}

export function renderLogcatSection(report: LogcatReport): string {
  const { summary } = report;
  const lines = [
    `## \`${displayCommand(["mobile", ...report.argv])}\``,
    "",
    report.pid === undefined
      ? `- **Scope:** \`${report.appId}\` is not running; lines that mention it, from the whole buffer`
      : `- **Scope:** \`${report.appId}\`, pid ${report.pid}`,
    `- **Logcat:** \`${report.file}\` — ${summary.lines} lines, ${summary.errors.length} at error or fatal`,
  ];
  if (summary.errors.length > 0) {
    lines.push("", "Last errors:", "", fenced(summary.errors.slice(-10).join("\n")));
  }
  return lines.join("\n");
}

export function renderShotSection(argv: string[], file: string): string {
  return [`## \`${displayCommand(["mobile", ...argv])}\``, "", `- **Screenshot:** \`${file}\``].join("\n");
}
