import { HarnessError } from "./args.ts";
import { endpoints } from "./config.ts";
import { send, type HttpResponse } from "./http.ts";

/**
 * The API's Spring Boot actuator. The product stack exposes health only;
 * the debug overlay (deploy/docker/compose.debug.yml) adds the diagnostics
 * endpoints, each behind any valid bearer token.
 */

export function actuator(path: string, token: string, init: { method?: string; body?: unknown; accept?: string } = {}): Promise<HttpResponse> {
  const body = init.body === undefined ? undefined : Buffer.from(JSON.stringify(init.body));
  const headers: Record<string, string> = { Authorization: `Bearer ${token}`, Accept: init.accept ?? "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    headers["Content-Length"] = String(body.length);
  }
  return send(init.method ?? "GET", new URL(`/actuator${path}`, endpoints().api), headers, body);
}

/** Exposed endpoint IDs, from the discovery page's links (`loggers-name` and the like are templates, not endpoints). */
export function exposedEndpoints(discovery: unknown): string[] {
  const links = (discovery as { _links?: Record<string, { templated?: boolean }> } | null)?._links ?? {};
  return Object.entries(links)
    .filter(([name, link]) => name !== "self" && !link.templated)
    .map(([name]) => name)
    .sort();
}

export async function discover(token: string): Promise<string[]> {
  const response = await actuator("", token);
  if (response.status !== 200) {
    throw new HarnessError(`GET /actuator → ${response.status} ${response.statusText}`);
  }
  return exposedEndpoints(JSON.parse(response.body));
}

/** Health components (dotted paths for nested groups) whose status is not UP. */
export function unhealthyComponents(health: unknown, prefix = ""): string[] {
  const components = (health as { components?: Record<string, { status?: string; components?: unknown }> } | null)?.components ?? {};
  return Object.entries(components).flatMap(([name, component]) => {
    const path = prefix ? `${prefix}.${name}` : name;
    if (component.components !== undefined) return unhealthyComponents(component, path);
    return component.status === "UP" ? [] : [`${path}: ${component.status ?? "unknown"}`];
  });
}

interface MediaTypeCondition {
  mediaType: string;
  negated: boolean;
}

interface Mapping {
  handler: string;
  predicate: string;
  details: {
    requestMappingConditions?: {
      consumes: MediaTypeCondition[];
      produces: MediaTypeCondition[];
      headers: { name: string; value: string | null; negated: boolean }[];
      params: { name: string; value: string | null; negated: boolean }[];
      methods: string[];
      patterns: string[];
    };
  } | null;
}

export interface Route {
  methods: string;
  path: string;
  consumes: string;
  produces: string;
  /** `Controller#method(Args)`, package dropped; resource handlers as Boot names them. */
  handler: string;
}

function mediaTypes(conditions: readonly MediaTypeCondition[]): string {
  return conditions.map((condition) => `${condition.negated ? "!" : ""}${condition.mediaType}`).join(", ") || "*";
}

function nameValue(conditions: readonly { name: string; value: string | null; negated: boolean }[]): string[] {
  return conditions.map((condition) => `${condition.name}${condition.value === null ? "" : `${condition.negated ? "!=" : "="}${condition.value}`}`);
}

/** Every DispatcherServlet mapping as a route, sorted by path then method. */
export function routes(mappings: unknown): Route[] {
  const contexts = (mappings as { contexts?: Record<string, { mappings?: { dispatcherServlets?: Record<string, Mapping[]> } }> } | null)?.contexts ?? {};
  const result: Route[] = [];
  for (const context of Object.values(contexts)) {
    for (const servlet of Object.values(context.mappings?.dispatcherServlets ?? {})) {
      for (const mapping of servlet) {
        const conditions = mapping.details?.requestMappingConditions;
        if (conditions === undefined) {
          result.push({ methods: "*", path: mapping.predicate, consumes: "*", produces: "*", handler: mapping.handler });
          continue;
        }
        const extra = [
          ...nameValue(conditions.params).map((param) => `params ${param}`),
          ...nameValue(conditions.headers).map((header) => `header ${header}`),
        ];
        for (const pattern of conditions.patterns) {
          result.push({
            methods: conditions.methods.join(",") || "*",
            path: extra.length ? `${pattern} [${extra.join("; ")}]` : pattern,
            consumes: mediaTypes(conditions.consumes),
            produces: mediaTypes(conditions.produces),
            handler: mapping.handler.replace(/^[\w.$]*\.(?=[A-Z])/, ""),
          });
        }
      }
    }
  }
  return result.sort((a, b) => a.path.localeCompare(b.path) || a.methods.localeCompare(b.methods));
}

/**
 * routes() as text — what `diag/routes.txt` holds: method and path aligned,
 * then the media-type conditions (left out when any type goes) and the handler.
 */
export function routeTable(list: readonly Route[]): string {
  const methods = Math.max(...list.map((route) => route.methods.length));
  const paths = Math.max(...list.map((route) => route.path.length));
  return list
    .map((route) =>
      [
        route.methods.padEnd(methods),
        route.path.padEnd(paths),
        ...(route.consumes === "*" ? [] : [`consumes=${route.consumes.replaceAll(" ", "")}`]),
        ...(route.produces === "*" ? [] : [`produces=${route.produces.replaceAll(" ", "")}`]),
        `→ ${route.handler}`,
      ].join("  "),
    )
    .join("\n")
    .concat("\n");
}

export const LOG_LEVELS = ["OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE"] as const;

export interface LoggerLevel {
  /** Null when the logger inherits its level. */
  configuredLevel: string | null;
  effectiveLevel: string;
}

export async function loggerLevel(token: string, logger: string): Promise<LoggerLevel> {
  const response = await actuator(`/loggers/${encodeURIComponent(logger)}`, token);
  if (response.status === 404) {
    throw new HarnessError(
      "the loggers endpoint is not exposed — start the stack with the debug overlay: harness up --debug",
    );
  }
  if (response.status !== 200) {
    throw new HarnessError(`GET /actuator/loggers/${logger} → ${response.status} ${response.statusText}`);
  }
  const parsed = JSON.parse(response.body) as { configuredLevel?: string | null; effectiveLevel?: string };
  return { configuredLevel: parsed.configuredLevel ?? null, effectiveLevel: parsed.effectiveLevel ?? "?" };
}

/** Sets a logger's configured level; null clears it (the logger inherits again). */
export async function setLoggerLevel(token: string, logger: string, level: string | null): Promise<void> {
  const response = await actuator(`/loggers/${encodeURIComponent(logger)}`, token, {
    method: "POST",
    body: { configuredLevel: level },
  });
  if (response.status !== 204 && response.status !== 200) {
    throw new HarnessError(`POST /actuator/loggers/${logger} → ${response.status} ${response.statusText} ${response.body.slice(0, 200)}`);
  }
}
