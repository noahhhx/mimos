import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

import { BUILD_INPUTS, endpoints, REPO_ROOT } from "../src/config.ts";

/** COPY sources of a Dockerfile (build-context paths; `--from` stage copies excluded). */
function copySources(dockerfile: string): string[] {
  return readFileSync(join(REPO_ROOT, dockerfile), "utf8")
    .split("\n")
    .filter((line) => /^COPY\s/.test(line) && !line.includes("--from"))
    .flatMap((line) => line.trim().split(/\s+/).slice(1, -1))
    .filter((arg) => !arg.startsWith("--"))
    .map((source) => source.replace(/^\.\//, "").replace(/\/$/, ""));
}

test("every build input exists and is actually copied by the service's Dockerfile", () => {
  for (const [service, { dockerfile, paths }] of Object.entries(BUILD_INPUTS)) {
    const sources = copySources(dockerfile);
    for (const path of paths) {
      assert.ok(existsSync(join(REPO_ROOT, path)), `${service}: ${path} does not exist`);
      assert.ok(
        sources.some((source) => path === source || path.startsWith(`${source}/`) || source.startsWith(`${path}/`)),
        `${service}: ${path} is not a COPY source of ${dockerfile} (${sources.join(", ")})`,
      );
    }
  }
});

test("endpoints follow compose's variables and defaults", () => {
  assert.deepEqual(endpoints({}), {
    api: "http://localhost:8080",
    keycloak: "http://localhost:8081",
    web: "http://localhost:3000",
    clientId: "mimos-web",
  });
  assert.equal(endpoints({ API_PORT: "9090" }).api, "http://localhost:9090");
  assert.equal(endpoints({ API_PORT: "9090", API_PUBLIC_URL: "http://api.test" }).api, "http://api.test");
});
