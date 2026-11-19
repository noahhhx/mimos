import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { ImportReport } from "@mimos/api-client";

import { exportFileName, importSummary, parseExportFile } from "../src/lib/account-data.ts";

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/account-data.json", import.meta.url), "utf8"),
) as {
  exportFileNameCases: { exportedAt: string; expected: string }[];
  parseExportFileCases: { text: string; document?: Record<string, unknown>; error?: string }[];
  importSummaryCases: { report: ImportReport; expected: string }[];
};

describe("the shared account data cases", () => {
  it("name the download after the export's date", () => {
    for (const each of cases.exportFileNameCases) {
      assert.equal(exportFileName(each.exportedAt), each.expected, each.exportedAt);
    }
  });

  it("accept a JSON object and refuse anything else, saying why", () => {
    for (const each of cases.parseExportFileCases) {
      const expected =
        each.error === undefined ? { ok: true, document: each.document } : { ok: false, error: each.error };
      assert.deepEqual(parseExportFile(each.text), expected, each.text);
    }
  });

  it("count each kind imported, singular and plural", () => {
    for (const each of cases.importSummaryCases) {
      assert.equal(importSummary(each.report), each.expected, JSON.stringify(each.report));
    }
  });
});
