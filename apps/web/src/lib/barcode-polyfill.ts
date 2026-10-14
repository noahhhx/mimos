import { BarcodeDetector, prepareZXingModule } from "barcode-detector/ponyfill";

/**
 * The bundler copies zxing-wasm's reader into the build's static assets and
 * names it by its content, so the binary always matches the installed
 * package, comes from Mimos's own origin, and is cached for good. Left to
 * itself the package would fetch it from a CDN (ADR-0025).
 */
const READER_WASM = new URL("zxing-wasm/reader/zxing_reader.wasm", import.meta.url).href;

prepareZXingModule({
  overrides: {
    locateFile: (path: string, prefix: string) => (path.endsWith(".wasm") ? READER_WASM : prefix + path),
  },
});

export { BarcodeDetector };
