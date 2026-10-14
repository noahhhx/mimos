import assert from "node:assert/strict";
import { after, beforeEach, test } from "node:test";

import { COCA_COLA, MARMITE, NUTELLA } from "./fixtures.ts";
import { createFakeServer, type LoggedRequest } from "./server.ts";

/** The fake's two surfaces: Open Food Facts' product read, and the `/_fake/` control routes scenarios drive. */
const server = createFakeServer();
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

interface Answer {
  code: string;
  status: number;
  status_verbose: string;
  product?: Record<string, unknown>;
}

function lookUp(barcode: string, fields?: string): Promise<Response> {
  const query = fields === undefined ? "" : `?fields=${fields}`;
  return fetch(`${base}/api/v2/product/${barcode}${query}`, { headers: { "User-Agent": "Mimos/test (https://github.com/noahhhx/mimos)" } });
}

async function product(barcode: string, fields?: string): Promise<Record<string, unknown>> {
  const response = await lookUp(barcode, fields);
  assert.equal(response.status, 200);
  const answer = (await response.json()) as Answer;
  assert.equal(answer.status, 1);
  return answer.product!;
}

function control(method: string, path: string, body?: unknown): Promise<Response> {
  return fetch(`${base}/_fake/${path}`, {
    method,
    ...(body === undefined ? {} : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
  });
}

test("serves a solid, a drink, and a product without fat", async () => {
  assert.equal((await product(NUTELLA)).product_name, "Nutella");
  const cola = await product(COCA_COLA);
  assert.equal(cola.serving_quantity_unit, "ml");
  assert.equal(cola.nutrition_data_per, "100g");
  const marmite = await product(MARMITE);
  assert.equal((marmite.nutriments as Record<string, unknown>).fat_100g, undefined);
});

test("answers only the fields asked for", async () => {
  assert.deepEqual(Object.keys(await product(NUTELLA, "product_name,brands")).sort(), ["brands", "product_name"]);
});

test("any other barcode is a 404 with status 0, as Open Food Facts answers it", async () => {
  const response = await lookUp("20000000");
  assert.equal(response.status, 404);
  assert.deepEqual(await response.json(), { code: "20000000", status: 0, status_verbose: "product not found" });
});

test("a scenario adds and removes a product", async () => {
  const added = await control("PUT", "products/4006381333931", { product_name: "Stabilo Boss", nutriments: {} });
  assert.equal(added.status, 200);
  assert.equal((await product("4006381333931")).code, "4006381333931");
  assert.equal((await control("DELETE", "products/4006381333931")).status, 204);
  assert.equal((await control("DELETE", "products/4006381333931")).status, 404);
  assert.equal((await lookUp("4006381333931")).status, 404);
});

test("a product under anything but 8 to 14 digits is a 400", async () => {
  assert.equal((await control("PUT", "products/1234", { product_name: "x" })).status, 400);
  assert.equal((await control("PUT", `products/${NUTELLA}`, ["not", "an", "object"])).status, 400);
});

test("a forced outage lasts its count, or until cleared", async () => {
  assert.equal((await control("PUT", "fault", { status: 503, times: 2 })).status, 200);
  assert.equal((await lookUp(NUTELLA)).status, 503);
  assert.equal((await lookUp(NUTELLA)).status, 503);
  assert.equal((await lookUp(NUTELLA)).status, 200);

  assert.equal((await control("PUT", "fault", { status: 429 })).status, 200);
  assert.equal((await lookUp(NUTELLA)).status, 429);
  assert.equal((await lookUp(NUTELLA)).status, 429);
  assert.equal((await control("DELETE", "fault")).status, 204);
  assert.equal((await lookUp(NUTELLA)).status, 200);
});

test("a fault other than a 429, 500, or 503 is a 400", async () => {
  assert.equal((await control("PUT", "fault", { status: 404 })).status, 400);
  assert.equal((await control("PUT", "fault", { status: 500, times: 0 })).status, 400);
});

test("the request log names each lookup, its fields, and who asked", async () => {
  await lookUp(NUTELLA, "product_name,nutriments");
  await lookUp("20000000");
  await control("GET", "health");
  const log = (await (await control("GET", "requests")).json()) as LoggedRequest[];
  assert.deepEqual(
    log.map(({ barcode, fields, userAgent, status }) => ({ barcode, fields, userAgent, status })),
    [
      { barcode: NUTELLA, fields: ["product_name", "nutriments"], userAgent: "Mimos/test (https://github.com/noahhhx/mimos)", status: 200 },
      { barcode: "20000000", fields: null, userAgent: "Mimos/test (https://github.com/noahhhx/mimos)", status: 404 },
    ],
  );
});

test("reset puts the fixtures back and empties the log", async () => {
  await control("DELETE", `products/${NUTELLA}`);
  await control("PUT", "fault", { status: 500 });
  await lookUp(NUTELLA);
  assert.equal((await control("POST", "reset")).status, 204);
  assert.deepEqual(await (await control("GET", "requests")).json(), []);
  assert.equal((await product(NUTELLA)).product_name, "Nutella");
});

test("an unknown route is a 404", async () => {
  assert.equal((await control("GET", "nothing")).status, 404);
  assert.equal((await control("DELETE", "products/1/extra")).status, 404);
  assert.equal((await fetch(`${base}/api/v0/product/${NUTELLA}.json`)).status, 404);
});
