import assert from "node:assert/strict";
import { test } from "node:test";

import { exposedEndpoints, routes, routeTable, unhealthyComponents } from "../src/actuator.ts";
import { remember } from "../src/commands/loglevel.ts";

const JSON_TYPES = [
  { mediaType: "application/json", negated: false },
  { mediaType: "application/problem+json", negated: false },
];

/** Trimmed from a real `GET /actuator/mappings` (Boot 4). */
const MAPPINGS = {
  contexts: {
    "mimos-api": {
      mappings: {
        dispatcherServlets: {
          dispatcherServlet: [
            {
              handler: "io.github.noahhhx.mimos.api.recipes.RecipesController#createRecipe(RecipeInput)",
              predicate: "{POST [/api/v1/recipes], consumes [application/json], produces [application/json || application/problem+json]}",
              details: {
                handlerMethod: { className: "io.github.noahhhx.mimos.api.recipes.RecipesController", name: "createRecipe" },
                requestMappingConditions: {
                  consumes: [{ mediaType: "application/json", negated: false }],
                  headers: [],
                  methods: ["POST"],
                  params: [],
                  patterns: ["/api/v1/recipes"],
                  produces: JSON_TYPES,
                },
              },
            },
            {
              handler: "io.github.noahhhx.mimos.api.recipes.RecipesController#listMyRecipes(String)",
              predicate: "{GET [/api/v1/recipes], produces [application/json || application/problem+json]}",
              details: {
                requestMappingConditions: {
                  consumes: [],
                  headers: [{ name: "X-Debug", value: null, negated: false }],
                  methods: ["GET"],
                  params: [{ name: "q", value: "x", negated: true }],
                  patterns: ["/api/v1/recipes"],
                  produces: JSON_TYPES,
                },
              },
            },
            {
              handler: "org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController#error(HttpServletRequest)",
              predicate: "{ [/error]}",
              details: {
                requestMappingConditions: {
                  consumes: [{ mediaType: "text/plain", negated: true }],
                  headers: [],
                  methods: [],
                  params: [],
                  patterns: ["/error", "/error/"],
                  produces: [],
                },
              },
            },
            { handler: "ResourceHttpRequestHandler [classpath [META-INF/resources/webjars/]]", predicate: "/webjars/**", details: null },
          ],
        },
      },
    },
  },
};

test("routes: one per pattern, with media types, conditions, and short handler names, sorted by path", () => {
  assert.deepEqual(routes(MAPPINGS), [
    {
      methods: "POST",
      path: "/api/v1/recipes",
      consumes: "application/json",
      produces: "application/json, application/problem+json",
      handler: "RecipesController#createRecipe(RecipeInput)",
    },
    {
      methods: "GET",
      path: "/api/v1/recipes [params q!=x; header X-Debug]",
      consumes: "*",
      produces: "application/json, application/problem+json",
      handler: "RecipesController#listMyRecipes(String)",
    },
    { methods: "*", path: "/error", consumes: "!text/plain", produces: "*", handler: "BasicErrorController#error(HttpServletRequest)" },
    { methods: "*", path: "/error/", consumes: "!text/plain", produces: "*", handler: "BasicErrorController#error(HttpServletRequest)" },
    { methods: "*", path: "/webjars/**", consumes: "*", produces: "*", handler: "ResourceHttpRequestHandler [classpath [META-INF/resources/webjars/]]" },
  ]);
  assert.deepEqual(routes({}), []);
});

test("route table: method and path aligned, wildcard media types left out", () => {
  const lines = routeTable(routes(MAPPINGS)).split("\n");
  assert.equal(
    lines[0],
    "POST  /api/v1/recipes                                consumes=application/json  produces=application/json,application/problem+json  → RecipesController#createRecipe(RecipeInput)",
  );
  assert.equal(lines[2], "*     /error                                         consumes=!text/plain  → BasicErrorController#error(HttpServletRequest)");
  assert.equal(lines.at(-1), "", "ends with a newline");
});

test("exposed endpoints come from the discovery links, minus self and templates", () => {
  const discovery = {
    _links: {
      self: { href: "http://localhost:8080/actuator", templated: false },
      health: { href: "…/health", templated: false },
      "health-path": { href: "…/health/{*path}", templated: true },
      loggers: { href: "…/loggers", templated: false },
      "loggers-name": { href: "…/loggers/{name}", templated: true },
    },
  };
  assert.deepEqual(exposedEndpoints(discovery), ["health", "loggers"]);
  assert.deepEqual(exposedEndpoints(null), []);
});

test("unhealthy health components are named, nested ones by path", () => {
  const health = {
    status: "DOWN",
    components: {
      db: { status: "DOWN", details: { error: "Connection refused" } },
      ping: { status: "UP" },
      groups: { status: "DOWN", components: { a: { status: "UP" }, b: { status: "OUT_OF_SERVICE" } } },
    },
  };
  assert.deepEqual(unhealthyComponents(health), ["db: DOWN", "groups.b: OUT_OF_SERVICE"]);
  assert.deepEqual(unhealthyComponents({ status: "UP" }), []);
});

test("loglevel remembers a logger's original level once, so a second change keeps the first original", () => {
  const first = remember({}, "org.springframework.web", { configuredLevel: null, effectiveLevel: "INFO" });
  assert.deepEqual(first, { "org.springframework.web": null });
  const second = remember(first, "org.springframework.web", { configuredLevel: "DEBUG", effectiveLevel: "DEBUG" });
  assert.deepEqual(second, { "org.springframework.web": null });
  assert.deepEqual(remember(second, "ROOT", { configuredLevel: "INFO", effectiveLevel: "INFO" }), {
    "org.springframework.web": null,
    ROOT: "INFO",
  });
});
