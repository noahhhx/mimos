import assert from "node:assert/strict";
import { test } from "node:test";

import type { JSONReport } from "@playwright/test/reporter";

import { HarnessError } from "../src/args.ts";
import { checkBrowsers, expectedBrowserDirs, globalErrors, scenarioTests } from "../src/playwright.ts";

const SCENARIOS = "/repo/tools/harness/scenarios";
const OUT = "/repo/.harness/runs/r/browser/create-recipe/results/create-recipe-creates-a-recipe";

test("the browser folders a Playwright release expects, as nixpkgs names them", () => {
  assert.deepEqual(
    expectedBrowserDirs({
      browsers: [
        { name: "chromium", revision: "1228" },
        { name: "chromium-headless-shell", revision: "1228" },
        { name: "chromium-tip-of-tree", revision: "1432" },
        { name: "firefox", revision: "1532" },
      ],
    }),
    ["chromium-1228", "chromium_headless_shell-1228"],
  );
});

test("without devenv's browsers, harness ui says how to get them", () => {
  assert.throws(() => checkBrowsers({}), (error: Error) => error instanceof HarnessError && /devenv shell/.test(error.message));
});

/** Trimmed from a real report of the failing create-recipe scenario. */
const REPORT = {
  config: {},
  errors: [],
  stats: { startTime: "2026-10-01T15:40:42.000Z", duration: 1500, expected: 1, unexpected: 1, flaky: 0, skipped: 0 },
  suites: [
    {
      title: "create-recipe.spec.ts",
      file: "create-recipe.spec.ts",
      line: 0,
      column: 0,
      specs: [
        {
          title: "creates a recipe",
          ok: false,
          tags: [],
          id: "1",
          file: "create-recipe.spec.ts",
          line: 7,
          column: 1,
          tests: [
            {
              timeout: 60000,
              annotations: [],
              expectedStatus: "passed",
              projectId: "",
              projectName: "",
              status: "unexpected",
              results: [
                {
                  workerIndex: 0,
                  parallelIndex: 0,
                  status: "failed",
                  duration: 979,
                  errors: [
                    {
                      message: "Error: the form reported an error\n\n\u001b[32m- Array []\u001b[39m",
                      location: { file: `${SCENARIOS}/create-recipe.spec.ts`, line: 35, column: 75 },
                    },
                  ],
                  stdout: [],
                  stderr: [],
                  retry: 0,
                  startTime: "2026-10-01T15:40:42.259Z",
                  annotations: [],
                  steps: [
                    { title: "open the new-recipe form", duration: 87 },
                    {
                      title: "submit",
                      duration: 81,
                      error: { message: "x" },
                      steps: [{ title: "inner", duration: 2, error: { message: "x" } }],
                    },
                  ],
                  attachments: [
                    { name: "error-context", contentType: "text/markdown", body: "inline" },
                    { name: "screenshot", contentType: "image/png", path: `${OUT}/test-failed-1.png` },
                    { name: "trace", contentType: "application/zip", path: `${OUT}/trace.zip` },
                  ],
                },
              ],
            },
          ],
        },
      ],
      suites: [
        {
          title: "nested",
          file: "create-recipe.spec.ts",
          line: 40,
          column: 1,
          specs: [
            {
              title: "a passing one",
              ok: true,
              tags: [],
              id: "2",
              file: "create-recipe.spec.ts",
              line: 41,
              column: 3,
              tests: [
                {
                  timeout: 60000,
                  annotations: [],
                  expectedStatus: "passed",
                  projectId: "",
                  projectName: "",
                  status: "expected",
                  results: [
                    {
                      workerIndex: 0,
                      parallelIndex: 0,
                      status: "passed",
                      duration: 500,
                      errors: [],
                      stdout: [],
                      stderr: [],
                      retry: 0,
                      startTime: "2026-10-01T15:40:43.259Z",
                      annotations: [],
                      attachments: [],
                    },
                  ],
                },
              ],
            },
          ],
        },
      ],
    },
  ],
} as unknown as JSONReport;

test("scenario tests: outcome, innermost failing step, colorless error, evidence folder", () => {
  const [failed, passed] = scenarioTests(REPORT, SCENARIOS);
  assert.deepEqual(failed, {
    title: "creates a recipe",
    location: "scenarios/create-recipe.spec.ts:7",
    status: "failed",
    durationMs: 979,
    failedStep: "submit › inner",
    error: "Error: the form reported an error\n\n- Array []",
    errorLocation: "scenarios/create-recipe.spec.ts:35",
    outputDir: OUT,
  });
  assert.equal(passed?.title, "a passing one");
  assert.equal(passed?.status, "passed");
  assert.equal(passed?.failedStep, undefined);
  assert.equal(passed?.outputDir, undefined);
});

test("errors outside any test are reported without colors", () => {
  assert.deepEqual(globalErrors({ ...REPORT, errors: [{ message: "\u001b[31mError: No tests found\u001b[39m" }] }), [
    "Error: No tests found",
  ]);
});
