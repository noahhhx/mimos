/**
 * Barcodes on packaged food, for the Log page's scan dialog (ADR-0025).
 * Nothing here touches the browser, so the camera component and the typed
 * field share one reading of what a barcode is.
 */

/** The barcode formats printed on retail food packs, as the Barcode Detection API names them. */
export const FOOD_BARCODE_FORMATS = ["ean_13", "ean_8", "upc_a", "upc_e"] as const;

/**
 * A barcode's digits as the lookup takes them: spaces and dashes are
 * dropped, and what is left must be 8 to 14 digits (EAN-8, UPC-A, EAN-13,
 * GTIN-14). Anything else is not a barcode.
 */
export function parseBarcode(text: string): string | undefined {
  const digits = text.replace(/[\s-]/g, "");
  return /^\d{8,14}$/.test(digits) ? digits : undefined;
}

export type FoodBarcodeFormat = (typeof FOOD_BARCODE_FORMATS)[number];

/** The food formats among those a browser's detector reads. */
export function foodFormatsAmong(supported: readonly string[]): FoodBarcodeFormat[] {
  return FOOD_BARCODE_FORMATS.filter((format) => supported.includes(format));
}

/**
 * Which detector the camera reads with: the browser's own when it reads a
 * food format, or else the bundled zxing one, which reads them all.
 * `nativeFormats` is undefined when the browser has no Barcode Detection
 * API (Safari, Firefox, Chrome on desktop Linux).
 */
export type DetectorChoice = { use: "native"; formats: FoodBarcodeFormat[] } | { use: "polyfill" };

export function chooseDetector(nativeFormats: readonly string[] | undefined): DetectorChoice {
  const formats = foodFormatsAmong(nativeFormats ?? []);
  return formats.length > 0 ? { use: "native", formats } : { use: "polyfill" };
}

/** The product's page on Open Food Facts, which the attribution links to. */
export function openFoodFactsUrl(barcode: string): string {
  return `https://world.openfoodfacts.org/product/${barcode}`;
}

/**
 * Why a lookup found nothing, in words: a 404 is a barcode Open Food Facts
 * does not know; anything else (a 502, no answer at all) is it not
 * answering.
 */
export function lookupFailure(status: number | undefined): string {
  return status === 404
    ? "Open Food Facts doesn't know that barcode. Log it by hand instead."
    : "Open Food Facts didn't answer. Try again, or log it by hand.";
}
