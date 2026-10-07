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
  const report = { sourceVersion: 1, recipes: 2, plannedMeals: 1, shoppingLists: 0, mealLogs: 3, warnings: [] };

  it("counts each kind, singular and plural", () => {
    assert.equal(
      importSummary({ ...report, fuelProfile: false, trainingSessions: 0 }),
      "Imported 2 recipes, 1 planned meal, no shopping lists, 3 logged meals, no fuel settings and no training sessions.",
    );
  });

  it("names fuel settings and training when they came over", () => {
    assert.equal(
      importSummary({
        sourceVersion: 5,
        recipes: 0,
        plannedMeals: 0,
        shoppingLists: 0,
        mealLogs: 0,
        fuelProfile: true,
        trainingSessions: 2,
        warnings: [],
      }),
      "Imported no recipes, no planned meals, no shopping lists, no logged meals, your fuel settings and 2 training sessions.",
    );
  });
});
