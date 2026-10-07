import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { runInNewContext } from "node:vm";

import {
  DEFAULT_PUBLIC_CONFIG,
  PUBLIC_CONFIG_GLOBAL,
  publicConfigFromEnv,
  runtimeConfigScript,
} from "../src/lib/public-config.ts";

describe("publicConfigFromEnv", () => {
  it("defaults to the local compose stack", () => {
    assert.deepEqual(publicConfigFromEnv({}), DEFAULT_PUBLIC_CONFIG);
  });

  it("treats blank values as unset", () => {
    assert.deepEqual(publicConfigFromEnv({ MIMOS_API_URL: " ", MIMOS_OIDC_CLIENT_ID: "" }), DEFAULT_PUBLIC_CONFIG);
  });

  it("reads every URL and drops trailing slashes", () => {
    const config = publicConfigFromEnv({
      MIMOS_API_URL: "https://api.mimos.home/",
      MIMOS_OIDC_AUTHORITY: "https://auth.mimos.home/realms/mimos/",
      MIMOS_OIDC_CLIENT_ID: "mimos-web",
      MIMOS_APP_URL: "https://mimos.home",
    });

    assert.deepEqual(config, {
      apiUrl: "https://api.mimos.home",
      oidcAuthority: "https://auth.mimos.home/realms/mimos",
      oidcClientId: "mimos-web",
      appUrl: "https://mimos.home",
      signup: "open",
    });
  });

  it("offers sign-up when MIMOS_SIGNUP is unset, blank, or open in any case", () => {
    for (const value of [undefined, "", "  ", "open", " OPEN ", "Open"]) {
      assert.equal(publicConfigFromEnv({ MIMOS_SIGNUP: value }).signup, "open", `MIMOS_SIGNUP=${JSON.stringify(value)}`);
    }
  });

  it("closes sign-up for any other value", () => {
    for (const value of ["closed", "off", "false", "disabled", "no", "opened"]) {
      assert.equal(publicConfigFromEnv({ MIMOS_SIGNUP: value }).signup, "closed", `MIMOS_SIGNUP=${value}`);
    }
  });
});

describe("runtimeConfigScript", () => {
  it("sets the config global the browser reads", () => {
    const window: Record<string, unknown> = {};
    runInNewContext(runtimeConfigScript(DEFAULT_PUBLIC_CONFIG), { window });

    // Copied out of the VM context, whose Object prototype differs.
    assert.deepEqual({ ...(window[PUBLIC_CONFIG_GLOBAL] as object) }, DEFAULT_PUBLIC_CONFIG);
  });

  it("cannot close a script element", () => {
    const script = runtimeConfigScript({ ...DEFAULT_PUBLIC_CONFIG, oidcClientId: "</script><b>" });

    assert.ok(!script.includes("</script>"));
    const window: Record<string, unknown> = {};
    runInNewContext(script, { window });
    assert.equal((window[PUBLIC_CONFIG_GLOBAL] as { oidcClientId: string }).oidcClientId, "</script><b>");
  });
});
