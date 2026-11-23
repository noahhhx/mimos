import assert from "node:assert/strict";
import { test } from "node:test";

import { check } from "../src/repo-rules.ts";

const rulesBroken = (path: string, text: string) => check(path, text).map((v) => `${v.rule} at ${v.line}`);

test("an em-dash in a web string, an app string, a seed recipe, or an API message fails", () => {
  assert.deepEqual(rulesBroken("apps/web/src/app/page.tsx", "const a = 1;\n<p>Plan — then cook</p>"), [
    "no em-dash in user-facing copy at 2",
  ]);
  assert.equal(rulesBroken("apps/mobile/shared/src/commonMain/kotlin/A.kt", 'Text("a — b")').length, 1);
  assert.equal(
    rulesBroken("core/core-recipes/src/main/resources/library/library-seed.json", '{"t":"a — b"}').length,
    1,
  );
  assert.equal(rulesBroken("apps/api/src/main/java/X.java", 'throw new IllegalArgumentException("a — b");').length, 1);
  assert.equal(rulesBroken("deploy/keycloak/themes/mimos/login/resources/js/theme.js", 'label = "a — b";').length, 1);
});

test("an em-dash in a comment, a doc, or a test passes", () => {
  const commented = "// a — b\nconst url = 'http://x'; // c — d\n/**\n * e — f\n */\n{/* g — h */}\n<!-- i — j -->";
  assert.deepEqual(rulesBroken("apps/web/src/app/page.tsx", commented), []);
  assert.deepEqual(rulesBroken("docs/guide/index.md", "a — b"), []);
  assert.deepEqual(rulesBroken("plugins/country-week/src/wheel.test.ts", '"a — b"'), []);
});

test("an en dash for a range passes", () => {
  assert.deepEqual(rulesBroken("apps/web/src/app/page.tsx", "<p>20–25 minutes</p>"), []);
});

test("a NEXT_PUBLIC_ variable anywhere in the web app fails", () => {
  assert.deepEqual(rulesBroken("apps/web/next.config.ts", "process.env.NEXT_PUBLIC_API_URL"), [
    "no NEXT_PUBLIC_ variables in the web app at 1",
  ]);
});

test("a literal color in a component fails, and globals.css may define them", () => {
  assert.equal(rulesBroken("apps/web/src/components/Card.tsx", 'style={{ color: "#c2410c" }}').length, 1);
  assert.equal(rulesBroken("apps/web/src/components/card.css", "border: 1px solid rgba(0, 0, 0, 0.1);").length, 1);
  assert.deepEqual(rulesBroken("apps/web/src/app/globals.css", "--accent: #c2410c;"), []);
  assert.deepEqual(rulesBroken("apps/web/src/components/Card.tsx", 'style={{ color: "var(--accent)" }}'), []);
});

test("a source package named target fails once per file", () => {
  assert.deepEqual(rulesBroken("core/core-fueling/src/main/java/io/x/target/A.java", "a\nb"), [
    "no source directory named target at 1",
  ]);
  assert.deepEqual(rulesBroken("core/core-fueling/src/main/java/io/x/targets/A.java", "a"), []);
});
