import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { nextRating, ratedLabel, rateLabel } from "../src/lib/rating.ts";

describe("nextRating", () => {
  it("sets a star on an unrated recipe", () => {
    assert.equal(nextRating(undefined, 3), 3);
  });

  it("overwrites with another star", () => {
    assert.equal(nextRating(2, 5), 5);
  });

  it("clears when the current star is clicked again", () => {
    assert.equal(nextRating(4, 4), null);
  });
});

describe("labels", () => {
  it("names each star button and a read-only rating", () => {
    assert.equal(rateLabel(3), "Rate 3 of 5 stars");
    assert.equal(ratedLabel(4), "Rated 4 of 5 stars");
  });
});
