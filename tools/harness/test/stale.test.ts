import assert from "node:assert/strict";
import { test } from "node:test";

import { freshness, latestSourceChange, type SourceProbe } from "../src/stale.ts";

/** An in-memory working tree: file → mtime, plus directory mtimes (git lists files only). */
function probe(files: Record<string, string>, deleted: string[] = [], dirs: Record<string, string> = DIRS): SourceProbe {
  const mtimes: Record<string, string> = { ...dirs, ...files };
  return {
    files: async (paths) =>
      // git ls-files -c still lists a tracked file deleted from the working tree.
      [...Object.keys(files), ...deleted].filter((file) =>
        paths.some((path) => file === path || file.startsWith(`${path}/`)),
      ),
    deleted: async (paths) => deleted.filter((file) => paths.some((path) => file.startsWith(`${path}/`))),
    mtime: (path) => (mtimes[path] === undefined ? undefined : new Date(mtimes[path])),
  };
}

const TREE = {
  "pom.xml": "2026-10-01T10:00:00Z",
  "apps/api/src/A.java": "2026-10-01T12:00:00Z",
  "apps/web/src/page.tsx": "2026-10-01T14:00:00Z",
  "core/core-recipes/src/Kept.java": "2026-10-01T08:00:00Z",
};

const DIRS = {
  core: "2026-10-01T09:00:00Z",
  "core/core-recipes": "2026-10-01T13:30:00Z",
};

test("the latest change is the newest file under the service's inputs only", async () => {
  const change = await latestSourceChange(["pom.xml", "apps/api"], probe(TREE));
  assert.deepEqual(change, { path: "apps/api/src/A.java", time: new Date("2026-10-01T12:00:00Z") });
});

test("a deleted file counts at its nearest surviving parent directory's mtime", async () => {
  const change = await latestSourceChange(["core"], probe(TREE, ["core/core-recipes/src/Gone.java"]));
  assert.deepEqual(change, {
    path: "core/core-recipes/src/Gone.java (deleted)",
    time: new Date("2026-10-01T13:30:00Z"),
  });
});

test("no files under the inputs means no change", async () => {
  assert.equal(await latestSourceChange(["integrations"], probe(TREE)), undefined);
});

test("an image is stale only when a source changed after it was built", async () => {
  const change = await latestSourceChange(["apps/api"], probe(TREE));
  assert.deepEqual(freshness(undefined, change), { kind: "no-image" });
  assert.deepEqual(freshness(new Date("2026-10-01T12:30:00Z"), change), { kind: "fresh" });
  assert.deepEqual(freshness(new Date("2026-10-01T11:00:00Z"), change), { kind: "stale", change });
  assert.deepEqual(freshness(new Date("2026-10-01T11:00:00Z"), undefined), { kind: "fresh" });
  // SOURCE_DATE_EPOCH as a Nix shell exports it: the build time is lost, not ancient.
  assert.deepEqual(freshness(new Date("1980-01-01T00:00:00Z"), change), { kind: "undated" });
});
