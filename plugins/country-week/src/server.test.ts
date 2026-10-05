import assert from "node:assert/strict";
import { after, test } from "node:test";

import type { PluginSuggestions, WeekPanel } from "@mimos/plugin-sdk";

import { createPluginServer, MANIFEST } from "./server.ts";
import { openStore } from "./store.ts";

/** The HTTP surface of the plugin (ADR-0006, ADR-0017): manifest, plan-suggestions, week-panel. */
const store = openStore(":memory:");
// Always 0: no shuffle, and the wheel stops on its first segment, the first eligible country.
const server = createPluginServer(store, () => 0);
await new Promise<void>((resolve) => {
  server.listen(0, "127.0.0.1", () => resolve());
});
const port = (server.address() as { port: number }).port;
const base = `http://127.0.0.1:${port}`;

after(() => {
  server.close();
  store.close();
});

const WEEK = "2026-09-07";
let subjects = 0;
function newSubject(): string {
  subjects += 1;
  return `2f0ac6e8-6f65-4a4b-9d3e-${String(subjects).padStart(12, "0")}`;
}

const CONTEXT = {
  weekStartDate: WEEK,
  plannedSlots: [{ date: WEEK, mealType: "DINNER", servings: 2 }],
  libraryRecipes: [
    { id: "2f0ac6e8-6f65-4a4b-9d3e-111111111111", title: "Spaghetti Aglio e Olio", tags: ["italian"], servings: 4 },
    { id: "2f0ac6e8-6f65-4a4b-9d3e-222222222222", title: "Beef Chili", tags: ["one-pot"], servings: 6 },
  ],
};

