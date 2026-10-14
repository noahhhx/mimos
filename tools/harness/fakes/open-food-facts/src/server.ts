import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";

import { fixtures, type OffProduct } from "./fixtures.ts";

/**
 * A fake Open Food Facts for the compose stack and harness scenarios
 * (ADR-0025). It serves the one read Mimos makes
 * (integrations/open-food-facts), `GET /api/v2/product/{barcode}`, as the
 * live API answers it: `status: 1` and the product (only the asked-for
 * `fields`) when it has one, a 404 with `status: 0` otherwise. Control
 * routes under `/_fake/` let a scenario add or remove products, force an
 * outage, and read which lookups reached it. State lives in memory: a
 * restart or `POST /_fake/reset` puts the fixtures back.
 */

/** A forced answer for every product read, `times` reads in a row (or until cleared). */
export interface Fault {
  status: 429 | 500 | 503;
  times?: number;
}

/** One product read the fake answered. */
export interface LoggedRequest {
  at: string;
  barcode: string;
  fields: string[] | null;
  userAgent: string | null;
  status: number;
}

const MAX_BODY_BYTES = 64 * 1024;
const PRODUCT_PATH = /^\/api\/v2\/product\/([^/]+)$/;
const FAULT_STATUSES = new Set([429, 500, 503]);

class HttpError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function send(res: ServerResponse, status: number, body?: unknown): void {
  if (body === undefined) {
    res.writeHead(status);
    res.end();
    return;
  }
  const json = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(json) });
  res.end(json);
}

async function readJsonObject(req: IncomingMessage): Promise<OffProduct> {
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
  return body as OffProduct;
}

/** The product cut to `fields`, as Open Food Facts does; every field without one. */
function only(product: OffProduct, fields: string[] | null): OffProduct {
  if (fields === null) return product;
  return Object.fromEntries(Object.entries(product).filter(([key]) => fields.includes(key)));
}

export function createFakeServer(): Server {
  let products = fixtures();
  let fault: Fault | null = null;
  let requests: LoggedRequest[] = [];

  function read(url: URL, req: IncomingMessage, res: ServerResponse): void {
    const match = PRODUCT_PATH.exec(url.pathname);
    if (req.method !== "GET" || match === null) {
      send(res, 404, { error: "not found" });
      return;
    }
    const barcode = decodeURIComponent(match[1]!);
    const fields = url.searchParams.get("fields")?.split(",") ?? null;
    const answer = (status: number, body: unknown): void => {
      requests.push({ at: new Date().toISOString(), barcode, fields, userAgent: req.headers["user-agent"] ?? null, status });
      send(res, status, body);
    };

    if (fault !== null) {
      const { status } = fault;
      if (fault.times !== undefined && --fault.times <= 0) fault = null;
      answer(status, { error: "forced by /_fake/fault" });
      return;
    }
    const product = products.get(barcode);
    if (product === undefined) {
      answer(404, { code: barcode, status: 0, status_verbose: "product not found" });
      return;
    }
    answer(200, { code: barcode, product: only(product, fields), status: 1, status_verbose: "product found" });
  }

  async function control(method: string, path: string, req: IncomingMessage, res: ServerResponse): Promise<void> {
    const [resource, id, ...rest] = path.split("/").slice(2);
    const route = rest.length > 0 ? "" : `${method} ${resource}${id === undefined ? "" : "/:id"}`;
    switch (route) {
      case "GET health":
        send(res, 200, { status: "UP" });
        return;
      case "POST reset":
        products = fixtures();
        fault = null;
        requests = [];
        send(res, 204);
        return;
      case "GET requests":
        send(res, 200, requests);
        return;
      case "PUT fault": {
        const { status, times } = await readJsonObject(req);
        if (typeof status !== "number" || !FAULT_STATUSES.has(status) || !(times === undefined || (Number.isInteger(times) && (times as number) >= 1))) {
          throw new HttpError(400, "a fault is {status: 429 | 500 | 503, times?: count}");
        }
        fault = { status, ...(times === undefined ? {} : { times }) } as Fault;
        send(res, 200, fault);
        return;
      }
      case "DELETE fault":
        fault = null;
        send(res, 204);
        return;
      case "PUT products/:id": {
        if (!/^\d{8,14}$/.test(id!)) throw new HttpError(400, "a barcode is 8 to 14 digits");
        const product = { ...(await readJsonObject(req)), code: id! };
        products.set(id!, product);
        send(res, 200, product);
        return;
      }
      case "DELETE products/:id":
        send(res, products.delete(id!) ? 204 : 404);
        return;
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

if (process.argv[1] === new URL(import.meta.url).pathname) {
  const port = Number(process.env.PORT ?? 8080);
  createFakeServer().listen(port, "0.0.0.0", () => {
    console.log(`fake Open Food Facts listening on :${port}`);
  });
}
