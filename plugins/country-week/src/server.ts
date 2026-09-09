import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";

import type { PluginManifest, SuggestionContext, PluginSuggestions } from "@mimos/plugin-sdk";

import { suggest } from "./country-week.ts";

/**
 * Country of the Week, served exactly as a third party would build it
 * (ADR-0006): a plain HTTP sidecar with zero runtime dependencies. Mimos
 * calls out; this service never calls back.
 */

export const MANIFEST: PluginManifest = {
  schema: "mimos.plugin.manifest/v1",
  id: "country-week",
  name: "Country of the Week",
  version: "0.1.0",
  apiVersions: ["1"],
  capabilities: ["plan-suggestions"],
  homepageUrl: "https://github.com/noahhhx/mimos",
};

const MAX_BODY_BYTES = 1024 * 1024;

function send(res: ServerResponse, status: number, body: unknown): void {
  const json = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(json) });
  res.end(json);
}

/** Reads and parses a JSON request body; a promise rejection means 400/413. */
async function readJsonBody(req: IncomingMessage): Promise<SuggestionContext> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += (chunk as Buffer).length;
    if (size > MAX_BODY_BYTES) {
      throw new Error("request body too large");
    }
    chunks.push(chunk as Buffer);
  }
  const parsed: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  return parsed as SuggestionContext;
}

/** Minimal shape check on the untrusted-ish context (Mimos sends it, but be honest about input). */
function validContext(context: SuggestionContext): boolean {
  return (
    typeof context.weekStartDate === "string" &&
    /^\d{4}-\d{2}-\d{2}$/.test(context.weekStartDate) &&
    !Number.isNaN(Date.parse(`${context.weekStartDate}T00:00:00Z`)) &&
    Array.isArray(context.plannedSlots) &&
    Array.isArray(context.libraryRecipes) &&
    context.libraryRecipes.every(
      (recipe) =>
        typeof recipe.id === "string" && typeof recipe.title === "string" && Array.isArray(recipe.tags),
    )
  );
}

export function createPluginServer(): Server {
  return createServer((req, res) => {
    const respond = (): void => {
      if (req.method === "GET" && req.url === "/manifest") {
        send(res, 200, MANIFEST);
        return;
      }
      if (req.method === "POST" && req.url === "/v1/plan-suggestions") {
        if ((req.headers["content-type"] ?? "").split(";")[0] !== "application/json") {
          send(res, 415, { error: "expected application/json" });
          return;
        }
        readJsonBody(req)
          .then((context) => {
            if (!validContext(context)) {
              send(res, 400, { error: "malformed suggestion context" });
              return;
            }
            const suggestions: PluginSuggestions = { suggestions: suggest(context) };
            send(res, 200, suggestions);
          })
          .catch(() => {
            send(res, 400, { error: "malformed suggestion context" });
          });
        return;
      }
      send(res, 404, { error: "not found" });
    };
    respond();
  });
}

if (process.env.NODE_ENV !== "test" && process.argv[1] === new URL(import.meta.url).pathname) {
  const port = Number(process.env.PORT ?? 8080);
  createPluginServer().listen(port, "0.0.0.0", () => {
    console.log(`country-week plugin listening on :${port}`);
  });
}
