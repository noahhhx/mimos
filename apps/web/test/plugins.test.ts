import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { pluginHomepage } from "../src/lib/plugins.ts";

describe("pluginHomepage", () => {
  it("keeps an http(s) URL", () => {
    assert.equal(pluginHomepage("https://example.org/mimos/country-week"), "https://example.org/mimos/country-week");
    assert.equal(pluginHomepage("http://plugin.local:8080/"), "http://plugin.local:8080/");
  });

  it("drops anything else", () => {
    assert.equal(pluginHomepage(undefined), null);
    assert.equal(pluginHomepage(""), null);
    assert.equal(pluginHomepage("javascript:alert(1)"), null);
    assert.equal(pluginHomepage("data:text/html,<p>hi</p>"), null);
    assert.equal(pluginHomepage("/relative"), null);
    assert.equal(pluginHomepage("not a url"), null);
  });
});
