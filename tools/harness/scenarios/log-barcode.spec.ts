import { readFileSync } from "node:fs";

import { MARMITE, NUTELLA, fixtures } from "../fakes/open-food-facts/src/fixtures.ts";
import { expect, test } from "./fixtures.ts";

/** The fake Open Food Facts (tools/harness/fakes/open-food-facts), published on loopback by compose.fakes.yml. */
const FAKE = `http://127.0.0.1:${process.env.OPEN_FOOD_FACTS_FAKE_PORT ?? "8091"}`;
const UNKNOWN_BARCODE = "4006381333931";

const SUGAR = (
  JSON.parse(
    readFileSync(new URL("../../../core/core-recipes/src/main/resources/library/ingredient-seed.json", import.meta.url), "utf8"),
  ) as { slug: string; calories: number }[]
).find((entry) => entry.slug === "sugar")!;
const NUTELLA_KCAL_PER_100_G = (fixtures().get(NUTELLA)!.nutriments as Record<string, number>)["energy-kcal_100g"]!;

/**
 * Log packaged food by its barcode from the Log page (ADR-0025): an
 * ingredient by amount first, then products looked up in the scan dialog
 * against the fake Open Food Facts. Headless Chromium has no
 * BarcodeDetector, so the dialog offers only the typed Barcode number
 * field here, and the camera path is never covered in CI. Each expected
 * kcal is worked out from the ingredient seed and the fake's fixtures.
 * What this run logs is deleted at the end, so reruns start from the same day.
 */
test("logs an ingredient by amount, then a product found by its typed barcode", async ({ loggedInPage: page }) => {
  expect((await page.request.post(`${FAKE}/_fake/reset`)).ok(), "the fake resets").toBe(true);
  const entries = page.locator(".log-entries li");
  // Today may hold these from an earlier run, so this run's entries are counted, not assumed alone.
  const sugar = entries.filter({ hasText: "Sugar, 15 g" });
  const nutella = entries.filter({ hasText: "Nutella, 30 g" });
  const dialog = page.getByRole("dialog", { name: "Scan a barcode" });
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

  await test.step("an unknown barcode keeps the dialog open and says so", async () => {
    await page.getByRole("button", { name: "Scan a barcode" }).click();
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole("button", { name: "Look up" })).toBeDisabled();
    await lookUp(UNKNOWN_BARCODE);
    await expect(dialog.getByRole("alert")).toHaveText(
      "Open Food Facts doesn't know that barcode. Log it by hand instead.",
    );
    await expect(dialog).toBeVisible();
  });

  await test.step("Open Food Facts not answering keeps the dialog open and says so", async () => {
    const fault = await page.request.put(`${FAKE}/_fake/fault`, { data: { status: 503, times: 1 } });
    expect(fault.ok(), "the fake fails the next read").toBe(true);
    await lookUp(MARMITE);
    await expect(dialog.getByRole("alert")).toHaveText("Open Food Facts didn't answer. Try again, or log it by hand.");
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
