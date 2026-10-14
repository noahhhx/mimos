import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import type { Page } from "@playwright/test";

import { MARMITE, NUTELLA, fixtures } from "../fakes/open-food-facts/src/fixtures.ts";
import { endpoints } from "../src/config.ts";
import { barcodeVideo } from "../src/fake-camera.ts";
import { expect, test } from "./fixtures.ts";

/** The fake Open Food Facts (tools/harness/fakes/open-food-facts), published on loopback by compose.fakes.yml. */
const FAKE = `http://127.0.0.1:${process.env.OPEN_FOOD_FACTS_FAKE_PORT ?? "8091"}`;
const UNKNOWN_BARCODE = "4006381333931";

/**
 * Chromium's camera here is a fake that films Nutella's barcode. A test
 * sees it only once it grants the camera permission; without it,
 * getUserMedia is refused, as when a person says no. The full Chromium
 * runs, since the headless shell has no camera at all (getUserMedia is
 * NotSupportedError there).
 */
const CAMERA_VIDEO = join(tmpdir(), "mimos-harness", "nutella-barcode.y4m");
mkdirSync(join(tmpdir(), "mimos-harness"), { recursive: true });
writeFileSync(CAMERA_VIDEO, barcodeVideo(NUTELLA));
test.use({
  channel: "chromium",
  launchOptions: { args: ["--use-fake-device-for-media-stream", `--use-file-for-fake-video-capture=${CAMERA_VIDEO}`] },
});

const SUGAR = (
  JSON.parse(
    readFileSync(new URL("../../../core/core-recipes/src/main/resources/library/ingredient-seed.json", import.meta.url), "utf8"),
  ) as { slug: string; calories: number }[]
).find((entry) => entry.slug === "sugar")!;
const NUTELLA_KCAL_PER_100_G = (fixtures().get(NUTELLA)!.nutriments as Record<string, number>)["energy-kcal_100g"]!;

/**
 * Log packaged food by its barcode from the Log page (ADR-0025): an
 * ingredient by amount first, then products looked up in the scan dialog
 * against the fake Open Food Facts, typed while the camera is refused.
 * Each expected kcal is worked out from the ingredient seed and the fake's
 * fixtures. What this run logs is deleted at the end, so reruns start from
 * the same day.
 */
