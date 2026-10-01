import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative } from "node:path";
import { stripVTControlCharacters } from "node:util";

import type { JSONReport } from "@playwright/test/reporter";

import { displayCommand, parseCommandArgs, UsageError } from "../args.ts";
import { fenced, finishRun, RUN_OPTION, RUN_USAGE, startRun, type Command } from "../command.ts";
import { captureLogs, describeLogs, linesSince } from "../compose.ts";
import { DEFAULT_USER, endpoints, REPO_ROOT, USERS } from "../config.ts";
import { copyRedacted } from "../evidence.ts";
import { exec } from "../exec.ts";
import { apiCallTable, exchanges, failureDetails, isFailure, type Exchange, type Har } from "../har.ts";
import { checkBrowsers, globalErrors, playwrightCli, scenarioTests, type ScenarioTest } from "../playwright.ts";
import { redactText } from "../redact.ts";
import { appendSummary, runStartedAt, writeRunFile, type Run } from "../run.ts";
import { passwordFor } from "./token.ts";

/** A Playwright scenario against the running stack, with trace, HAR, console, and screenshots as evidence. */

const HARNESS_DIR = join(import.meta.dirname, "../..");
export const SCENARIOS_DIR = join(HARNESS_DIR, "scenarios");
const CONFIG = join(HARNESS_DIR, "playwright.config.ts");

/** Scenario names: `scenarios/<name>.spec.ts`. */
export function scenarioNames(dir = SCENARIOS_DIR): string[] {
  return readdirSync(dir)
    .filter((file) => file.endsWith(".spec.ts"))
    .map((file) => file.slice(0, -".spec.ts".length))
    .sort();
}

export interface UiArgs {
  scenario: string;
  headed: boolean;
  user: string;
  run: string | undefined;
}

export function parseUiArgs(argv: string[], known: readonly string[] = scenarioNames()): UiArgs {
  const { values, positionals } = parseCommandArgs({
    args: argv,
    allowPositionals: true,
    options: { headed: { type: "boolean" }, as: { type: "string" }, ...RUN_OPTION },
  });
  const [scenario, ...extra] = positionals;
  if (scenario === undefined || extra.length > 0) {
    throw new UsageError(`expected one scenario: ${known.join(" | ")}`);
  }
  if (!known.includes(scenario)) {
    throw new UsageError(`no scenario ${scenario} — scenarios: ${known.join(", ")}`);
  }
  const user = values.as ?? DEFAULT_USER;
  passwordFor(user);
  return { scenario, headed: values.headed ?? false, user, run: values.run };
}

/** `browser/<scenario>`, or `-2`, `-3`, … when the run already ran it (`--run`). */
function evidenceDir(run: Run, scenario: string): string {
  let dir = `browser/${scenario}`;
  for (let n = 2; existsSync(join(run.dir, dir)); n++) dir = `browser/${scenario}-${n}`;
  return dir;
}

function count(n: number, singular: string, plural = `${singular}s`): string {
  return `${n} ${n === 1 ? singular : plural}`;
}

function seconds(ms: number): string {
  return `${(ms / 1000).toFixed(1)} s`;
}

function readJson<T>(path: string): T | undefined {
  try {
    return JSON.parse(readFileSync(path, "utf8")) as T;
  } catch {
    return undefined;
  }
}

interface ConsoleEntry {
  type: string;
  text: string;
  source?: string | null;
}

function readJsonLines<T>(path: string): T[] {
  if (!existsSync(path)) return [];
  return readFileSync(path, "utf8")
    .split("\n")
    .flatMap((line) => {
      try {
        return line.trim() === "" ? [] : [JSON.parse(line) as T];
      } catch {
        return [];
      }
    });
}

/** What one test left behind, read back from the (redacted) run folder. */
interface TestEvidence {
  /** Relative to the run folder. */
  dir: string | undefined;
  /** Relative to the repo root (for commands to paste). */
  fromRepo: string | undefined;
  files: string[];
  exchanges: Exchange[];
  console: ConsoleEntry[];
}

