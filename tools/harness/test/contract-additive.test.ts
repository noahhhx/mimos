import assert from "node:assert/strict";
import { test } from "node:test";

import { removalsBetween } from "../src/contract-additive.ts";

const base = `
paths:
  /a:
    get:
      operationId: getA
    post:
      operationId: postA
  /b:
    get:
      operationId: getB
components:
  schemas:
    Thing:
      type: object
      properties:
        id: { type: string }
        kind: { type: string, enum: [one, two] }
        nested:
          type: object
          properties:
            deep: { type: string }
`;

test("a pure addition passes", () => {
  const next = base
    .replace("enum: [one, two]", "enum: [one, two, three]")
    .replace("        id: { type: string }", "        id: { type: string }\n        extra: { type: string }")
    .replace("  /b:", "  /c:\n    get:\n      operationId: getC\n  /b:");
  assert.deepEqual(removalsBetween(base, next), []);
});

test("a removed operation fails, by route and by method", () => {
  assert.deepEqual(removalsBetween(base, base.replace("    post:\n      operationId: postA\n", "")), [
    "removed operation POST /a",
  ]);
  assert.deepEqual(removalsBetween(base, base.replace("  /b:\n    get:\n      operationId: getB\n", "")), [
    "removed operation GET /b",
  ]);
});

test("a removed schema property fails, nested ones too", () => {
  assert.deepEqual(removalsBetween(base, base.replace("        id: { type: string }\n", "")), [
    "removed property id at #/components/schemas/Thing",
  ]);
  assert.deepEqual(removalsBetween(base, base.replace("            deep: { type: string }\n", "            other: { type: string }\n")), [
    "removed property deep at #/components/schemas/Thing/properties/nested",
  ]);
});

test("a removed enum value fails", () => {
  assert.deepEqual(removalsBetween(base, base.replace("enum: [one, two]", "enum: [one]")), [
    'removed enum value "two" at #/components/schemas/Thing/properties/kind',
  ]);
});

const operation = `
paths:
  /list:
    get:
      operationId: list
      parameters:
        - { name: week, in: query, schema: { type: string } }
        - { name: sort, in: query, schema: { type: string, enum: [asc, desc] } }
        - $ref: "#/components/parameters/Limit"
      responses:
        "200": { description: ok }
        "404": { description: missing }
components:
  parameters:
    Limit: { name: limit, in: query, schema: { type: integer } }
  schemas:
    Shape:
      type: object
      required: [id, name]
      properties:
        id: { type: string }
        name: { type: string }
        value:
          oneOf:
            - $ref: "#/components/schemas/Circle"
            - $ref: "#/components/schemas/Square"
            - type: object
              properties: { inline: { type: string } }
`;

test("a parameter inserted before others is a pure addition", () => {
  const next = operation.replace(
    "        - { name: week,",
    "        - { name: first, in: query, schema: { type: string } }\n        - { name: week,",
  );
  assert.deepEqual(removalsBetween(operation, next), []);
});

test("a new response code, optional property, and oneOf member pass", () => {
  const next = operation
    .replace('        "404"', '        "201": { description: made }\n        "404"')
    .replace("        name: { type: string }", "        name: { type: string }\n        tag: { type: string }")
    .replace('            - type: object', '            - $ref: "#/components/schemas/Triangle"\n            - type: object');
  assert.deepEqual(removalsBetween(operation, next), []);
});

test("a removed parameter is reported as one, whatever its position", () => {
  assert.deepEqual(
    removalsBetween(operation, operation.replace("        - { name: week, in: query, schema: { type: string } }\n", "")),
    ["removed parameter week in query at #/paths//list/get/parameters"],
  );
  assert.deepEqual(
    removalsBetween(operation, operation.replace('        - $ref: "#/components/parameters/Limit"\n', "")),
    ["removed parameter #/components/parameters/Limit at #/paths//list/get/parameters"],
  );
});

test("a removed enum value of a parameter is still reported once, in place", () => {
  assert.deepEqual(removalsBetween(operation, operation.replace("enum: [asc, desc]", "enum: [asc]")), [
    'removed enum value "desc" at #/paths//list/get/parameters/sort in query/schema',
  ]);
});

test("a property no longer required fails, but a removed property is reported once", () => {
  assert.deepEqual(removalsBetween(operation, operation.replace("required: [id, name]", "required: [id]")), [
    "removed name from required at #/components/schemas/Shape",
  ]);
  const withoutName = operation
    .replace("required: [id, name]", "required: [id]")
    .replace("        name: { type: string }\n", "");
  assert.deepEqual(removalsBetween(operation, withoutName), [
    "removed property name at #/components/schemas/Shape",
  ]);
});

test("a changed type fails", () => {
  assert.deepEqual(removalsBetween(operation, operation.replace("id: { type: string }", "id: { type: integer }")), [
    'changed type from "string" to "integer" at #/components/schemas/Shape/properties/id',
  ]);
});

test("a removed response status code fails", () => {
  assert.deepEqual(removalsBetween(operation, operation.replace('        "404": { description: missing }\n', "")), [
    "removed response 404 at #/paths//list/get",
  ]);
});

test("a removed oneOf member fails, by $ref or inline position", () => {
  assert.deepEqual(
    removalsBetween(operation, operation.replace('            - $ref: "#/components/schemas/Square"\n', "")),
    ["removed oneOf member #/components/schemas/Square at #/components/schemas/Shape/properties/value/oneOf"],
  );
  assert.deepEqual(
    removalsBetween(
      operation,
      operation.replace("            - type: object\n              properties: { inline: { type: string } }\n", ""),
    ),
    ["removed oneOf member inline #0 at #/components/schemas/Shape/properties/value/oneOf"],
  );
});

test("reordering oneOf members is not a removal", () => {
  const next = operation.replace(
    '            - $ref: "#/components/schemas/Circle"\n            - $ref: "#/components/schemas/Square"\n',
    '            - $ref: "#/components/schemas/Square"\n            - $ref: "#/components/schemas/Circle"\n',
  );
  assert.deepEqual(removalsBetween(operation, next), []);
});
