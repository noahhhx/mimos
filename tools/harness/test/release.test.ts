import assert from "node:assert/strict";
import { test } from "node:test";

import { HarnessError } from "../src/args.ts";
import { parseReleaseArgs, RELEASE_PROJECT, releaseComposeArgs, releaseEnv } from "../src/commands/release.ts";
import {
  decide,
  flowNamesIn,
  hasMobileClient,
  imageRef,
  outputLines,
  pomVersion,
  releaseOf,
  type PickInput,
} from "../src/release.ts";

const withMobile = JSON.stringify({ clients: [{ clientId: "mimos-web" }, { clientId: "mimos-mobile" }] });
const withoutMobile = JSON.stringify({ clients: [{ clientId: "mimos-web" }, { clientId: "mimos-agent" }] });

const ready: PickInput = {
  tag: "v0.2.0",
  mainVersion: "0.2.1-SNAPSHOT",
  realmAtTag: withMobile,
  missingImages: [],
  flowsAtTag: ["connect-and-sign-in", "plan-a-week"],
  flowsNow: ["connect-and-sign-in", "plan-a-week", "kitchen-home"],
};

test("a release with the mobile client runs the flows it already had, and none added since", () => {
  const pick = decide(ready);
  assert.deepEqual(pick, {
    kind: "run",
    release: { tag: "v0.2.0", version: "0.2.0" },
    flows: ["connect-and-sign-in", "plan-a-week"],
  });
});

test("a flow the release had but main removed is not run", () => {
  const pick = decide({ ...ready, flowsNow: ["plan-a-week"] });
  assert.equal(pick.kind === "run" && pick.flows.join(), "plan-a-week");
});

test("each reason to skip says so, and a skip writes no outputs", () => {
  const cases: [Partial<PickInput>, RegExp][] = [
    [{ tag: undefined }, /No v\* release yet/],
    [{ realmAtTag: withoutMobile }, /v0\.2\.0 predates the mimos-mobile Keycloak client/],
    [{ realmAtTag: undefined }, /predates the mimos-mobile/],
    [{ missingImages: ["mimos-web"] }, /v0\.2\.0's images are not published \(mimos-web\)/],
    [{ flowsAtTag: ["old-flow"] }, /has none of main's flows/],
  ];
  for (const [change, reason] of cases) {
    const pick = decide({ ...ready, ...change });
    assert.equal(pick.kind, "skip", String(reason));
    assert.match(pick.kind === "skip" ? pick.reason : "", reason);
    assert.equal(outputLines(pick), "");
  }
});

test("main below the release fails, naming the version to raise, and writes no outputs", () => {
  for (const mainVersion of ["0.1.9-SNAPSHOT", "0.1.99-SNAPSHOT"]) {
    const pick = decide({ ...ready, mainVersion });
    assert.equal(pick.kind, "fail", mainVersion);
    assert.match(pick.kind === "fail" ? pick.reason : "", /below v0\.2\.0.*Raise pom\.xml's version above 0\.2\.0/);
    assert.equal(outputLines(pick), "");
  }
});

test("main at or above the release runs, as the app shows no update notice then", () => {
  for (const mainVersion of ["0.2.0-SNAPSHOT", "0.2.1-SNAPSHOT", "0.10.0-SNAPSHOT", "1.0.0-SNAPSHOT"]) {
    assert.equal(decide({ ...ready, mainVersion }).kind, "run", mainVersion);
  }
  assert.equal(decide({ ...ready, tag: "v0.2.0-rc.1", mainVersion: "0.2.0-SNAPSHOT" }).kind, "run");
});

test("the pom version is the project's, not the Spring Boot parent's", () => {
  const pom = `<parent><artifactId>spring-boot-starter-parent</artifactId><version>4.2.0</version></parent>
    <groupId>io.github.noahhhx.mimos</groupId>
    <artifactId>mimos-parent</artifactId>
    <version>0.1.2-SNAPSHOT</version>`;
  assert.equal(pomVersion(pom), "0.1.2-SNAPSHOT");
  assert.throws(() => pomVersion("<project/>"), HarnessError);
});

test("a run's outputs name the tag, the image version, and the flows", () => {
  assert.equal(outputLines(decide(ready)), "tag=v0.2.0\nversion=0.2.0\nflows=connect-and-sign-in plan-a-week\n");
});

test("release tags are versions publish tags images with, without the v", () => {
  assert.deepEqual(releaseOf("v1.2.3-rc.4"), { tag: "v1.2.3-rc.4", version: "1.2.3-rc.4" });
  assert.equal(imageRef("mimos-api", "1.2.3"), "ghcr.io/noahhhx/mimos-api:1.2.3");
  for (const tag of ["1.2.3", "v1.2", "v1.2.3-beta", "vnext"]) assert.throws(() => releaseOf(tag), HarnessError, tag);
});

test("the mobile client is found by its client id only", () => {
  assert.equal(hasMobileClient(withMobile), true);
  assert.equal(hasMobileClient(withoutMobile), false);
  assert.equal(hasMobileClient(JSON.stringify({ clients: [{ name: "mimos-mobile" }] })), false);
  assert.equal(hasMobileClient("{}"), false);
});

test("flow names come from yaml files only, sorted", () => {
  assert.deepEqual(flowNamesIn(["apps/mobile/flows/b.yaml", "assets", "a.yaml", "README.md"]), ["a", "b"]);
});

test("the stack's env points the self-host compose file at this machine's ports and admin", () => {
  const env = releaseEnv({ tag: "v0.2.0", version: "0.2.0" }, "key", {
    API_PORT: "18080",
    KEYCLOAK_ADMIN_PASSWORD: "pw",
  });
  assert.equal(env.MIMOS_VERSION, "0.2.0");
  assert.equal(env.API_PUBLIC_URL, "http://localhost:18080");
  assert.equal(env.KEYCLOAK_PUBLIC_URL, "http://localhost:8081");
  assert.equal(env.WEB_ORIGIN, "http://localhost:3000");
  assert.equal(env.KEYCLOAK_ADMIN_PASSWORD, "pw");
  assert.equal(env.MIMOS_SECRET_KEY, "key");
});

test("compose runs as its own project, the tag's file first, with the fakes over it", () => {
  const args = releaseComposeArgs("/r", ["up"]);
  assert.deepEqual(args.slice(0, 3), ["compose", "-p", RELEASE_PROJECT]);
  const files = args.flatMap((arg, index) => (args[index - 1] === "-f" ? [arg] : []));
  assert.equal(files[0], "/r/compose.yml");
  assert.match(files[1] ?? "", /deploy\/docker\/compose\.fakes\.yml$/);
  assert.match(args[args.indexOf("--project-directory") + 1] ?? "", /deploy\/docker$/);
  assert.equal(args.at(-1), "up");
});

test("release takes up with an optional tag, or down alone", () => {
  assert.deepEqual(parseReleaseArgs(["up"]), { action: "up", tag: undefined, run: undefined });
  assert.deepEqual(parseReleaseArgs(["up", "v1.0.0", "--run", "latest"]), {
    action: "up",
    tag: "v1.0.0",
    run: "latest",
  });
  assert.deepEqual(parseReleaseArgs(["down"]), { action: "down", tag: undefined, run: undefined });
  for (const argv of [[], ["start"], ["down", "v1.0.0"], ["up", "a", "b"], ["down", "--run", "x"]]) {
    assert.throws(() => parseReleaseArgs(argv), /expected|takes no/, argv.join(" "));
  }
});
