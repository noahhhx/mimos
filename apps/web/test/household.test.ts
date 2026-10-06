import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { householdName, inviteLink, listNames } from "../src/lib/household.ts";
import { returnPath } from "../src/lib/return-to.ts";

describe("householdName", () => {
  it("names one, two, and more members the way a sentence would", () => {
    assert.equal(householdName(["Sam"]), "Sam's household");
    assert.equal(householdName(["Sam", "Alex"]), "Sam and Alex's household");
    assert.equal(householdName(["Sam", "Alex", "Jo"]), "Sam, Alex and Jo's household");
  });

  it("falls back when there is nobody to name", () => {
    assert.equal(householdName([]), "this household");
    assert.equal(listNames([]), "");
  });
});

describe("inviteLink", () => {
  it("joins the app's URL and the token without doubling slashes", () => {
    assert.equal(inviteLink("https://mimos.example/", "abc-_123"), "https://mimos.example/app/join/abc-_123");
    assert.equal(inviteLink("http://localhost:3000", "t"), "http://localhost:3000/app/join/t");
  });
});

describe("returnPath", () => {
  it("returns to the app page sign-in started from", () => {
    assert.equal(returnPath({ returnTo: "/app/join/abc" }), "/app/join/abc");
    assert.equal(returnPath({ returnTo: "/app" }), "/app");
  });

  it("goes to the Kitchen for anything that is not an app page", () => {
    for (const state of [
      undefined,
      null,
      "/app/join/abc",
      {},
      { returnTo: 42 },
      { returnTo: "https://evil.example/app" },
      { returnTo: "//evil.example/app" },
      { returnTo: "/application" },
      { returnTo: "/recipes" },
      { returnTo: "/app/callback?code=1" },
    ]) {
      assert.equal(returnPath(state), "/app", JSON.stringify(state));
    }
  });
});
