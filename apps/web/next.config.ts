import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Standalone output keeps the runtime container small (apps/web/Dockerfile).
  output: "standalone",
  // The API client ships TypeScript source; Next compiles it with the app.
  transpilePackages: ["@mimos/api-client"],
};

export default nextConfig;
