import assert from "node:assert/strict";
import { test } from "node:test";

import { HarnessError } from "../src/args.ts";
import { barcodeVideo, ean13Modules } from "../src/fake-camera.ts";

test("draws Nutella's EAN-13 as the modules zxing-wasm 3.1.3 read back as 3017620422003", () => {
  assert.equal(
    ean13Modules("3017620422003"),
    "10100011010011001001000100001010011011000110101010101110011011001101100111001011100101000010101",
  );
});

test("refuses a code that is not 13 digits or whose check digit is wrong", () => {
  assert.throws(() => ean13Modules("301762042200"), HarnessError);
  assert.throws(() => ean13Modules("301762042200X"), HarnessError);
  assert.throws(() => ean13Modules("3017620422004"), HarnessError);
});

test("the video is one 640x480 4:2:0 Y4M frame: bars dark, quiet zone and chroma neutral", () => {
  const video = barcodeVideo("3017620422003");
  const header = "YUV4MPEG2 W640 H480 F5:1 Ip A1:1 C420jpeg\nFRAME\n";
  assert.equal(video.subarray(0, header.length).toString(), header);
  assert.equal(video.length, header.length + 640 * 480 * 1.5);

  const row = video.subarray(header.length + 240 * 640, header.length + 241 * 640);
  assert.equal(row[0], 235, "the quiet zone is white");
  assert.equal(row[130], 16, "the start guard's first bar is black");
  assert.equal(row[134], 235, "then a space");
  assert.equal(video.at(-1), 128, "chroma is neutral grey");
});
