import { readFileSync } from "node:fs";

import { MARMITE, fixtures } from "../fakes/open-food-facts/src/fixtures.ts";
import { apiAs, browserWeek, expect, planLedger, register, test } from "./fixtures.ts";

/** The fake Open Food Facts (tools/harness/fakes/open-food-facts), published on loopback by compose.fakes.yml. */
const FAKE = `http://127.0.0.1:${process.env.OPEN_FOOD_FACTS_FAKE_PORT ?? "8091"}`;

const BANANA = (
  JSON.parse(
    readFileSync(
      new URL("../../../core/core-recipes/src/main/resources/library/ingredient-seed.json", import.meta.url),
      "utf8",
    ),
  ) as { slug: string; calories: number }[]
).find((entry) => entry.slug === "banana")!;

const MARMITE_KCAL_PER_100_G = (fixtures().get(MARMITE)!.nutriments as Record<string, number>)["energy-kcal_100g"]!;

type ShoppingList = { items: { name: string; quantity?: number }[] };

/**
 * Plan single foods without a recipe (ADR-0026): a banana picked from the
 * search, then Marmite scanned by its typed barcode against the fake Open
 * Food Facts. The camera is refused (this test never grants it), so the
 * scan dialog falls back to the typed field.
 */
test("plans a banana and a scanned product as foods, not recipes", async ({ page }) => {
  const stamp = Date.now().toString(36);
  expect((await page.request.post(`${FAKE}/_fake/reset`)).ok(), "the fake resets").toBe(true);

  await register(page, `plan-food-${stamp}`);
  const api = await apiAs(page);
  const { monday } = await browserWeek(page);
  const kcal = (pieces: number) => `${Math.round(BANANA.calories * pieces)} kcal`;
  const plan = planLedger(page);
  const planned = plan.planned(0);
  const addSnack = () =>
    plan.table
      .getByRole("button", { name: /^Add Snack for / })
      .first()
      .click();

  await test.step("plan a banana as Monday's snack: one piece, its calories from the seed", async () => {
    await page.goto("/app/plan");
    await addSnack();
    const picker = page.getByRole("dialog", { name: "Pick a meal" });
    await picker.getByRole("searchbox").fill("banana");
    await picker.getByRole("button", { name: /^Banana · Ingredient/ }).click();
    await expect(picker).toHaveCount(0);
    await expect(plan.meal("Banana")).toContainText("1 piece");
    const banana = await plan.openMeal("Banana");
    await expect(banana.getByLabel("Amount of Banana")).toHaveValue("1");
    await expect(planned).toHaveAccessibleName(new RegExp(`: ${kcal(1)}$`));
  });

  await test.step("the API reads it as a food, with no recipe", async () => {
    const week = await api<{ entries: { recipeId?: string; food?: { catalogSlug: string }; servings: number }[] }>(
      "GET",
      `/api/v1/plans/${monday}`,
    );
    expect(week.entries).toHaveLength(1);
    expect(week.entries[0]).toMatchObject({ food: { catalogSlug: "banana" }, servings: 1 });
    expect(week.entries[0]!.recipeId).toBeUndefined();
  });

  await test.step("two bananas double the total without a reload", async () => {
    const banana = await plan.openMeal("Banana");
    await banana.getByLabel("Amount of Banana").fill("2");
    await banana.getByLabel("Amount of Banana").press("Enter");
    await expect(planned).toHaveAccessibleName(new RegExp(`: ${kcal(2)}$`));
    await expect(plan.meal("Banana")).toContainText("2 pieces");
  });

  await test.step("the shopping list has the banana", async () => {
    await page.goto(`/app/shopping-list?week=${monday}`);
    await page.getByRole("button", { name: "Generate from this week's plan" }).click();
    await expect(page.locator(".shopping-items li").filter({ hasText: "Banana" })).toBeVisible();
    const list = await api<ShoppingList>("GET", `/api/v1/plans/${monday}/shopping-list`);
    expect(list.items.find((item) => item.name === "Banana")?.quantity).toBe(2);
  });

  await test.step("log the snack: the Log page lists it", async () => {
    await page.goto("/app/plan");
    const banana = await plan.openMeal("Banana");
    await banana.getByRole("button", { name: "Log", exact: true }).click();
    await expect(banana.getByRole("status")).toHaveText("Banana logged.");
    await page.goto("/app/log");
    await page.getByRole("row", { name: /^Mon/ }).click();
    await expect(page.locator(".log-entries li").filter({ hasText: "Banana, 2 pieces" })).toBeVisible();
  });

  await test.step("scan Marmite by its typed barcode: it lacks a fat value, so the form asks for it", async () => {
    await page.goto("/app/plan");
    await addSnack();
    const picker = page.getByRole("dialog", { name: "Pick a meal" });
    await picker.getByRole("button", { name: "Scan a barcode" }).click();
    const scan = page.getByRole("dialog", { name: "Scan a barcode" });
    await scan.getByLabel("Barcode number").fill(MARMITE);
    await scan.getByRole("button", { name: "Look up" }).click();
    await expect(scan).toBeHidden();
    const form = picker.getByRole("group", { name: "New ingredient" });
    await expect(form.getByLabel("Name")).toHaveValue("Yeast Extract (Marmite)");
    await form.getByLabel("Fat g").fill("0");
    await form.getByRole("button", { name: "Save ingredient" }).click();
    await expect(picker).toHaveCount(0);
  });

  await test.step("it is planned at its 8 g serving", async () => {
    await expect(plan.meal("Yeast Extract (Marmite)")).toContainText("8 g");
    const marmite = await plan.openMeal("Yeast Extract (Marmite)");
    await expect(marmite.getByLabel("Amount of Yeast Extract (Marmite)")).toHaveValue("8");
    await expect(planned).toHaveAccessibleName(
      new RegExp(`: ${Math.round(BANANA.calories * 2 + (MARMITE_KCAL_PER_100_G * 8) / 100)} kcal$`),
    );
  });

  await test.step("searching marmite in the picker finds the new ingredient", async () => {
    await addSnack();
    const picker = page.getByRole("dialog", { name: "Pick a meal" });
    await picker.getByRole("searchbox").fill("marmite");
    await expect(picker.getByRole("button", { name: /^Yeast Extract \(Marmite\)/ })).toContainText("Your ingredient");
  });
});