function testEvidence(run: Run, test: ScenarioTest): TestEvidence {
  if (test.outputDir === undefined || !existsSync(test.outputDir)) {
    return { dir: undefined, fromRepo: undefined, files: [], exchanges: [], console: [] };
  }
  const har = readJson<Har>(join(test.outputDir, "network.har"));
  return {
    dir: relative(run.dir, test.outputDir),
    fromRepo: relative(REPO_ROOT, test.outputDir),
    files: readdirSync(test.outputDir).sort(),
    exchanges: har ? exchanges(har) : [],
    console: readJsonLines<ConsoleEntry>(join(test.outputDir, "console.jsonl")),
  };
}

const MARK: Record<string, string> = { passed: "✓", skipped: "–" };

function testSection(test: ScenarioTest, evidence: TestEvidence, apiOrigin: string): string[] {
  const failed = test.status !== "passed" && test.status !== "skipped";
  const errors = evidence.console.filter((entry) => entry.type === "error" || entry.type === "pageerror");
  const section = [`### ${MARK[test.status] ?? "✗"} ${test.title} — ${test.status} (${seconds(test.durationMs)})`, ""];
  if (failed) {
    if (test.failedStep) section.push(`- **Failed at step:** ${test.failedStep}`);
    section.push(`- **Location:** \`${test.errorLocation ?? test.location}\``);
  }
  if (evidence.dir !== undefined && evidence.fromRepo !== undefined) {
    const screenshots = evidence.files.filter((file) => file.endsWith(".png"));
    section.push(
      `- **Evidence:** \`${evidence.dir}/\``,
      `  - \`trace.zip\` — \`devenv shell -- npx playwright show-trace ${join(evidence.fromRepo, "trace.zip")}\``,
      `  - \`network.har\` — ${count(evidence.exchanges.length, "request")}, ${evidence.exchanges.filter(isFailure).length} failed`,
      `  - \`console.jsonl\` — ${count(evidence.console.length, "entry", "entries")}, ${count(errors.length, "error")}`,
      ...(screenshots.length ? [`  - screenshots: ${screenshots.map((file) => `\`${file}\``).join(", ")}`] : []),
      ...evidence.files.filter((file) => file.endsWith(".webm")).map((file) => `  - video: \`${file}\``),
      ...(evidence.files.includes("error-context.md") ? ["  - `error-context.md` — the page's accessibility tree at the failure"] : []),
    );
  }
  if (failed && test.error) {
    section.push("", "Error:", "", fenced(test.error.slice(0, 3000)));
  }
  if (evidence.dir !== undefined) {
    section.push("", "Requests to the API:", "", ...apiCallTable(evidence.exchanges, apiOrigin));
    const failures = evidence.exchanges.filter(isFailure);
    if (failures.length > 0) {
      section.push("", `Failed requests (${failures.length}):`);
      for (const failure of failures) section.push("", ...failureDetails(failure));
    }
    if (errors.length > 0) {
      section.push(
        "",
        "Console errors:",
        "",
        fenced(errors.slice(0, 10).map((entry) => `[${entry.type}] ${entry.text}${entry.source ? ` (${entry.source})` : ""}`).join("\n")),
      );
    }
  }
  return section;
}

