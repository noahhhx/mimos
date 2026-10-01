import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it, mock } from "node:test";

import { authorizedFetch } from "../src/lib/api-fetch.ts";

const API = "http://localhost:8080/api/v1/recipes";

describe("authorizedFetch", () => {
  let sent: Request[];

  beforeEach(() => {
    sent = [];
    mock.method(globalThis, "fetch", async (input: RequestInfo | URL, init?: RequestInit) => {
      sent.push(new Request(input, init));
      return new Response(null, { status: 204 });
    });
  });

  afterEach(() => mock.restoreAll());

  // How the generated client calls it (client.gen.ts: `_fetch(request)`):
  // one prepared Request, no init. Regression test for the recipe 415.
  it("keeps the Content-Type and body of the client's prepared Request", async () => {
    const body = JSON.stringify({ title: "Soup" });
    const request = new Request(API, { method: "POST", headers: { "Content-Type": "application/json" }, body });

    await authorizedFetch(async () => "token-1")(request);

    const [out] = sent;
    assert.ok(out);
    assert.equal(out.method, "POST");
    assert.equal(out.headers.get("Content-Type"), "application/json");
    assert.equal(out.headers.get("Authorization"), "Bearer token-1");
    assert.match(out.headers.get("X-Request-Id") ?? "", /^[0-9a-f-]{32,36}$/);
    assert.equal(await out.text(), body);
  });

  it("keeps a request ID the caller set", async () => {
    await authorizedFetch(async () => "token-1")(new Request(API, { headers: { "X-Request-Id": "caller-id" } }));

    assert.equal(sent[0]?.headers.get("X-Request-Id"), "caller-id");
  });

  it("sends no Authorization header when signed out", async () => {
    await authorizedFetch(async () => undefined)(new Request(API));

    assert.equal(sent[0]?.headers.has("Authorization"), false);
  });

  it("uses init's headers when the caller passes them", async () => {
    await authorizedFetch(async () => "token-1")(API, { headers: { Accept: "application/json" } });

    assert.equal(sent[0]?.headers.get("Accept"), "application/json");
    assert.equal(sent[0]?.headers.get("Authorization"), "Bearer token-1");
  });
});
