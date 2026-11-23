import assert from "node:assert/strict";
import { test } from "node:test";

import { flowNames } from "../src/maestro.ts";
import { assignShards, flowsOfShard, weighFlows } from "../src/shards.ts";

const loads = (shards: string[][], seconds: Record<string, number>) =>
  shards.map((shard) => shard.reduce((sum, name) => sum + (seconds[name] ?? 0), 0));

test("every repository flow runs in exactly one of four shards", () => {
  const shards = [1, 2, 3, 4].map((shard) => flowsOfShard(shard, 4));
  const all = shards.flat();
  assert.deepEqual([...all].sort(), flowNames());
  assert.equal(new Set(all).size, all.length);
  for (const shard of shards) assert.ok(shard.length > 0);
});

test("long flows spread out, so no shard carries two of them", () => {
  const seconds = { a: 100, b: 90, c: 10, d: 10, e: 10, f: 10 };
  const shards = assignShards(weighFlows(Object.keys(seconds), seconds), 2);
  assert.ok(shards.every((shard) => !(shard.includes("a") && shard.includes("b"))));
  assert.deepEqual(loads(shards, seconds), [120, 110]);
});

test("a flow missing from the table weighs the table's median and still runs", () => {
  assert.deepEqual(weighFlows(["a", "new"], { a: 10, b: 30, c: 50 }), [
    { name: "a", seconds: 10 },
    { name: "new", seconds: 30 },
  ]);
  assert.deepEqual(weighFlows(["new"], {}), [{ name: "new", seconds: 1 }]);
});

test("equal times split by name, the same on every shard", () => {
  const flows = ["d", "b", "a", "c"].map((name) => ({ name, seconds: 1 }));
  assert.deepEqual(assignShards(flows, 2), [
    ["a", "c"],
    ["b", "d"],
  ]);
});

test("a shard outside 1 to the count is refused", () => {
  assert.throws(() => flowsOfShard(0, 4), RangeError);
  assert.throws(() => flowsOfShard(5, 4), RangeError);
  assert.throws(() => assignShards([], 0), RangeError);
});
