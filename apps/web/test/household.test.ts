import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import { appJoinLink, householdName, inviteLink, listNames } from "../src/lib/household.ts";
import { returnPath } from "../src/lib/return-to.ts";

type Named = { names: string[]; expected: string };

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/household.json", import.meta.url), "utf8"),
) as {
  householdNameCases: Named[];
  listNamesCases: Named[];
  inviteLinkCases: { webAddress: string; token: string; link: string; appLink: string }[];
};

describe("the shared household cases", () => {
  it("name a household and its members the way a sentence would", () => {
    for (const each of cases.householdNameCases) {
      assert.equal(householdName(each.names), each.expected, JSON.stringify(each));
    }
    for (const each of cases.listNamesCases) {
      assert.equal(listNames(each.names), each.expected, JSON.stringify(each));
    }
  });

  it("make the web's invite link and the app's, without doubling slashes", () => {
    for (const each of cases.inviteLinkCases) {
      assert.equal(inviteLink(each.webAddress, each.token), each.link, JSON.stringify(each));
      assert.equal(appJoinLink(each.webAddress, each.token), each.appLink, JSON.stringify(each));
    }
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