test("logs an ingredient by amount, then a product found by its typed barcode", async ({ loggedInPage: page }) => {
  expect((await page.request.post(`${FAKE}/_fake/reset`)).ok(), "the fake resets").toBe(true);
  const entries = page.locator(".log-entries li");
  // Today may hold these from an earlier run, so this run's entries are counted, not assumed alone.
  const sugar = entries.filter({ hasText: "Sugar, 15 g" });
  const nutella = entries.filter({ hasText: "Nutella, 30 g" });
  const dialog = page.getByRole("dialog", { name: "Scan a barcode" });
  const lookupAlert = dialog.getByRole("alert").filter({ hasText: "Open Food Facts" });
  const sugarKcal = `${Math.round((SUGAR.calories * 15) / 100)} kcal`;
  const nutellaKcal = `${Math.round((NUTELLA_KCAL_PER_100_G * 30) / 100)} kcal`;
  let sugarBefore = 0;
  let nutellaBefore = 0;

  const lookUp = async (typed: string) => {
    await dialog.getByLabel("Barcode number").fill(typed);
    await dialog.getByRole("button", { name: "Look up" }).click();
  };

  await test.step("open the log", async () => {
    await page.goto("/app/log");
    await expect(page.getByRole("heading", { name: "Log something else" })).toBeVisible();
    sugarBefore = await sugar.count();
    nutellaBefore = await nutella.count();
  });

  await test.step("log 15 g of sugar picked from the What search", async () => {
    await page.getByLabel("What").fill("sugar");
    await page.getByRole("option", { name: /^Sugar\s*Ingredient · \d+ kcal per 100 g$/ }).click();
    await expect(page.getByLabel("Amount")).toHaveValue("100");
    await page.getByLabel("Amount").fill("15");
    await expect(page.locator(".log-total")).toHaveText(new RegExp(`^Comes to ${sugarKcal}`));
    await page.getByRole("button", { name: "Log meal" }).click();
    await expect(sugar).toHaveCount(sugarBefore + 1);
    await expect(sugar.last()).toContainText(sugarKcal);
  });

  await test.step("a refused camera says so and leaves the typed field", async () => {
    await page.getByRole("button", { name: "Scan a barcode" }).click();
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole("alert")).toHaveText("Mimos can't use the camera. Type the barcode instead.");
    await expect(dialog.getByRole("button", { name: "Look up" })).toBeDisabled();
  });

  await test.step("an unknown barcode keeps the dialog open and says so", async () => {
    await lookUp(UNKNOWN_BARCODE);
    await expect(lookupAlert).toHaveText(
      "Open Food Facts doesn't know that barcode. Log it by hand instead.",
    );
    await expect(dialog).toBeVisible();
  });

  await test.step("Open Food Facts not answering keeps the dialog open and says so", async () => {
    const fault = await page.request.put(`${FAKE}/_fake/fault`, { data: { status: 503, times: 1 } });
    expect(fault.ok(), "the fake fails the next read").toBe(true);
    await lookUp(MARMITE);
    await expect(lookupAlert).toHaveText("Open Food Facts didn't answer. Try again, or log it by hand.");
  });

  await test.step("a product with a serving size starts at one serving, with its brand and source", async () => {
    await lookUp(MARMITE);
    await expect(dialog).toBeHidden();
    await expect(page.getByLabel("What")).toHaveValue("Yeast Extract (Marmite)");
    await expect(page.getByLabel("Amount")).toHaveValue("8");
    await expect(page.getByText("One serving is 8 g.")).toBeVisible();
    await expect(page.getByRole("link", { name: "From Open Food Facts" })).toHaveAttribute(
      "href",
      `https://world.openfoodfacts.org/product/${MARMITE}`,
    );
  });

  await test.step("a typed barcode with spaces finds Nutella, at 100 g without a serving size", async () => {
    await page.getByRole("button", { name: "Scan a barcode" }).click();
    await lookUp("3017620 422003");
    await expect(dialog).toBeHidden();
    await expect(page.getByLabel("What")).toHaveValue("Nutella");
    await expect(page.getByLabel("Amount")).toHaveValue("100");
    await expect(page.getByText(/One serving is/)).toHaveCount(0);
    await expect(page.locator(".log-total")).toHaveText(
      new RegExp(`^Comes to ${Math.round(NUTELLA_KCAL_PER_100_G)} kcal`),
    );
  });

  await test.step("log 30 g of it: the day lists both entries with their kcal", async () => {
    await page.getByLabel("Amount").fill("30");
    await expect(page.locator(".log-total")).toHaveText(new RegExp(`^Comes to ${nutellaKcal}`));
    await page.getByRole("button", { name: "Log meal" }).click();
    await expect(nutella).toHaveCount(nutellaBefore + 1);
    await expect(nutella.last()).toContainText(nutellaKcal);
    await expect(sugar.last()).toContainText(sugarKcal);
    await expect(page.getByLabel("What")).toHaveValue("");
    await expect(page.getByRole("link", { name: "From Open Food Facts" })).toHaveCount(0);
  });

  await test.step("delete what this run logged", async () => {
    await nutella.last().getByRole("button", { name: "Delete" }).click();
    await expect(nutella).toHaveCount(nutellaBefore);
    await sugar.last().getByRole("button", { name: "Delete" }).click();
    await expect(sugar).toHaveCount(sugarBefore);
  });
});

/** The browser globals the camera checks touch; the harness compiles without DOM types. */
type CameraStream = { getTracks(): { readyState: string }[] };
type CameraWindow = {
  cameraStreams: CameraStream[];
  navigator: { mediaDevices: { getUserMedia(constraints: unknown): Promise<CameraStream> } };
};

/** Keeps every stream the page opens, so a test can see that each track was stopped. */
function keepCameraStreams(): void {
  const page = globalThis as unknown as CameraWindow;
  const { mediaDevices } = page.navigator;
  const getUserMedia = mediaDevices.getUserMedia.bind(mediaDevices);
  page.cameraStreams = [];
  mediaDevices.getUserMedia = async (constraints) => {
    const stream = await getUserMedia(constraints);
    page.cameraStreams.push(stream);
    return stream;
  };
}

const liveCameraTracks = (page: Page) =>
  page.evaluate(
    () =>
      (globalThis as unknown as CameraWindow).cameraStreams
        .flatMap((stream) => stream.getTracks())
        .filter((track) => track.readyState === "live").length,
  );

