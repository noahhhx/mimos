import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { parseTrail, readCookState, recipeHref, writeCookState, type CookStorage } from "../src/lib/cook-trail.ts";

const A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const C = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";

function memoryStorage(): CookStorage {
  const values = new Map<string, string>();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => void values.set(key, value),
  };
}

describe("cook state", () => {
  it("reads back what was written, per recipe", () => {
    const storage = memoryStorage();
    writeCookState(storage, A, { title: "Sandwich", ingredients: [0, 2], steps: [1] });

    assert.deepEqual(readCookState(storage, A), { title: "Sandwich", ingredients: [0, 2], steps: [1] });
    assert.equal(readCookState(storage, B), undefined);
  });

  it("ignores what it cannot read", () => {
    const storage = memoryStorage();
    storage.setItem(`mimos.cook.${A}`, "{not json");
    storage.setItem(`mimos.cook.${B}`, JSON.stringify({ ingredients: [1, -1, "2", 1.5], steps: "x" }));

    assert.equal(readCookState(storage, A), undefined);
    assert.deepEqual(readCookState(storage, B), { title: "", ingredients: [1], steps: [] });
  });
});

describe("parseTrail", () => {
  it("reads the recipes followed, first one first", () => {
    assert.deepEqual(parseTrail(`${A},${B}`, C), [A, B]);
  });

  it("is empty without a trail", () => {
    assert.deepEqual(parseTrail(null, C), []);
    assert.deepEqual(parseTrail("", C), []);
  });

  it("drops what is not a recipe id", () => {
    assert.deepEqual(parseTrail(`${A},javascript:alert(1),${B}`, C), [A, B]);
  });

  it("stops before the current recipe or a repeat, so it never loops", () => {
    assert.deepEqual(parseTrail(`${A},${C},${B}`, C), [A]);
    assert.deepEqual(parseTrail(`${A},${B},${A}`, C), [A, B]);
  });
});

describe("recipeHref", () => {
  it("carries the trail that leads to the recipe", () => {
    assert.equal(recipeHref(C, [A, B]), `/app/recipes/${C}?via=${A},${B}`);
    assert.equal(recipeHref(A, []), `/app/recipes/${A}`);
  });
});
