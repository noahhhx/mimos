import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";

import { ATHLETE_ID, type Calendar, fixtures, GOOD_KEY, type IcuRecord } from "./fixtures.ts";

/**
 * A fake intervals.icu for the compose stack and harness scenarios (item 13
 * of docs/plans/fuel-targets). It serves the four reads Mimos makes
 * (integrations/intervals-icu) for athlete `i1` and one good key, and
 * control routes under `/_fake/` that let a scenario change what it serves,
 * force a 401 or a 429, and read which requests reached it. State lives in
 * memory: a restart or `POST /_fake/reset` puts the fixtures back.
 */

/** A forced answer for every intervals.icu read, `times` reads in a row (or until cleared). */
export interface Fault {
  status: 401 | 429;
  /** Seconds, sent as `Retry-After` on a 429. */
  retryAfter?: number;
  times?: number;
}

/** One intervals.icu read the fake answered. The key itself is never kept. */
export interface LoggedRequest {
  at: string;
  method: string;
  path: string;
  query: Record<string, string>;
  key: "good" | "wrong" | "missing";
  status: number;
}

const MAX_BODY_BYTES = 64 * 1024;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const ATHLETE_PATH = /^\/api\/v1\/athlete\/([^/]+)(\/events|\/activities|\/wellness)?$/;

class HttpError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function send(res: ServerResponse, status: number, body?: unknown, headers: Record<string, string> = {}): void {
  if (body === undefined) {
    res.writeHead(status, headers);
    res.end();
    return;
  }
  const json = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(json), ...headers });
  res.end(json);
}

async function readJsonObject(req: IncomingMessage): Promise<IcuRecord> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += (chunk as Buffer).length;
    if (size > MAX_BODY_BYTES) throw new HttpError(413, "request body too large");
    chunks.push(chunk as Buffer);
  }
  let body: unknown;
  try {
    body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new HttpError(400, "body is not JSON");
  }
  if (typeof body !== "object" || body === null || Array.isArray(body)) {
    throw new HttpError(400, "body must be a JSON object");
  }
  return body as IcuRecord;
}

/** The key from `Authorization: Basic base64(API_KEY:<key>)`, judged against the one good key. */
function keyOf(req: IncomingMessage): LoggedRequest["key"] {
  const header = req.headers.authorization;
  if (!header?.startsWith("Basic ")) return "missing";
  const decoded = Buffer.from(header.slice("Basic ".length), "base64").toString("utf8");
  return decoded === `API_KEY:${GOOD_KEY}` ? "good" : "wrong";
}

/** The inclusive `oldest`/`newest` range every list read must name, as the client always does. */
function range(query: URLSearchParams): { oldest: string; newest: string } {
  const oldest = query.get("oldest");
  const newest = query.get("newest");
  if (!oldest || !newest || !DATE.test(oldest) || !DATE.test(newest)) {
    throw new HttpError(400, "oldest and newest must both be YYYY-MM-DD");
  }
  return { oldest, newest };
}

function inRange(records: Iterable<IcuRecord>, dateOf: (record: IcuRecord) => string, query: URLSearchParams): IcuRecord[] {
  const { oldest, newest } = range(query);
  return [...records]
    .filter((record) => {
      const date = dateOf(record);
      return date >= oldest && date <= newest;
    })
    .sort((a, b) => dateOf(a).localeCompare(dateOf(b)));
}

const startDate = (record: IcuRecord): string => String(record.start_date_local ?? "").slice(0, 10);
const wellnessDate = (record: IcuRecord): string => String(record.id ?? "");