function post(path: string, body: unknown, contentType = "application/json"): Promise<Response> {
  return fetch(`${base}${path}`, {
    method: "POST",
    headers: { "Content-Type": contentType },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

async function weekPanel(body: unknown): Promise<WeekPanel> {
  const response = await post("/v1/week-panel", body);
  assert.equal(response.status, 200);
  return (await response.json()) as WeekPanel;
}

async function suggestions(body: unknown): Promise<PluginSuggestions> {
  const response = await post("/v1/plan-suggestions", body);
  assert.equal(response.status, 200);
  return (await response.json()) as PluginSuggestions;
}

test("the manifest declares both capabilities", async () => {
  const response = await fetch(`${base}/manifest`);
  assert.equal(response.status, 200);
  assert.equal((response.headers.get("content-type") ?? "").split(";")[0], "application/json");
  const manifest = (await response.json()) as typeof MANIFEST;
  assert.equal(manifest.schema, "mimos.plugin.manifest/v1");
  assert.equal(manifest.id, "country-week");
  assert.equal(manifest.version, "0.2.0");
  assert.deepEqual(manifest.capabilities, ["plan-suggestions", "week-panel"]);
  assert.ok(manifest.apiVersions.includes("1"));
});

test("rendering a week writes nothing", async () => {
  const subject = newSubject();
  const panel = await weekPanel({ subject, weekStartDate: WEEK });
  assert.equal(panel.summary, undefined);
  assert.ok(panel.blocks.some((block) => block.type === "actions" && block.actions[0]?.id === "spin"));
  assert.deepEqual(store.ledger(subject), { choices: [], removed: new Set() });
});

test("spin, then choose, locks the week and shows its flag from then on", async () => {
  const subject = newSubject();
  const spun = await weekPanel({ subject, weekStartDate: WEEK, action: { id: "spin" } });
  const choose = spun.blocks
    .flatMap((block) => (block.type === "actions" ? block.actions : []))
    .find((action) => action.id === "choose");
  assert.deepEqual(choose, { id: "choose", label: "Choose Algeria", value: "DZ", primary: true });
  assert.deepEqual(store.ledger(subject).choices, [], "a spin alone writes nothing");

  await weekPanel({ subject, weekStartDate: WEEK, action: { id: choose.id, value: choose.value } });
  const rendered = await weekPanel({ subject, weekStartDate: WEEK });
  assert.deepEqual(rendered.summary, { icon: "\u{1F1E9}\u{1F1FF}", label: "Algeria" });

  const nextWeek = await weekPanel({ subject, weekStartDate: "2026-09-14" });
  assert.equal(nextWeek.summary, undefined);
  assert.deepEqual(
    nextWeek.blocks.flatMap((block) => (block.type === "text" ? [block.text] : [])),
    ["196 countries left. Africa sits this one out after your last pick, Algeria."],
  );
});

test("a forged action re-renders the week and writes nothing", async () => {
  const subject = newSubject();
  for (const action of [{ id: "choose", value: "ZZ" }, { id: "remove", value: "nope" }, { id: "explode" }]) {
    const panel = await weekPanel({ subject, weekStartDate: WEEK, action });
    assert.equal(panel.summary, undefined);
  }
  assert.deepEqual(store.ledger(subject), { choices: [], removed: new Set() });
});

test("plan suggestions follow the week's chosen country, and there are none without one", async () => {
  const subject = newSubject();
  assert.deepEqual(await suggestions(CONTEXT), { suggestions: [] }, "no subject");
  assert.deepEqual(await suggestions({ ...CONTEXT, subject }), { suggestions: [] }, "nothing chosen");

  await weekPanel({ subject, weekStartDate: WEEK, action: { id: "choose", value: "IT" } });
  const { suggestions: cards } = await suggestions({ ...CONTEXT, subject });
  assert.equal(cards.length, 1);
  const card = cards[0];
  assert.ok(card);
  assert.equal(card.title, "Italy week");
  assert.equal(card.icon, "\u{1F1EE}\u{1F1F9}");
  assert.deepEqual(
    card.entries.map((entry) => [entry.date, entry.recipeId]),
    [["2026-09-08", "2f0ac6e8-6f65-4a4b-9d3e-111111111111"]],
  );
  assert.deepEqual(
    await suggestions({ ...CONTEXT, subject, weekStartDate: "2026-09-14" }),
    { suggestions: [] },
    "another week has no country",
  );
});

test("malformed requests are rejected, not crashed on", async () => {
  const subject = newSubject();
  const statuses = await Promise.all([
    post("/v1/plan-suggestions", "{not json").then((r) => r.status),
    post("/v1/plan-suggestions", "{}", "text/plain").then((r) => r.status),
    post("/v1/plan-suggestions", { ...CONTEXT, weekStartDate: "not-a-date" }).then((r) => r.status),
    post("/v1/plan-suggestions", { weekStartDate: WEEK }).then((r) => r.status),
    post("/v1/plan-suggestions", { ...CONTEXT, subject: "alice" }).then((r) => r.status),
    post("/v1/week-panel", "{not json").then((r) => r.status),
    post("/v1/week-panel", { subject, weekStartDate: WEEK }, "text/plain").then((r) => r.status),
    post("/v1/week-panel", { weekStartDate: WEEK }).then((r) => r.status),
    post("/v1/week-panel", { subject: "alice", weekStartDate: WEEK }).then((r) => r.status),
    post("/v1/week-panel", { subject, weekStartDate: "2026-02-30" }).then((r) => r.status),
    post("/v1/week-panel", { subject, weekStartDate: WEEK, action: "spin" }).then((r) => r.status),
    post("/v1/week-panel", { subject, weekStartDate: WEEK, action: { id: "Spin!" } }).then((r) => r.status),
    post("/v1/week-panel", { subject, weekStartDate: WEEK, action: { id: "choose", value: 7 } }).then((r) => r.status),
    post("/v1/week-panel", { subject, weekStartDate: WEEK, action: { id: "choose", value: "x".repeat(101) } }).then(
      (r) => r.status,
    ),
    fetch(`${base}/nope`).then((r) => r.status),
  ]);
  assert.deepEqual(statuses, [400, 415, 400, 400, 400, 400, 415, 400, 400, 400, 400, 400, 400, 400, 404]);
});
