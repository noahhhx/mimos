import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { test } from "node:test";

import { HarnessError } from "../src/args.ts";
import { APP_REDIRECT_URI, codeFromRedirect, loginFormAction, pkcePair } from "../src/app-session.ts";

test("the PKCE challenge is the verifier's SHA-256, base64url (S256)", () => {
  const { verifier, challenge } = pkcePair("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk");
  assert.equal(challenge, "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
  assert.equal(createHash("sha256").update(verifier).digest("base64url"), challenge);
  assert.notEqual(pkcePair().verifier, pkcePair().verifier);
});

test("the login form's action is read from kc-form-login with its entities decoded", () => {
  const html = `<form id="kc-other" action="/nope"></form><form id="kc-form-login" onsubmit="x" action="http://localhost:8081/realms/mimos/login-actions/authenticate?session_code=a&amp;execution=b&amp;client_id=mimos-mobile" method="post">`;
  assert.equal(loginFormAction(html), "http://localhost:8081/realms/mimos/login-actions/authenticate?session_code=a&execution=b&client_id=mimos-mobile");
  assert.equal(loginFormAction("<p>Invalid username or password.</p>"), undefined);
});

test("the code is taken from the redirect to the app only when its state matches", () => {
  assert.equal(codeFromRedirect(`${APP_REDIRECT_URI}?state=s1&session_state=x&code=c1`, "s1"), "c1");
  assert.throws(() => codeFromRedirect(`${APP_REDIRECT_URI}?state=other&code=c1`, "s1"), HarnessError);
  assert.throws(() => codeFromRedirect(`${APP_REDIRECT_URI}?state=s1&error=access_denied`, "s1"), /access_denied/);
  assert.throws(() => codeFromRedirect("http://localhost:8081/realms/mimos/login-actions/authenticate", "s1"), /did not redirect/);
  assert.throws(() => codeFromRedirect(null, "s1"), /no Location/);
});
