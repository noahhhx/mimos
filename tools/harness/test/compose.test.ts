import assert from "node:assert/strict";
import { test } from "node:test";

import { composeEnv, describeLogs, isHealthy, linesSince, parseImageLabels, parseJsonOutput } from "../src/compose.ts";

test("compose JSON output: a single array or one object per line", () => {
  assert.deepEqual(parseJsonOutput('[{"Service":"api"},{"Service":"web"}]\n'), [{ Service: "api" }, { Service: "web" }]);
  assert.deepEqual(parseJsonOutput('{"Service":"api"}\n{"Service":"web"}\n'), [{ Service: "api" }, { Service: "web" }]);
  assert.deepEqual(parseJsonOutput("\n"), []);
});

test("healthy means running and healthy, or running without a healthcheck", () => {
  const base = { service: "api", container: "mimos-api-1", exitCode: 0 };
  assert.ok(isHealthy({ ...base, state: "running", health: "healthy" }));
  assert.ok(isHealthy({ ...base, state: "running", health: "" }));
  assert.ok(!isHealthy({ ...base, state: "running", health: "starting" }));
  assert.ok(!isHealthy({ ...base, state: "exited", health: "" }));
});

test("log lines are selected by their compose timestamp", () => {
  const text = [
    "2026-10-01T15:25:09.900000000Z before",
    "2026-10-01T15:25:10.314614000Z WARN Resolved [HttpMediaTypeNotSupportedException]",
    "  continuation line without a timestamp",
    "",
  ].join("\n");
  assert.deepEqual(linesSince(text, new Date("2026-10-01T15:25:10.000Z")), [
    "2026-10-01T15:25:10.314614000Z WARN Resolved [HttpMediaTypeNotSupportedException]",
  ]);
});

test("log listing names files with output and folds the empty ones", () => {
  assert.equal(
    describeLogs([
      { service: "api", file: "logs/api.log", lines: 3, text: "" },
      { service: "web", file: "logs/web.log", lines: 0, text: "" },
      { service: "postgres", file: "logs/postgres.log", lines: 0, text: "" },
    ]),
    "`logs/api.log` (3 lines); no output from web, postgres",
  );
});

test("compose runs without SOURCE_DATE_EPOCH, so images carry their real build time", () => {
  assert.deepEqual(composeEnv({ PATH: "/bin", SOURCE_DATE_EPOCH: "315532800" }), { PATH: "/bin" });
});

test("a container's image is the identity compose labelled it with, not the image it reports", () => {
  // From a real stack on the containerd image store: the container's Image is
  // an index digest the tag no longer points at, but its label names the
  // platform manifest the current build still has — compose will not
  // recreate it, so harness status must not call it outdated.
  const inspected = [
    {
      Image: "sha256:c7c11b1e05785605575c5679edb5dc1fd409db19615dad11f45c1b5cde2c09e5",
      Config: {
        Labels: {
          "com.docker.compose.service": "country-week",
          "com.docker.compose.image": "sha256:083558deb9eef35e1a0fa2e9da41ac9a28c42a1eddccc13c7407d7b7bf0ca5c9",
        },
      },
    },
    { Config: { Labels: { "com.docker.compose.service": "postgres" } } },
    { Config: { Labels: null } },
  ];
  assert.deepEqual(
    parseImageLabels(inspected),
    new Map([["country-week", "sha256:083558deb9eef35e1a0fa2e9da41ac9a28c42a1eddccc13c7407d7b7bf0ca5c9"]]),
  );
});
