import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { PanelBlock } from "@mimos/api-client";

import { hasLanding, problemDetail, shownBlocks } from "../src/lib/panels.ts";

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/panels.json", import.meta.url), "utf8"),
) as {
  hasLandingCases: { blocks: PanelBlock[]; expected: boolean }[];
  shownBlocksCases: { name: string; blocks: PanelBlock[]; spinning: boolean; expected: string[] }[];
};

describe("shownBlocks", () => {
  for (const each of cases.shownBlocksCases) {
    it(each.name, () => {
      const shown = shownBlocks(each.blocks, each.spinning);
      assert.deepEqual(
        shown.map(({ key }) => key),
        each.expected,
      );
      assert.deepEqual(
        shown.map(({ block }) => block),
        each.blocks.slice(0, each.expected.length),
      );
    });
  }
});

describe("hasLanding", () => {
  it("is true only for a wheel with a landing", () => {
    for (const each of cases.hasLandingCases) {
      assert.equal(hasLanding(each.blocks), each.expected, JSON.stringify(each.blocks));
    }
  });
});

describe("problemDetail", () => {
  it("reads a problem's detail", () => {
    assert.equal(
      problemDetail({ status: 502, title: "Bad Gateway", detail: "Country of the Week did not answer." }, "x"),
      "Country of the Week did not answer.",
    );
  });

  it("falls back when there is no detail", () => {
    assert.equal(problemDetail({ status: 502 }, "That did not work."), "That did not work.");
    assert.equal(problemDetail(undefined, "That did not work."), "That did not work.");
    assert.equal(problemDetail("oops", "That did not work."), "That did not work.");
    assert.equal(problemDetail({ detail: 42 }, "That did not work."), "That did not work.");
  });
});
