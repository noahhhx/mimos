import { publicConfigFromEnv, runtimeConfigJson } from "@/lib/public-config";

// Read on every request, never at build time: the image must not carry the
// URLs of the machine that built it (ADR-0012).
export const dynamic = "force-dynamic";

/** The public configuration for apps that are not a browser, such as the Android app. */
export function GET() {
  return new Response(runtimeConfigJson(publicConfigFromEnv(process.env)), {
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
