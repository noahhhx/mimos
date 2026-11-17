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
