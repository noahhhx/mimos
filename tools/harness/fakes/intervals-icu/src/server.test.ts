import assert from "node:assert/strict";
import { after, beforeEach, test } from "node:test";

import { ATHLETE_ID, GOOD_KEY } from "./fixtures.ts";
import { createFakeServer, type LoggedRequest } from "./server.ts";

/** The fake's two surfaces: intervals.icu's four reads, and the `/_fake/` control routes scenarios drive. */
const TODAY = "2026-10-12";
const server = createFakeServer(() => TODAY);
await new Promise<void>((resolve) => {
  server.listen(0, "127.0.0.1", () => resolve());
});
const base = `http://127.0.0.1:${(server.address() as { port: number }).port}`;

after(() => {
  server.close();
});

beforeEach(async () => {
  assert.equal((await fetch(`${base}/_fake/reset`, { method: "POST" })).status, 204);
});

function basic(key: string): string {
  return `Basic ${Buffer.from(`API_KEY:${key}`).toString("base64")}`;
}

function read(path: string, key: string | null = GOOD_KEY): Promise<Response> {
  return fetch(`${base}/api/v1/athlete/${path}`, key === null ? {} : { headers: { Authorization: basic(key) } });
}

async function readJson(path: string): Promise<Record<string, unknown>[]> {
  const response = await read(path);
  assert.equal(response.status, 200);
  return (await response.json()) as Record<string, unknown>[];
}

