import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { PanelBlock } from "@mimos/api-client";

import { hasLanding, problemDetail, shownBlocks } from "../src/lib/panels.ts";

const text = (value: string): PanelBlock => ({ type: "text", text: value });
const wheel = (landing?: number): PanelBlock => ({
  type: "wheel",
  segments: [{ label: "Peru", icon: "🇵🇪" }, { label: "Japan", icon: "🇯🇵" }],
  ...(landing === undefined ? {} : { landing }),
});
const highlight: PanelBlock = { type: "highlight", icon: "🇵🇪", title: "Peru", text: "South America" };
const actions: PanelBlock = { type: "actions", actions: [{ id: "choose", label: "Choose", primary: true }] };

describe("shownBlocks", () => {
  it("shows every block when nothing spins", () => {
    const blocks = [text("Spin for a country."), wheel(0), highlight, actions];
    assert.deepEqual(
      shownBlocks(blocks, false).map(({ block }) => block),
      blocks,
    );
  });

  it("hides what comes after a spinning wheel", () => {
    const blocks = [text("Spin for a country."), wheel(0), highlight, actions];
    assert.deepEqual(
      shownBlocks(blocks, true).map(({ block }) => block),
      [blocks[0], blocks[1]],
    );
  });

  it("hides nothing for a wheel with nowhere to land", () => {
    const blocks = [wheel(), actions];
    assert.equal(shownBlocks(blocks, true).length, 2);
  });

  it("keys blocks by type and order, so a wheel keeps its key when blocks move around it", () => {
    const before = shownBlocks([text("a"), wheel(), actions], false).map(({ key }) => key);
    const after = shownBlocks([wheel(1), highlight, text("b"), text("c"), actions], false).map(({ key }) => key);
    assert.deepEqual(before, ["text:0", "wheel:0", "actions:0"]);
    assert.deepEqual(after, ["wheel:0", "highlight:0", "text:0", "text:1", "actions:0"]);
  });
});

describe("hasLanding", () => {
  it("is true only for a wheel with a landing", () => {
    assert.equal(hasLanding([text("a"), wheel(1)]), true);
    assert.equal(hasLanding([wheel(0)]), true);
    assert.equal(hasLanding([wheel()]), false);
    assert.equal(hasLanding([highlight, actions]), false);
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
