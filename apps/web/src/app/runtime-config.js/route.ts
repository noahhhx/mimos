import { publicConfigFromEnv, runtimeConfigScript } from "@/lib/public-config";

// Read on every request, never at build time: the image must not carry the
// URLs of the machine that built it (ADR-0012).
export const dynamic = "force-dynamic";

/** The browser's public configuration; the root layout loads it first. */
export function GET() {
  return new Response(runtimeConfigScript(publicConfigFromEnv(process.env)), {
    headers: {
      "Content-Type": "text/javascript; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