function control(method: string, path: string, body?: unknown): Promise<Response> {
  return fetch(`${base}/_fake/${path}`, {
    method,
    ...(body === undefined ? {} : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
  });
}

const WEEK = `oldest=${TODAY}&newest=2026-10-18`;

test("serves the athlete record to the good key", async () => {
  const response = await read(ATHLETE_ID);
  assert.equal(response.status, 200);
  const athlete = (await response.json()) as { id: string; sex: string; sportSettings: { ftp: number | null }[] };
  assert.equal(athlete.id, ATHLETE_ID);
  assert.equal(athlete.sex, "M");
  assert.deepEqual(
    athlete.sportSettings.map((settings) => settings.ftp),
    [null, 282],
  );
});

test("a wrong or missing key is a 401, another athlete a 403", async () => {
  assert.equal((await read(ATHLETE_ID, "not-the-key")).status, 401);
  assert.equal((await read(ATHLETE_ID, null)).status, 401);
  assert.equal((await read("i2")).status, 403);
});

test("planned workouts sit around today, filtered by date and category", async () => {
  const events = await readJson(`${ATHLETE_ID}/events?${WEEK}&category=WORKOUT`);
  assert.deepEqual(
    events.map((event) => [event.id, event.start_date_local, event.joules]),
    [
      [41870231, "2026-10-12T00:00:00", 1360800],
      [41870232, "2026-10-14T00:00:00", undefined],
    ],
  );
  assert.deepEqual(await readJson(`${ATHLETE_ID}/events?oldest=2026-10-13&newest=2026-10-13`), []);
  assert.deepEqual(await readJson(`${ATHLETE_ID}/events?${WEEK}&category=NOTE`), []);
});

test("activities and wellness are read by their dates, inclusive", async () => {
  const activities = await readJson(`${ATHLETE_ID}/activities?oldest=2026-10-11&newest=2026-10-11`);
  assert.deepEqual(
    activities.map((activity) => [activity.id, activity.icu_joules, activity.calories]),
    [["i98765432", 2203560, 2410]],
  );
  const wellness = await readJson(`${ATHLETE_ID}/wellness?oldest=2026-10-09&newest=2026-10-11`);
  assert.deepEqual(
    wellness.map((record) => [record.id, record.weight, record.bodyFat]),
    [
      ["2026-10-09", 71.4, 14.1],
      ["2026-10-10", 71.0, 13.6],
      ["2026-10-11", 70.8, undefined],
    ],
  );
});

test("a list read without a full date range is a 400", async () => {
  assert.equal((await read(`${ATHLETE_ID}/activities?oldest=${TODAY}`)).status, 400);
  assert.equal((await read(`${ATHLETE_ID}/wellness?oldest=${TODAY}&newest=tomorrow`)).status, 400);
});

test("a scenario changes a workout, removes one, and adds an activity", async () => {
  const changed = await control("PUT", "events/41870232", {
    start_date_local: "2026-10-15T00:00:00",
    type: "Ride",
    moving_time: 3600,
    joules: 900000,
  });
  assert.equal(changed.status, 200);
  assert.equal(((await changed.json()) as { category: string }).category, "WORKOUT");
  assert.equal((await control("DELETE", "events/41870231")).status, 204);
  assert.equal((await control("DELETE", "events/41870231")).status, 404);
  assert.deepEqual(
    (await readJson(`${ATHLETE_ID}/events?${WEEK}`)).map((event) => [event.id, event.start_date_local, event.joules]),
    [[41870232, "2026-10-15T00:00:00", 900000]],
  );

  const added = await control("PUT", "activities/i555", { start_date_local: "2026-10-12T17:05:00", type: "Run", moving_time: 2700, calories: 610 });
  assert.equal(added.status, 200);
  assert.deepEqual(
    (await readJson(`${ATHLETE_ID}/activities?${WEEK}`)).map((activity) => [activity.id, activity.calories]),
    [["i555", 610]],
  );
});

test("a write without a usable start date or id is a 400", async () => {
  assert.equal((await control("PUT", "events/41870231", { type: "Ride" })).status, 400);
  assert.equal((await control("PUT", "events/abc", { start_date_local: "2026-10-12T00:00:00" })).status, 400);
  assert.equal((await control("PUT", "activities/i1", ["not", "an", "object"])).status, 400);
  assert.equal((await control("PUT", "wellness/today", { bodyFat: 12 })).status, 400);
});

test("setting body fat keeps the day's weight", async () => {
  assert.equal((await control("PUT", "wellness/2026-10-11", { bodyFat: 12.5 })).status, 200);
  assert.equal((await control("PUT", "wellness/2026-10-12", { bodyFat: 12.2 })).status, 200);
  assert.deepEqual(
    (await readJson(`${ATHLETE_ID}/wellness?oldest=2026-10-11&newest=2026-10-12`)).map((record) => [record.id, record.weight, record.bodyFat]),
    [
      ["2026-10-11", 70.8, 12.5],
      ["2026-10-12", undefined, 12.2],
    ],
  );
});

test("a forced 429 carries Retry-After and lasts its count; a forced 401 lasts until cleared", async () => {
  assert.equal((await control("PUT", "fault", { status: 429, retryAfter: 30, times: 2 })).status, 200);
  for (let i = 0; i < 2; i++) {
    const limited = await read(ATHLETE_ID);
    assert.equal(limited.status, 429);
    assert.equal(limited.headers.get("retry-after"), "30");
  }
  assert.equal((await read(ATHLETE_ID)).status, 200);

  assert.equal((await control("PUT", "fault", { status: 401 })).status, 200);
  assert.equal((await read(ATHLETE_ID)).status, 401);
  assert.equal((await read(ATHLETE_ID)).status, 401);
  assert.equal((await control("DELETE", "fault")).status, 204);
  assert.equal((await read(ATHLETE_ID)).status, 200);
});

test("a fault other than a 401 or a 429 is a 400", async () => {
  assert.equal((await control("PUT", "fault", { status: 500 })).status, 400);
  assert.equal((await control("PUT", "fault", { status: 429, times: 0 })).status, 400);
});

test("the request log names each read and how its key fared, never the key", async () => {
  await read(`${ATHLETE_ID}/events?${WEEK}&category=WORKOUT`);
  await read(ATHLETE_ID, "not-the-key");
  await control("GET", "health");
  const log = (await (await control("GET", "requests")).json()) as LoggedRequest[];
  assert.deepEqual(
    log.map(({ path, query, key, status }) => ({ path, query, key, status })),
    [
      { path: `/api/v1/athlete/${ATHLETE_ID}/events`, query: { oldest: TODAY, newest: "2026-10-18", category: "WORKOUT" }, key: "good", status: 200 },
      { path: `/api/v1/athlete/${ATHLETE_ID}`, query: {}, key: "wrong", status: 401 },
    ],
  );
  assert.ok(!JSON.stringify(log).includes(GOOD_KEY));
});

test("reset puts the fixtures back and empties the log", async () => {
  await control("DELETE", "events/41870231");
  await control("PUT", "fault", { status: 401 });
  await read(ATHLETE_ID);
  assert.equal((await control("POST", "reset")).status, 204);
  assert.deepEqual(await (await control("GET", "requests")).json(), []);
  assert.equal((await readJson(`${ATHLETE_ID}/events?${WEEK}`)).length, 2);
});

test("an unknown control route is a 404", async () => {
  assert.equal((await control("GET", "nothing")).status, 404);
  assert.equal((await control("DELETE", "events/1/extra")).status, 404);
});
