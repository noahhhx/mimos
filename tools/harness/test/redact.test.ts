import assert from "node:assert/strict";
import { test } from "node:test";

import { REDACTED, redactBody, redactConfig, redactHar, redactHeaders, redactJson, redactText } from "../src/redact.ts";

const JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl";

test("secret headers are redacted case-insensitively, keeping the auth scheme", () => {
  assert.deepEqual(
    redactHeaders({ authorization: `Bearer ${JWT}`, Cookie: "s=1", "Content-Type": "application/json" }),
    { authorization: `Bearer ${REDACTED}`, Cookie: REDACTED, "Content-Type": "application/json" },
  );
});

test("secret JSON fields are redacted at any depth", () => {
  assert.deepEqual(
    redactJson({
      access_token: "a",
      nested: [{ Refresh_Token: "r", id_token: "i", password: "p", title: "Soup" }],
      count: 3,
      empty: null,
    }),
    {
      access_token: REDACTED,
      nested: [{ Refresh_Token: REDACTED, id_token: REDACTED, password: REDACTED, title: "Soup" }],
      count: 3,
      empty: null,
    },
  );
});

test("free text loses JWTs and bearer credentials wherever they appear", () => {
  assert.equal(redactText(`token=${JWT} ok`), `token=${REDACTED} ok`);
  assert.equal(redactText("Authorization: Bearer abc.def-ghi"), `Authorization: Bearer ${REDACTED}`);
  assert.equal(redactText("nothing to see"), "nothing to see");
});

test("bodies: form fields, JSON fields, and untouched JSON keeps its formatting", () => {
  assert.equal(
    redactBody("grant_type=password&username=test&password=mimos-test", "application/x-www-form-urlencoded"),
    `grant_type=password&username=test&password=${encodeURIComponent(REDACTED)}`,
  );
  assert.equal(redactBody('{"access_token":"x","n":1}', "application/json"), `{"access_token":"${REDACTED}","n":1}`);
  const pretty = '{\n  "title": "Soup"\n}';
  assert.equal(redactBody(pretty, "application/json"), pretty);
  assert.equal(redactBody(`{ broken ${JWT}`, "application/json"), `{ broken ${REDACTED}`);
});

test("HAR-shaped data: headers and params by name, every cookie, bodies by media type", () => {
  assert.deepEqual(
    redactHar({
      request: {
        url: `http://localhost/x?t=${JWT}`,
        headers: [
          { name: "Authorization", value: `Bearer ${JWT}` },
          { name: "Cookie", value: "KC=1" },
          { name: "Accept", value: "*/*" },
        ],
        cookies: [{ name: "AUTH_SESSION_ID", value: "abc", path: "/" }],
        postData: {
          mimeType: "application/x-www-form-urlencoded",
          text: "username=test&password=mimos-test",
          params: [
            { name: "username", value: "test" },
            { name: "password", value: "mimos-test" },
          ],
        },
      },
      response: {
        content: { mimeType: "application/json", text: '{"access_token":"opaque","expires_in":300}' },
      },
      binary: { mimeType: "image/png", encoding: "base64", text: "iVBORw0KGgo=" },
    }),
    {
      request: {
        url: `http://localhost/x?t=${REDACTED}`,
        headers: [
          { name: "Authorization", value: `Bearer ${REDACTED}` },
          { name: "Cookie", value: REDACTED },
          { name: "Accept", value: "*/*" },
        ],
        cookies: [{ name: "AUTH_SESSION_ID", value: REDACTED, path: "/" }],
        postData: {
          mimeType: "application/x-www-form-urlencoded",
          text: "username=test&password=%5BREDACTED%5D",
          params: [
            { name: "username", value: "test" },
            { name: "password", value: REDACTED },
          ],
        },
      },
      response: {
        content: { mimeType: "application/json", text: `{"access_token":"${REDACTED}","expires_in":300}` },
      },
      binary: { mimeType: "image/png", encoding: "base64", text: "iVBORw0KGgo=" },
    },
  );
});

test("actuator config: secret-named keys lose string values, keeping origins, numbers, and nested groups", () => {
  const env = {
    activeProfiles: ["local"],
    propertySources: [
      {
        name: "systemEnvironment",
        properties: {
          SPRING_DATASOURCE_PASSWORD: { value: "mimos", origin: 'System Environment Property "SPRING_DATASOURCE_PASSWORD"' },
          SPRING_DATASOURCE_URL: { value: "jdbc:postgresql://postgres:5432/mimos", origin: "env" },
          "spring.security.oauth2.client.registration.x.client-secret": { value: "s3cret", origin: "yml" },
          MIMOS_API_KEY: { value: "k", origin: "env" },
          NOTE: { value: `Bearer ${JWT}`, origin: "env" },
        },
      },
    ],
  };
  assert.deepEqual(redactConfig(env), {
    activeProfiles: ["local"],
    propertySources: [
      {
        name: "systemEnvironment",
        properties: {
          SPRING_DATASOURCE_PASSWORD: { value: REDACTED, origin: 'System Environment Property "SPRING_DATASOURCE_PASSWORD"' },
          SPRING_DATASOURCE_URL: { value: "jdbc:postgresql://postgres:5432/mimos", origin: "env" },
          "spring.security.oauth2.client.registration.x.client-secret": { value: REDACTED, origin: "yml" },
          MIMOS_API_KEY: { value: REDACTED, origin: "env" },
          NOTE: { value: `Bearer ${REDACTED}`, origin: "env" },
        },
      },
    ],
  });
  const configprops = {
    properties: { password: "mimos", username: "mimos", maxTokenCount: 100, opaquetoken: { clientId: "api", clientSecret: "s" } },
    inputs: { password: { value: "mimos", origin: "env" }, tokens: ["a", "b"] },
  };
  assert.deepEqual(redactConfig(configprops), {
    properties: { password: REDACTED, username: "mimos", maxTokenCount: 100, opaquetoken: { clientId: "api", clientSecret: REDACTED } },
    inputs: { password: { value: REDACTED, origin: "env" }, tokens: [REDACTED, REDACTED] },
  });
});