export function createFakeServer(today: () => string = () => new Date().toISOString().slice(0, 10)): Server {
  let calendar: Calendar = fixtures(today());
  let fault: Fault | null = null;
  let requests: LoggedRequest[] = [];

  function read(url: URL, req: IncomingMessage, res: ServerResponse): void {
    const key = keyOf(req);
    const answer = (status: number, body?: unknown, headers: Record<string, string> = {}): void => {
      requests.push({
        at: new Date().toISOString(),
        method: req.method ?? "",
        path: url.pathname,
        query: Object.fromEntries(url.searchParams),
        key,
        status,
      });
      send(res, status, body, headers);
    };

    if (fault !== null) {
      const { status, retryAfter } = fault;
      if (fault.times !== undefined && --fault.times <= 0) fault = null;
      answer(status, { error: "forced by /_fake/fault" }, status === 429 && retryAfter !== undefined ? { "Retry-After": String(retryAfter) } : {});
      return;
    }
    if (key !== "good") {
      answer(401, { error: "Unauthorized" }, { "WWW-Authenticate": 'Basic realm="intervals.icu"' });
      return;
    }
    const match = ATHLETE_PATH.exec(url.pathname);
    if (req.method !== "GET" || match === null) {
      answer(404, { error: "not found" });
      return;
    }
    if (match[1] !== ATHLETE_ID) {
      answer(403, { error: "Access denied" });
      return;
    }
    try {
      const query = url.searchParams;
      switch (match[2]) {
        case undefined:
          answer(200, calendar.athlete);
          return;
        case "/events": {
          const categories = query.get("category")?.split(",");
          const events = inRange(calendar.events.values(), startDate, query);
          answer(200, categories ? events.filter((event) => categories.includes(String(event.category))) : events);
          return;
        }
        case "/activities":
          answer(200, inRange(calendar.activities.values(), startDate, query));
          return;
        case "/wellness":
          answer(200, inRange(calendar.wellness.values(), wellnessDate, query));
          return;
      }
    } catch (error) {
      if (!(error instanceof HttpError)) throw error;
      answer(error.status, { error: error.message });
    }
  }

  async function control(method: string, path: string, req: IncomingMessage, res: ServerResponse): Promise<void> {
    const [resource, id, ...rest] = path.split("/").slice(2);
    const route = rest.length > 0 ? "" : `${method} ${resource}${id === undefined ? "" : "/:id"}`;
    switch (route) {
      case "GET health":
        send(res, 200, { status: "UP" });
        return;
      case "POST reset":
        calendar = fixtures(today());
        fault = null;
        requests = [];
        send(res, 204);
        return;
      case "GET requests":
        send(res, 200, requests);
        return;
      case "PUT fault": {
        const body = await readJsonObject(req);
        const { status, retryAfter, times } = body;
        if ((status !== 401 && status !== 429) || !optionalCount(retryAfter, 0) || !optionalCount(times, 1)) {
          throw new HttpError(400, "a fault is {status: 401 | 429, retryAfter?: seconds, times?: count}");
        }
        fault = { status, ...(retryAfter === undefined ? {} : { retryAfter }), ...(times === undefined ? {} : { times }) } as Fault;
        send(res, 200, fault);
        return;
      }
      case "DELETE fault":
        fault = null;
        send(res, 204);
        return;
      case "PUT events/:id": {
        if (!/^\d+$/.test(id!)) throw new HttpError(400, "an event id is a number");
        const event = { category: "WORKOUT", ...withStartDate(await readJsonObject(req)), id: Number(id) };
        calendar.events.set(event.id, event);
        send(res, 200, event);
        return;
      }
      case "DELETE events/:id":
        send(res, calendar.events.delete(Number(id)) ? 204 : 404);
        return;
      case "PUT activities/:id": {
        const activity = { ...withStartDate(await readJsonObject(req)), id: id! };
        calendar.activities.set(activity.id, activity);
        send(res, 200, activity);
        return;
      }
      case "DELETE activities/:id":
        send(res, calendar.activities.delete(id!) ? 204 : 404);
        return;
      case "PUT wellness/:id": {
        if (!DATE.test(id!)) throw new HttpError(400, "a wellness id is its date, YYYY-MM-DD");
        // Merged into the day's record, so setting body fat keeps the weight.
        const record = { ...calendar.wellness.get(id!), ...(await readJsonObject(req)), id: id! };
        calendar.wellness.set(id!, record);
        send(res, 200, record);
        return;
      }
      default:
        throw new HttpError(404, `no control route ${method} ${path}`);
    }
  }

  return createServer((req, res) => {
    const url = new URL(req.url ?? "/", "http://fake");
    if (!url.pathname.startsWith("/_fake/")) {
      read(url, req, res);
      return;
    }
    control(req.method ?? "", url.pathname, req, res).catch((error: unknown) => {
      if (error instanceof HttpError) {
        send(res, error.status, { error: error.message });
      } else {
        console.error(error);
        send(res, 500, { error: "internal error" });
      }
    });
  });
}

function optionalCount(value: unknown, min: number): boolean {
  return value === undefined || (Number.isInteger(value) && (value as number) >= min);
}

function withStartDate(record: IcuRecord): IcuRecord {
  if (typeof record.start_date_local !== "string" || !DATE.test(record.start_date_local.slice(0, 10))) {
    throw new HttpError(400, "start_date_local must start with YYYY-MM-DD");
  }
  return record;
}

if (process.argv[1] === new URL(import.meta.url).pathname) {
  const port = Number(process.env.PORT ?? 8080);
  createFakeServer().listen(port, "0.0.0.0", () => {
    console.log(`fake intervals.icu listening on :${port} (athlete ${ATHLETE_ID})`);
  });
}
