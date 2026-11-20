import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { describe, it } from "node:test";

import { chooseDetector, foodFormatsAmong, lookupFailure, openFoodFactsUrl, parseBarcode } from "../src/lib/barcode.ts";

// The app's commonTest reads the same file (libraries/test-cases/README.md).
const cases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/barcode.json", import.meta.url), "utf8"),
) as {
  parseCases: { about: string; text: string; expected: string | null }[];
  lookupFailureCases: { status: number | null; message: string }[];
  productPage: { barcode: string; url: string };
};

describe("parseBarcode", () => {
  for (const each of cases.parseCases) {
    it(each.about, () => assert.equal(parseBarcode(each.text), each.expected ?? undefined));
  }
});

describe("foodFormatsAmong", () => {
  it("keeps only the EAN and UPC formats a detector reads", () => {
    assert.deepEqual(foodFormatsAmong(["qr_code", "ean_13", "upc_a", "code_128"]), ["ean_13", "upc_a"]);
    assert.deepEqual(foodFormatsAmong(["qr_code", "aztec"]), []);
  });
});

describe("chooseDetector", () => {
  it("uses the browser's own detector when it reads a food format, with only those formats", () => {
    assert.deepEqual(chooseDetector(["qr_code", "ean_13", "upc_a"]), { use: "native", formats: ["ean_13", "upc_a"] });
  });

  it("loads the polyfill when the browser has no detector, or one that reads no food format", () => {
    assert.deepEqual(chooseDetector(undefined), { use: "polyfill" });
    assert.deepEqual(chooseDetector([]), { use: "polyfill" });
    assert.deepEqual(chooseDetector(["qr_code", "aztec"]), { use: "polyfill" });
  });
});

describe("the zxing reader the web serves", () => {
  it("is the very file the polyfill's own zxing-wasm ships, so the wasm matches its glue code", () => {
    const wasm = "zxing-wasm/reader/zxing_reader.wasm";
    const fromPolyfill = createRequire(import.meta.resolve("barcode-detector/ponyfill")).resolve(wasm);
    const fromWeb = createRequire(new URL("../src/lib/barcode-polyfill.ts", import.meta.url)).resolve(wasm);
    assert.equal(fromWeb, fromPolyfill);
  });
});

describe("lookup results", () => {
  it("links the product's page on Open Food Facts", () => {
    assert.equal(openFoodFactsUrl(cases.productPage.barcode), cases.productPage.url);
  });

  it("tells an unknown barcode from Open Food Facts not answering", () => {
    for (const each of cases.lookupFailureCases) {
      assert.equal(lookupFailure(each.status ?? undefined), each.message, String(each.status));
    }
  });
});