/** What the page has fetched so far: the zxing wasm, and whether any of its scripts holds the zxing polyfill. */
async function zxingFetched(page: Page): Promise<{ wasm: string[]; polyfillCode: boolean }> {
  const urls = await page.evaluate(() => performance.getEntriesByType("resource").map((entry) => entry.name));
  const scripts = urls.filter((url) => new URL(url).pathname.endsWith(".js"));
  const bodies = await Promise.all(scripts.map(async (url) => (await page.request.get(url)).text()));
  return {
    wasm: urls.filter((url) => new URL(url).pathname.endsWith(".wasm")),
    polyfillCode: bodies.some((body) => body.includes("zxing_reader")),
  };
}

test.describe("with the camera allowed", () => {
  test.use({ permissions: ["camera"] });

  /**
   * Headless Chromium has no BarcodeDetector, like Safari and Firefox, so
   * the dialog loads the zxing polyfill and reads the fake camera's
   * Nutella barcode with it. Everything comes from Mimos's own origins:
   * no CDN is asked for zxing's wasm.
   */
  test("scans a barcode with the zxing polyfill, its wasm from the app's origin", async ({ loggedInPage: page }) => {
    expect((await page.request.post(`${FAKE}/_fake/reset`)).ok(), "the fake resets").toBe(true);
    const { web, api, keycloak } = endpoints();
    const origins = new Set<string>();
    page.on("request", (request) => origins.add(new URL(request.url()).origin));
    await page.addInitScript(keepCameraStreams);
    const dialog = page.getByRole("dialog", { name: "Scan a barcode" });

    await test.step("the log page loads none of zxing", async () => {
      await page.goto("/app/log");
      await expect(page.getByRole("heading", { name: "Log something else" })).toBeVisible();
      expect(await zxingFetched(page)).toEqual({ wasm: [], polyfillCode: false });
    });

    await test.step("the camera reads Nutella and the dialog picks it", async () => {
      await page.getByRole("button", { name: "Scan a barcode" }).click();
      await expect(dialog).toBeHidden();
      await expect(page.getByLabel("What")).toHaveValue("Nutella");
      await expect(page.getByLabel("Amount")).toHaveValue("100");
      await expect(page.getByRole("link", { name: "From Open Food Facts" })).toHaveAttribute(
        "href",
        `https://world.openfoodfacts.org/product/${NUTELLA}`,
      );
      expect(await liveCameraTracks(page), "every camera track stopped on the find").toBe(0);
    });

    await test.step("zxing and its wasm came from the app, and nothing from anywhere else", async () => {
      const fetched = await zxingFetched(page);
      expect(fetched.polyfillCode, "the polyfill loaded with the dialog").toBe(true);
      expect(fetched.wasm).toHaveLength(1);
      expect(fetched.wasm[0]).toMatch(new RegExp(`^${web}/_next/static/media/zxing_reader\\.[^/]+\\.wasm$`));
      expect([...origins].filter((origin) => ![web, api, keycloak].includes(origin))).toEqual([]);
    });
  });

  /**
   * Chrome on Android has a BarcodeDetector that reads EAN-13: the dialog
   * uses it and fetches none of zxing. The native detector is stubbed
   * here (headless Chromium has none) and reports Nutella once the camera
   * shows a frame.
   */
  test("uses the browser's own BarcodeDetector when it reads food formats", async ({ loggedInPage: page }) => {
    expect((await page.request.post(`${FAKE}/_fake/reset`)).ok(), "the fake resets").toBe(true);
    await page.addInitScript(keepCameraStreams);
    await page.addInitScript((barcode) => {
      (globalThis as unknown as { BarcodeDetector: unknown }).BarcodeDetector = class {
        static async getSupportedFormats() {
          return ["qr_code", "ean_13", "ean_8"];
        }
        async detect(video: { videoWidth: number }) {
          return video.videoWidth > 0 ? [{ rawValue: barcode }] : [];
        }
      };
    }, NUTELLA);
    const dialog = page.getByRole("dialog", { name: "Scan a barcode" });

    await page.goto("/app/log");
    await page.getByRole("button", { name: "Scan a barcode" }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByLabel("What")).toHaveValue("Nutella");
    expect(await liveCameraTracks(page), "every camera track stopped on the find").toBe(0);
    expect(await zxingFetched(page)).toEqual({ wasm: [], polyfillCode: false });
  });
});
