import { join } from "node:path";

import { defineConfig } from "@playwright/test";

import { endpoints, REPO_ROOT } from "./src/config.ts";

/**
 * Scenarios for `harness ui <name>` (docs/harness/step-2-browser.md). The
 * harness points HARNESS_OUTPUT_DIR at a staging folder and copies the
 * results into the run folder redacted; HARNESS_SCENARIO selects exactly one
 * file. Browsers come from devenv (PLAYWRIGHT_BROWSERS_PATH).
 */

const output = process.env.HARNESS_OUTPUT_DIR ?? join(REPO_ROOT, ".harness/playwright");
const scenario = process.env.HARNESS_SCENARIO;

export default defineConfig({
  testDir: "scenarios",
  testMatch: scenario ? `${scenario}.spec.ts` : "*.spec.ts",
  outputDir: join(output, "results"),
  reporter: [["list"], ["json", { outputFile: join(output, "report.json") }]],
  // One scenario, one browser, no retries: a flaky failure is evidence too.
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: endpoints().web,
    browserName: "chromium",
    trace: "on",
    screenshot: "on",
    video: "retain-on-failure",
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
});
