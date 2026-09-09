import { defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
  input: "../../contracts/plugins/plan-suggestions/v1/openapi.yaml",
  output: {
    path: "src",
  },
  // Types only: a plugin *serves* this API, so the SDK is the contract's
  // shape, not a client (ADR-0006).
  plugins: ["@hey-api/typescript"],
});
