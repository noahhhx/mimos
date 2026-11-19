import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { showsPlannedTotals } from "../src/lib/planned-totals.ts";

describe("showsPlannedTotals", () => {
  it("always shows them to a household of one", () => {
    assert.equal(showsPlannedTotals(false, "mine"), true);
    assert.equal(showsPlannedTotals(false, "everyone"), true);
  });

  it("shows them in a shared household's Mine view only", () => {
    assert.equal(showsPlannedTotals(true, "mine"), true);
    assert.equal(showsPlannedTotals(true, "everyone"), false);
  });
});
