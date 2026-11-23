import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { eatsIt, parsePlanView, visibleEntries } from "../src/lib/diners.ts";

const me = { id: "me", displayName: "Sam", you: true };
const partner = { id: "partner", displayName: "Alex", you: false };

describe("visibleEntries", () => {
  const shared = { id: "dinner", diners: [me, partner] };
  const mine = { id: "my-lunch", diners: [me] };
  const theirs = { id: "their-lunch", diners: [partner] };

  it("shows the meals the caller eats, shared ones included, under Mine", () => {
    assert.deepEqual(
      visibleEntries([shared, mine, theirs], "mine").map((entry) => entry.id),
      ["dinner", "my-lunch"],
    );
    assert.equal(eatsIt(theirs), false);
  });

  it("shows every meal under Everyone", () => {
    assert.deepEqual(
      visibleEntries([shared, mine, theirs], "everyone").map((entry) => entry.id),
      ["dinner", "my-lunch", "their-lunch"],
    );
  });
});

describe("parsePlanView", () => {
  it("reads a stored view and falls back to Mine", () => {
    assert.equal(parsePlanView("everyone"), "everyone");
    assert.equal(parsePlanView("mine"), "mine");
    assert.equal(parsePlanView(null), "mine");
    assert.equal(parsePlanView("all"), "mine");
  });
});
