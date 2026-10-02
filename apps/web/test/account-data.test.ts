import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { exportFileName, importSummary, parseExportFile } from "../src/lib/account-data.ts";

describe("exportFileName", () => {
  it("names the file after the export's date", () => {
    assert.equal(exportFileName("2026-10-02T09:15:00Z"), "mimos-export-2026-10-02.json");
  });
});

describe("parseExportFile", () => {
  it("accepts a JSON object", () => {
    assert.deepEqual(parseExportFile('{"format":"mimos.export","version":1}'), {
      ok: true,
      document: { format: "mimos.export", version: 1 },
    });
  });

  it("rejects text that is not JSON", () => {
    const result = parseExportFile("recipes, plans");
    assert.equal(result.ok, false);
    assert.match(!result.ok ? result.error : "", /not valid JSON/);
  });

  it("rejects JSON that is not an object", () => {
    for (const text of ["[]", "null", '"mimos"', "1"]) {
      assert.equal(parseExportFile(text).ok, false, text);
    }
  });
});

describe("importSummary", () => {
  it("counts each kind, singular and plural", () => {
    assert.equal(
      importSummary({ sourceVersion: 1, recipes: 2, plannedMeals: 1, shoppingLists: 0, mealLogs: 3, warnings: [] }),
      "Imported 2 recipes, 1 planned meal, no shopping lists and 3 logged meals.",
    );
  });
});
