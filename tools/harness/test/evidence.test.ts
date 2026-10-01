import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, test } from "node:test";

import { copyRedacted, redactEvidence, redactTraceZip } from "../src/evidence.ts";
import { REDACTED } from "../src/redact.ts";
import { readZip, writeZip } from "../src/zip.ts";

const JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl";
const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x00, 0x00, 0x65, 0x79, 0x4a]);

const dir = mkdtempSync(join(tmpdir(), "harness-evidence-"));
after(() => rmSync(dir, { recursive: true, force: true }));

test("a trace's network records, form posts, and resources are redacted; media is untouched", () => {
  const network = `${JSON.stringify({
    type: "resource-snapshot",
    snapshot: {
      request: { headers: [{ name: "Authorization", value: `Bearer ${JWT}` }], cookies: [{ name: "KC", value: "s" }] },
    },
  })}\n`;
  const zip = writeZip([
    { name: "0-trace.network", data: Buffer.from(network) },
    { name: "resources/a.json", data: Buffer.from(`{"access_token":"opaque","scope":"openid"}`) },
    { name: "resources/b.dat", data: Buffer.from("username=test&password=mimos-test") },
    { name: "resources/c.html", data: Buffer.from(`<p>${JWT}</p>`) },
    { name: "resources/d.png", data: PNG },
  ]);
  const entries = Object.fromEntries(readZip(redactTraceZip(zip)).map((entry) => [entry.name, entry.data]));

  const record = JSON.parse(entries["0-trace.network"]!.toString()) as {
    snapshot: { request: { headers: { value: string }[]; cookies: { value: string }[] } };
  };
  assert.equal(record.snapshot.request.headers[0]?.value, `Bearer ${REDACTED}`);
  assert.equal(record.snapshot.request.cookies[0]?.value, REDACTED);
  assert.equal(entries["resources/a.json"]!.toString(), `{"access_token":"${REDACTED}","scope":"openid"}`);
  assert.equal(entries["resources/b.dat"]!.toString(), "username=test&password=%5BREDACTED%5D");
  assert.equal(entries["resources/c.html"]!.toString(), `<p>${REDACTED}</p>`);
  assert.deepEqual(entries["resources/d.png"], PNG);
});

test("HARs are redacted structurally and stay valid JSON", () => {
  const har = JSON.stringify({
    log: { entries: [{ request: { headers: [{ name: "authorization", value: `Bearer ${JWT}` }] } }] },
  });
  const redacted = JSON.parse(redactEvidence("network.har", Buffer.from(har)).toString()) as {
    log: { entries: { request: { headers: { value: string }[] } }[] };
  };
  assert.equal(redacted.log.entries[0]?.request.headers[0]?.value, `Bearer ${REDACTED}`);
});

test("copying a tree redacts text, keeps media, skips dotfiles, and rewrites paths", () => {
  const from = join(dir, "staging");
  const to = join(dir, "run", "browser");
  mkdirSync(join(from, "results", "t1"), { recursive: true });
  writeFileSync(join(from, "report.json"), JSON.stringify({ path: join(from, "results/t1/trace.zip"), note: JWT }));
  writeFileSync(join(from, "results", ".last-run.json"), "{}");
  writeFileSync(join(from, "results", "t1", "shot.png"), PNG);

  const copied = copyRedacted(from, to, (text) => text.replaceAll(from, to));

  assert.deepEqual(copied, ["report.json", join("results", "t1", "shot.png")]);
  assert.deepEqual(JSON.parse(readFileSync(join(to, "report.json"), "utf8")), {
    path: join(to, "results/t1/trace.zip"),
    note: REDACTED,
  });
  assert.deepEqual(readFileSync(join(to, "results", "t1", "shot.png")), PNG);
});
