import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { describe, it } from "node:test";

import { chooseDetector, foodFormatsAmong, lookupFailure, openFoodFactsUrl, parseBarcode } from "../src/lib/barcode.ts";

describe("parseBarcode", () => {
  it("keeps 8 to 14 digits as typed", () => {
    assert.equal(parseBarcode("50184453"), "50184453");
    assert.equal(parseBarcode("3017620422003"), "3017620422003");
    assert.equal(parseBarcode("10012345678902"), "10012345678902");
  });

  it("drops spaces and dashes, as barcodes are printed and read aloud", () => {
    assert.equal(parseBarcode(" 3 017620 422003 "), "3017620422003");
    assert.equal(parseBarcode("3017-6204-22003"), "3017620422003");
  });

  it("refuses too few or too many digits, and anything that is not a digit", () => {
    assert.equal(parseBarcode(""), undefined);
    assert.equal(parseBarcode("1234567"), undefined);
    assert.equal(parseBarcode("123456789012345"), undefined);
    assert.equal(parseBarcode("30176204220O3"), undefined);
    assert.equal(parseBarcode("3017620.422003"), undefined);
  });
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
    assert.equal(openFoodFactsUrl("3017620422003"), "https://world.openfoodfacts.org/product/3017620422003");
  });

  it("tells an unknown barcode from Open Food Facts not answering", () => {
    assert.equal(lookupFailure(404), "Open Food Facts doesn't know that barcode. Log it by hand instead.");
    assert.equal(lookupFailure(502), "Open Food Facts didn't answer. Try again, or log it by hand.");
    assert.equal(lookupFailure(undefined), "Open Food Facts didn't answer. Try again, or log it by hand.");
  });
});
