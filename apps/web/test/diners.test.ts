import assert from "node:assert/strict";
import { describe, it } from "node:test";

import {
  chipLabels,
  defaultServings,
  eatsIt,
  parsePlanView,
  shareOf,
  toggleDiner,
  visibleEntries,
} from "../src/lib/diners.ts";

const me = { id: "me", displayName: "Sam", you: true };
const partner = { id: "partner", displayName: "Alex", you: false };

describe("shareOf", () => {
  it("splits the servings cooked between the diners", () => {
    assert.equal(shareOf(4, 2), 2);
    assert.equal(shareOf(3, 2), 1.5);
    assert.equal(shareOf(2, 1), 2);
  });

  it("rounds to the nearest 0.1 serving", () => {
    assert.equal(shareOf(2, 3), 0.7);
    assert.equal(shareOf(1.5, 2), 0.8);
    assert.equal(shareOf(10, 3), 3.3);
  });

  it("never logs less than 0.1 serving", () => {
    assert.equal(shareOf(0.5, 6), 0.1);
    assert.equal(shareOf(0.5, 100), 0.1);
  });
});

describe("defaultServings", () => {
  it("keeps 2 for a household of one, whatever the meal", () => {
    for (const mealType of ["BREAKFAST", "LUNCH", "DINNER", "SNACK"] as const) {
      assert.equal(defaultServings(mealType, 1), 2, mealType);
    }
  });

  it("cooks one serving per default diner in a shared household", () => {
    assert.equal(defaultServings("DINNER", 3), 3);
    assert.equal(defaultServings("LUNCH", 3), 1);
    assert.equal(defaultServings("BREAKFAST", 2), 1);
    assert.equal(defaultServings("SNACK", 2), 1);
  });
});

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

describe("toggleDiner", () => {
  it("adds and removes a member", () => {
    assert.deepEqual(toggleDiner(["me"], "partner"), ["me", "partner"]);
    assert.deepEqual(toggleDiner(["me", "partner"], "me"), ["partner"]);
  });

  it("never leaves a meal with nobody", () => {
    assert.equal(toggleDiner(["me"], "me"), null);
  });
});

describe("chipLabels", () => {
  it("uses initials", () => {
    assert.deepEqual(chipLabels(["Sam Lee", "alex", "Jo Ann Smith"]), ["SL", "A", "JS"]);
  });

  it("spells out more of the name where initials would match", () => {
    assert.deepEqual(chipLabels(["Sam", "Sara", "Alex"]), ["Sam", "Sar", "A"]);
    assert.deepEqual(chipLabels(["Sam", "Steve"]), ["Sa", "St"]);
  });
});
