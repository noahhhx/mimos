import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";

import { appCalls, classify, operationIds, renderParity, scan, webCalls } from "../src/api-parity.ts";

const SPEC = `
paths:
  /api/v1/me:
    get: { operationId: getMe }
  /api/v1/instance:
    get: { operationId: getInstance }
    parameters: []
  /api/v1/recipes/{recipeId}/pictures:
    post: { operationId: addRecipePicture }
  /api/v1/recipes:
    get: { operationId: listMyRecipes }
    post: { operationId: createRecipe }
components:
  schemas:
    Example:
      example: { operationId: notAnOperation }
`;
const OPERATIONS = ["addRecipePicture", "createRecipe", "getInstance", "getMe", "listMyRecipes"];

test("operations are the operationIds of the path methods, sorted", () => {
  assert.deepEqual(operationIds(SPEC), OPERATIONS);
});

test("web: a call counts in every shape, an import, a comment, or a string does not", () => {
  const source = `
import { getMe, createRecipe, listMyRecipes, addRecipePicture } from "@mimos/api-client";
// listMyRecipes({ client })
const label = "createRecipe(";
void getMe({ client: apiClient });
const upload = () =>
  addRecipePicture<true>(
    { client: apiClient, path: { recipeId }, body: { file } },
  );
`;
  assert.deepEqual(webCalls(source, OPERATIONS), ["addRecipePicture", "getMe"]);
  assert.deepEqual(webCalls(`import { getMe } from "@mimos/api-client";\nclient.getMe ( {} )`, OPERATIONS), ["getMe"]);
});

test("web: an unused import is not a call, and a file without the generated client is not the API", () => {
  assert.deepEqual(webCalls(`import { getMe } from "@mimos/api-client";\nexport { getMe };`, OPERATIONS), []);
  assert.deepEqual(webCalls(`function getMe() {}\ngetMe();`, OPERATIONS), []);
  assert.deepEqual(webCalls(`import { getMe } from "@mimos/api-client";\nfunction getMe() {}`, OPERATIONS), []);
});

test("app: member calls and references count only in files that use the generated API", () => {
  const generated = `
import io.github.noahhhx.mimos.api.apis.InstanceApi
import io.github.noahhhx.mimos.api.apis.MeApi

/** Its API's \`getInstance\` names the server. */
val unusedMention = "getMe()"
suspend fun load(http: MimosHttp) {
    http.api(::MeApi)
        .getMe()
        .body()
    http.api(::InstanceApi).getInstance().body()
    val create = api::createRecipe
}
`;
  assert.deepEqual(appCalls(generated, OPERATIONS), ["createRecipe", "getInstance", "getMe"]);
});

test("app: Android's keystore getInstance is not Mimos's getInstance", () => {
  const keystore = `
import javax.crypto.Cipher
import java.security.KeyStore

val cipher = Cipher.getInstance(TRANSFORMATION)
val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
`;
  assert.deepEqual(appCalls(keystore, OPERATIONS), []);
  assert.deepEqual(appCalls(`import io.github.noahhhx.mimos.api.apis.MeApi\n// getMe()\n`, OPERATIONS), []);
  const mixed = `import io.github.noahhhx.mimos.api.apis.InstanceApi\nval c = Cipher.getInstance("AES")\nval k = KeyStore\n    .getInstance("x")`;
  assert.deepEqual(appCalls(mixed, OPERATIONS), []);
  assert.deepEqual(appCalls(`${mixed}\nval me = meApi.getMe()`, OPERATIONS), ["getMe"]);
});

test("operations fall into both, web only, app only, and neither, each sorted", () => {
  const parity = classify(OPERATIONS, ["getMe", "createRecipe", "listMyRecipes"], ["getInstance", "getMe"]);
  assert.deepEqual(parity.both, ["getMe"]);
  assert.deepEqual(parity.webOnly, ["createRecipe", "listMyRecipes"]);
  assert.deepEqual(parity.appOnly, ["getInstance"]);
  assert.deepEqual(parity.neither, ["addRecipePicture"]);
  const text = renderParity(parity);
  assert.match(text, /^API operations: 5\. Web calls 3, the app calls 2\./);
  assert.match(text, /Web only \(2\)\n {2}createRecipe\n {2}listMyRecipes\n/);
  assert.equal(renderParity(parity), text);
});

test("a scan reads production sources only", () => {
  const root = mkdtempSync(join(tmpdir(), "parity-"));
  const write = (file: string, text: string): void => {
    mkdirSync(dirname(join(root, file)), { recursive: true });
    writeFileSync(join(root, file), text);
  };
  try {
    write("contracts/api/openapi.yaml", SPEC);
    write("apps/web/src/page.tsx", `import { getMe } from "@mimos/api-client";\nvoid getMe({});`);
    write("apps/web/src/page.test.tsx", `import { createRecipe } from "@mimos/api-client";\ncreateRecipe({});`);
    write("apps/web/src/generated/client.ts", `import { listMyRecipes } from "@mimos/api-client";\nlistMyRecipes();`);
    write("apps/mobile/shared/src/commonMain/kotlin/A.kt", `import io.github.noahhhx.mimos.api.apis.InstanceApi\nfun f() = api.getInstance()`);
    write("apps/mobile/shared/src/commonTest/kotlin/ATest.kt", `import io.github.noahhhx.mimos.api.apis.MeApi\nfun f() = api.getMe()`);
    write("apps/mobile/shared/build/generated/B.kt", `import io.github.noahhhx.mimos.api.apis.MeApi\nfun f() = api.createRecipe()`);
    write("apps/mobile/androidApp/src/main/kotlin/Keystore.kt", `val c = Cipher.getInstance("AES")`);
    const parity = scan(root);
    assert.deepEqual(parity.web, ["getMe"]);
    assert.deepEqual(parity.app, ["getInstance"]);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
