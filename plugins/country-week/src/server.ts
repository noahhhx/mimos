import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";

import type {
  PanelAction,
  PluginManifest,
  PluginSuggestions,
  SuggestionContext,
  WeekPanel,
  WeekPanelRequest,
} from "@mimos/plugin-sdk";

import { suggest } from "./country-week.ts";
import { openStore, type Store } from "./store.ts";
import { chosenFor, panel, type Rng, step } from "./wheel.ts";

/**
 * Country of the Week, served exactly as a third party would build it
 * (ADR-0006, ADR-0017): a plain HTTP sidecar with zero runtime
 * dependencies and its own SQLite file. Mimos calls out; this service
 * never calls back.
 */

export const MANIFEST: PluginManifest = {
  schema: "mimos.plugin.manifest/v1",
  id: "country-week",
  name: "Country of the Week",
  version: "0.2.0",
  apiVersions: ["1"],
  capabilities: ["plan-suggestions", "week-panel"],
  homepageUrl: "https://github.com/noahhhx/mimos",
};

const MAX_BODY_BYTES = 1024 * 1024;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ACTION_ID = /^[a-z0-9][a-z0-9-]{0,39}$/;

function send(res: ServerResponse, status: number, body: unknown): void {
  const json = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(json) });
  res.end(json);
}

async function readJsonBody(req: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += (chunk as Buffer).length;
    if (size > MAX_BODY_BYTES) {
      throw new Error("request body too large");
    }
    chunks.push(chunk as Buffer);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** A real calendar date in ISO form (2026-02-30 is not). */
function isDate(value: unknown): value is string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false;
  }
  const time = Date.parse(`${value}T00:00:00Z`);
  return !Number.isNaN(time) && new Date(time).toISOString().startsWith(value);
}

function isSubject(value: unknown): value is string {
  return typeof value === "string" && UUID.test(value);
}

function parseSuggestionContext(body: unknown): SuggestionContext | null {
  if (
    !isRecord(body) ||
    !isDate(body.weekStartDate) ||
    (body.subject !== undefined && !isSubject(body.subject)) ||
    !Array.isArray(body.plannedSlots) ||
    !Array.isArray(body.libraryRecipes) ||
    !body.libraryRecipes.every(
      (recipe) =>
        isRecord(recipe) &&
        typeof recipe.id === "string" &&
        typeof recipe.title === "string" &&
        Array.isArray(recipe.tags),
    )
  ) {
    return null;
  }
  return body as SuggestionContext;
}

function parseAction(value: unknown): PanelAction | null {
  if (!isRecord(value) || typeof value.id !== "string" || !ACTION_ID.test(value.id)) {
    return null;
  }
  if (value.value === undefined) {
    return { id: value.id };
  }
  return typeof value.value === "string" && value.value.length <= 100 ? { id: value.id, value: value.value } : null;
}

function parseWeekPanelRequest(body: unknown): WeekPanelRequest | null {
  if (!isRecord(body) || !isSubject(body.subject) || !isDate(body.weekStartDate)) {
    return null;
  }
  if (body.action === undefined) {
    return { subject: body.subject, weekStartDate: body.weekStartDate };
  }
  const action = parseAction(body.action);
  return action === null ? null : { subject: body.subject, weekStartDate: body.weekStartDate, action };
}

function weekPanel(store: Store, rng: Rng, request: WeekPanelRequest): WeekPanel {
  const { effect, state } = step(store.ledger(request.subject), request.weekStartDate, request.action, rng);
  if (effect !== null) {
    store.apply(request.subject, request.weekStartDate, effect);
  }
  return panel(state);
}

function planSuggestions(store: Store, context: SuggestionContext): PluginSuggestions {
  const country = context.subject === undefined ? null : chosenFor(store.ledger(context.subject), context.weekStartDate);
  return { suggestions: suggest(country, context) };
}

/** A JSON POST endpoint: 415 for another content type, 400 for a body `parse` rejects, 500 when `handle` throws. */
function jsonRoute<T>(
  req: IncomingMessage,
  res: ServerResponse,
  parse: (body: unknown) => T | null,
  handle: (input: T) => unknown,
): void {
  if ((req.headers["content-type"] ?? "").split(";")[0] !== "application/json") {
    send(res, 415, { error: "expected application/json" });
    return;
  }
  readJsonBody(req).then(
    (body) => {
      const input = parse(body);
      if (input === null) {
        send(res, 400, { error: "malformed request" });
        return;
      }
      try {
        send(res, 200, handle(input));
      } catch (error) {
        console.error(error);
        send(res, 500, { error: "internal error" });
      }
    },
    () => {
      send(res, 400, { error: "malformed request" });
    },
  );
}

export function createPluginServer(store: Store, rng: Rng): Server {
  return createServer((req, res) => {
    if (req.method === "GET" && req.url === "/manifest") {
      send(res, 200, MANIFEST);
    } else if (req.method === "POST" && req.url === "/v1/plan-suggestions") {
      jsonRoute(req, res, parseSuggestionContext, (context) => planSuggestions(store, context));
    } else if (req.method === "POST" && req.url === "/v1/week-panel") {
      jsonRoute(req, res, parseWeekPanelRequest, (request) => weekPanel(store, rng, request));
    } else {
      send(res, 404, { error: "not found" });
    }
  });
}

if (process.env.NODE_ENV !== "test" && process.argv[1] === new URL(import.meta.url).pathname) {
  const port = Number(process.env.PORT ?? 8080);
  const path = process.env.COUNTRY_WEEK_DB ?? ":memory:";
  if (path === ":memory:") {
    console.log("COUNTRY_WEEK_DB is not set: choices last until the plugin stops");
  }
  createPluginServer(openStore(path), Math.random).listen(port, "0.0.0.0", () => {
    console.log(`country-week plugin listening on :${port}`);
  });
}
