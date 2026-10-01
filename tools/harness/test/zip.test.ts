import assert from "node:assert/strict";
import { test } from "node:test";

import { HarnessError } from "../src/args.ts";
import { readZip, writeZip } from "../src/zip.ts";

/** Written by Python's zipfile: one stored and one deflated entry — a foreign writer, not our own. */
const PYTHON_ZIP = Buffer.from(
  "UEsDBBQAAAAAAAAAIQDPYiAZBQAAAAUAAAAKAAAAc3RvcmVkLnR4dHBsYWluUEsDBBQAAAAIAFOFQV04nDemNQAAAJYAAAAQAAAAZGVmbGF0ZWQubmV0d29ya6tWykvMTVWyUnIsLcnIL8qsSizJzM9T0lEqS8wpBYk7pSYWpRYpJCYl66WkpinVclXTQQcAUEsBAhQDFAAAAAAAAAAhAM9iIBkFAAAABQAAAAoAAAAAAAAAAAAAAIABAAAAAHN0b3JlZC50eHRQSwECFAMUAAAACABThUFdOJw3pjUAAACWAAAAEAAAAAAAAAAAAAAAgAEtAAAAZGVmbGF0ZWQubmV0d29ya1BLBQYAAAAAAgACAHYAAACQAAAAAAA=",
  "base64",
);

test("reads stored and deflated entries written by another tool", () => {
  const entries = readZip(PYTHON_ZIP);
  assert.deepEqual(
    entries.map((entry) => entry.name),
    ["stored.txt", "deflated.network"],
  );
  assert.equal(entries[0]?.data.toString(), "plain");
  assert.equal(entries[1]?.data.toString(), '{"name":"Authorization","value":"Bearer abc.def"}\n'.repeat(3));
});

test("what it writes reads back identically, binary and unicode names included", () => {
  const entries = [
    { name: "trace.trace", data: Buffer.from('{"type":"action"}\n') },
    { name: "resources/ü.jpeg", data: Buffer.from([0xff, 0xd8, 0x00, 0x01, 0x02]) },
    { name: "empty.txt", data: Buffer.alloc(0) },
  ];
  assert.deepEqual(readZip(writeZip(entries)), entries);
});

test("rejects what is not a zip", () => {
  assert.throws(() => readZip(Buffer.from("definitely not a zip archive")), HarnessError);
});