export const uiCommand: Command = {
  name: "ui",
  summary: "Run a browser scenario (Playwright) and record its evidence",
  get usage() {
    return `harness ui <scenario> [--headed] [--as <user>]

  <scenario>    ${scenarioNames().join(" | ")} (tools/harness/scenarios/<scenario>.spec.ts)
  --headed      show the browser window
  --as <user>   sign in as ${Object.keys(USERS).join(" | ")} (default ${DEFAULT_USER})
${RUN_USAGE}

Runs the scenario in Chromium (from devenv) against ${endpoints().web}. Writes
browser/<scenario>/: the runner's output and JSON report, and per test a trace,
HAR, console log, screenshots, and (on failure) a video — all redacted. summary.md
gets each test's outcome, failing step, API calls, and every failed request in
full. Exit code: 0 when every test passed, 1 otherwise.`;
  },
  async run(argv) {
    const args = parseUiArgs(argv);
    checkBrowsers();
    const cli = playwrightCli();
    const startedAt = new Date();
    const run = startRun(args.scenario, startedAt, args.run);
    const dir = evidenceDir(run, args.scenario);
    const target = join(run.dir, dir);
    const staging = mkdtempSync(join(tmpdir(), "harness-ui-"));
    try {
      const result = await exec(
        process.execPath,
        [cli, "test", "--config", CONFIG, ...(args.headed ? ["--headed"] : [])],
        {
          cwd: HARNESS_DIR,
          stream: true,
          env: { ...process.env, HARNESS_OUTPUT_DIR: staging, HARNESS_SCENARIO: args.scenario, HARNESS_USER: args.user },
        },
      );
      // Playwright's output names staging paths — absolute, or relative to its cwd in the
      // runner output; point them at the run folder (repo-relative, for pasting) instead.
      const rewrite = (text: string): string =>
        text.replaceAll(relative(HARNESS_DIR, staging), relative(REPO_ROOT, target)).replaceAll(staging, target);
      copyRedacted(staging, target, rewrite);
      const output = rewrite(redactText(stripVTControlCharacters(result.stdout + result.stderr)));
      writeRunFile(run, `${dir}/output.log`, output);

      const report = readJson<JSONReport>(join(target, "report.json"));
      const tests = report ? scenarioTests(report, SCENARIOS_DIR) : [];
      const passed = result.code === 0 && tests.length > 0 && tests.every((test) => test.status !== "failed" && test.status !== "timedOut");

      await new Promise((resolve) => setTimeout(resolve, 300));
      const since = new Date(runStartedAt(run, startedAt).getTime() - 1000).toISOString();
      const logs = await captureLogs(run, { since }).catch((error: Error) => error);

      const failedCount = tests.filter((test) => test.status !== "passed" && test.status !== "skipped").length;
      const section = [
        `## \`${displayCommand(["ui", ...argv])}\` — ${passed ? "passed" : "failed"}`,
        "",
        `- **Scenario:** \`scenarios/${args.scenario}.spec.ts\` as ${args.user}, ${args.headed ? "headed" : "headless"} · ` +
          `${tests.length - failedCount} passed, ${failedCount} failed · ${seconds(Date.now() - startedAt.getTime())}`,
        `- **Runner:** \`${dir}/output.log\`, \`${dir}/report.json\` (Playwright exited ${result.code})`,
        logs instanceof Error ? `- **Logs:** not captured — ${logs.message}` : `- **Logs (run window):** ${describeLogs(logs)}`,
      ];
      const errors = report ? globalErrors(report) : [];
      if (!report || errors.length > 0 || tests.length === 0) {
        section.push(
          "",
          report ? "**Playwright reported errors outside any test:**" : "**Playwright produced no report.** Last output:",
          "",
          fenced((errors.length > 0 ? errors.join("\n\n") : output.trimEnd().split("\n").slice(-30).join("\n")) || "(no output)"),
        );
      }
      const evidence = tests.map((test) => testEvidence(run, test));
      tests.forEach((test, index) => section.push("", ...testSection(test, evidence[index]!, endpoints().api)));
      const apiLog = logs instanceof Error ? undefined : logs.find((log) => log.service === "api");
      const apiLines = linesSince(apiLog?.text ?? "", startedAt);
      if (!passed && apiLines.length > 0) {
        section.push("", "API log lines since the scenario started:", "", fenced(apiLines.slice(-20).join("\n")));
      }
      appendSummary(run, section.join("\n"));
      const exitCode = passed ? 0 : 1;
      await finishRun(run, ["ui", ...argv], startedAt, exitCode);

      tests.forEach((test, index) => {
        process.stdout.write(`${MARK[test.status] ?? "✗"} ${test.title} — ${test.status}${test.failedStep ? ` at: ${test.failedStep}` : ""}\n`);
        for (const failure of evidence[index]!.exchanges.filter(isFailure)) {
          process.stdout.write(`  ${failure.method} ${failure.url} → ${failure.status || "no response"} ${failure.statusText}\n`);
        }
      });
      process.stdout.write(`summary: ${relative(process.cwd(), join(run.dir, "summary.md"))}\n`);
      return exitCode;
    } finally {
      rmSync(staging, { recursive: true, force: true });
    }
  },
};
