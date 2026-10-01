import { existsSync, readdirSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join, relative } from "node:path";
import { stripVTControlCharacters } from "node:util";

import type { JSONReport, JSONReportSuite, JSONReportTestStep } from "@playwright/test/reporter";

import { HarnessError } from "./args.ts";

/**
 * The Playwright side of `harness ui`: where the runner and its browsers
 * are, and what its JSON report says about each scenario test.
 */

const require = createRequire(import.meta.url);

/** `@playwright/test`'s CLI entry point, as installed by `npm ci`. */
export function playwrightCli(): string {
  try {
    return join(dirname(require.resolve("@playwright/test/package.json")), "cli.js");
  } catch {
    throw new HarnessError("@playwright/test is not installed — run `npm ci` at the repo root");
  }
}

function playwrightCoreDir(): string {
  const test = createRequire(require.resolve("@playwright/test/package.json"));
  const playwright = createRequire(test.resolve("playwright/package.json"));
  return dirname(playwright.resolve("playwright-core/package.json"));
}

/** Browser folder names this Playwright release expects (`chromium_headless_shell-1228`). */
export function expectedBrowserDirs(browsersJson: { browsers: { name: string; revision: string }[] }): string[] {
  return browsersJson.browsers
    .filter((browser) => browser.name === "chromium" || browser.name === "chromium-headless-shell")
    .map((browser) => `${browser.name.replaceAll("-", "_")}-${browser.revision}`);
}

/**
 * Fails early, with a fix, when the browsers are not devenv's or do not
 * match the installed Playwright — instead of Playwright's own advice to
 * download browsers, which is wrong here (docs/harness/step-2-browser.md).
 */
export function checkBrowsers(env: NodeJS.ProcessEnv = process.env): void {
  const path = env.PLAYWRIGHT_BROWSERS_PATH;
  if (!path) {
    throw new HarnessError("no PLAYWRIGHT_BROWSERS_PATH — run it in the devenv shell: devenv shell -- harness ui …");
  }
  const core = playwrightCoreDir();
  const { version } = JSON.parse(readFileSync(join(core, "package.json"), "utf8")) as { version: string };
  const present = existsSync(path) ? readdirSync(path) : [];
  const missing = expectedBrowserDirs(JSON.parse(readFileSync(join(core, "browsers.json"), "utf8"))).filter(
    (dir) => !present.includes(dir),
  );
  if (missing.length > 0) {
    throw new HarnessError(
      `Playwright ${version} needs ${missing.join(", ")}, which ${path} does not have (it has ${present.join(", ") || "nothing"}).\n` +
        "The npm @playwright/test pin in tools/harness/package.json must equal nixpkgs' playwright-driver version — " +
        "re-run `npm ci`, or bump the pin to match devenv.lock.",
    );
  }
}

export interface ScenarioTest {
  title: string;
  /** `scenarios/create-recipe.spec.ts:12` */
  location: string;
  status: string;
  durationMs: number;
  /** The failing step's path, outermost first (`submit the form › expect.toBeVisible`). */
  failedStep: string | undefined;
  /** First error, colors stripped. */
  error: string | undefined;
  errorLocation: string | undefined;
  /** The test's evidence directory (where its trace, HAR, and screenshots are), if it produced any. */
  outputDir: string | undefined;
}

/** The innermost failing step under `steps`, as a title path. */
function failingStep(steps: readonly JSONReportTestStep[] | undefined): string[] {
  for (const step of steps ?? []) {
    if (step.error) return [step.title, ...failingStep(step.steps)];
  }
  return [];
}

function* specsOf(suites: readonly JSONReportSuite[]): Generator<{ suite: JSONReportSuite; spec: JSONReportSuite["specs"][number] }> {
  for (const suite of suites) {
    for (const spec of suite.specs) yield { suite, spec };
    yield* specsOf(suite.suites ?? []);
  }
}

/** One entry per test result (the harness runs without retries, so one per test). */
export function scenarioTests(report: JSONReport, testDir: string): ScenarioTest[] {
  const tests: ScenarioTest[] = [];
  for (const { spec } of specsOf(report.suites)) {
    for (const test of spec.tests) {
      for (const result of test.results) {
        const error = result.errors[0];
        const step = failingStep(result.steps);
        const attachment = result.attachments.find((candidate) => candidate.path !== undefined);
        tests.push({
          title: spec.title,
          location: `scenarios/${spec.file}:${spec.line}`,
          status: result.status ?? "unknown",
          durationMs: result.duration,
          failedStep: step.length > 0 ? step.join(" › ") : undefined,
          error: error?.message === undefined ? undefined : stripVTControlCharacters(error.message),
          errorLocation:
            error?.location === undefined
              ? undefined
              : `${relative(dirname(testDir), error.location.file)}:${error.location.line}`,
          outputDir: attachment?.path === undefined ? undefined : dirname(attachment.path),
        });
      }
    }
  }
  return tests;
}

/** Errors outside any test (config errors, no tests found), colors stripped. */
export function globalErrors(report: JSONReport): string[] {
  return report.errors.map((error) => stripVTControlCharacters(error.message ?? String(error.value ?? "unknown error")));
}
