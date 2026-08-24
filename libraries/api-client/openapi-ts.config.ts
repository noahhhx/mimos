import { defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
  input: "../../contracts/api/openapi.yaml",
  output: {
    path: "src/client",
    
  },
  plugins: ["@hey-api/typescript", "@hey-api/client-fetch", "@hey-api/sdk"],
});
