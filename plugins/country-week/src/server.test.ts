import assert from "node:assert/strict";
import { after, test } from "node:test";

import { createPluginServer, MANIFEST } from "./server.ts";

/** The HTTP surface of the plugin (ADR-0006): manifest + v1 plan-suggestions. */
const server = createPluginServer();
await new Promise<void>((resolve) => {
  server.listen(0, "127.0.0.1", () => resolve());
});
const port = (server.address() as { port: number }).port;
const base = `http://127.0.0.1:${port}`;

after(() => {
  server.close();
});

const CONTEXT = {
  weekStartDate: "2026-09-07",
  plannedSlots: [{ date: "2026-09-07", mealType: "DINNER", servings: 2 }],
  libraryRecipes: [
    { id: "2f0ac6e8-6f65-4a4b-9d3e-111111111111", title: "Spaghetti Aglio e Olio", tags: ["italian"], servings: 4 },
    { id: "2f0ac6e8-6f65-4a4b-9d3e-222222222222", title: "Beef Chili", tags: ["one-pot"], servings: 6 },
  ],
};

test("the manifest declares identity and the plan-suggestions capability", async () => {
  const response = await fetch(`${base}/manifest`);
  assert.equal(response.status, 200);
  assert.equal((response.headers.get("content-type") ?? "").split(";")[0], "application/json");
  const manifest = (await response.json()) as typeof MANIFEST;
  assert.equal(manifest.schema, "mimos.plugin.manifest/v1");
  assert.equal(manifest.id, "country-week");
  assert.deepEqual(manifest.capabilities, ["plan-suggestions"]);
  assert.ok(manifest.apiVersions.includes("1"));
});

test("a suggestion context yields a validated card", async () => {
  const response = await fetch(`${base}/v1/plan-suggestions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(CONTEXT),
  });
  assert.equal(response.status, 200);
  const body = (await response.json()) as { suggestions: Array<{ title: string; entries: Array<{ date: string }> }> };
  assert.equal(body.suggestions.length, 1);
  const card = body.suggestions[0];
  assert.ok(card);
  assert.ok(card.title.endsWith(" week"));
  // Monday's dinner is taken; entries start Tuesday.
  for (const entry of card.entries) {
    assert.notEqual(entry.date, "2026-09-07");
  }
});

test("malformed requests are rejected, not crashed on", async () => {
  const badJson = await fetch(`${base}/v1/plan-suggestions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: "{not json",
  });
  assert.equal(badJson.status, 400);

  const wrongType = await fetch(`${base}/v1/plan-suggestions`, {
    method: "POST",
    headers: { "Content-Type": "text/plain" },
    body: "{}",
  });
  assert.equal(wrongType.status, 415);

  const badDate = await fetch(`${base}/v1/plan-suggestions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...CONTEXT, weekStartDate: "not-a-date" }),
  });
  assert.equal(badDate.status, 400);

  const missing = await fetch(`${base}/v1/plan-suggestions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ weekStartDate: "2026-09-07" }),
  });
  assert.equal(missing.status, 400);

  const unknown = await fetch(`${base}/nope`);
  assert.equal(unknown.status, 404);
});
