import type { Locator, Page } from "@playwright/test";

import { endpoints } from "../src/config.ts";
import { apiAs, browserWeek, expect, register, test } from "./fixtures.ts";

type Nutrition = { calories?: number; proteinG?: number; carbsG?: number; fatG?: number };
type PlannedTotals = {
  startDate: string;
  days: { date: string; total: Required<Nutrition>; meals: number; mealsWithoutNutrition: number }[];
};

test("the plan shows each day's planned total", async ({ page }) => {
  const stamp = Date.now().toString(36);
  const mystery = `Harness mystery soup ${stamp}`;
  const unknown = `Harness unknown stew ${stamp}`;

  await register(page, `totals-${stamp}`);
  const api = await apiAs(page);
  const { monday } = await browserWeek(page);
  const library = await api<{ title: string; nutrition?: Nutrition }[]>("GET", "/api/v1/recipes/library");
  const dinner = library.find((recipe) => recipe.nutrition?.calories != null && /^[\w '-]+$/.test(recipe.title));
  expect(dinner, "the library has a recipe with calories").toBeDefined();
  const { title, nutrition } = dinner!;
  const kcal = (servings: number) => `${Math.round(Math.round(nutrition!.calories! * servings * 10) / 10)} kcal`;
  const mondayCard = day(page, 0);
  const tuesdayCard = day(page, 1);
  const planned = mondayCard.getByRole("group", { name: /^Planned for / });

  await test.step("an empty week shows no planned totals", async () => {
    const totalsLoaded = page.waitForResponse((response) => response.url().endsWith(`/plans/${monday}/my-totals`));
    await page.goto("/app/plan");
    expect((await totalsLoaded).ok(), "the week's totals load").toBe(true);
    await expect(mondayCard.getByRole("heading")).toBeVisible();
    await expect(page.getByRole("group", { name: /^Planned for / })).toHaveCount(0);
  });

  await test.step(`plan ${title} for Monday's dinner: Planned reads twice its calories`, async () => {
    await planOn(mondayCard, "Dinner", title);
    await expect(mondayCard.locator(".planned-meal").filter({ hasText: title }).getByText("2 servings")).toBeVisible();
    await expect(planned).toContainText("Planned");
    await expect(planned).toContainText(kcal(2));
    await expect(planned).toContainText(/\d+ g protein · \d+ g carbs · \d+ g fat/);
  });

  await test.step("the API agrees: seven days, and one meal on Monday", async () => {
    const totals = await api<PlannedTotals>("GET", `/api/v1/plans/${monday}/my-totals`);
    expect(totals.days.map((day) => day.date)).toHaveLength(7);
    expect(totals.days[0]).toMatchObject({ date: monday, meals: 1, mealsWithoutNutrition: 0 });
    expect(totals.days.slice(1).every((day) => day.meals === 0)).toBe(true);
  });

  await test.step("one more serving updates the total without a reload", async () => {
    await mondayCard.locator(".planned-meal").filter({ hasText: title }).getByRole("button", { name: "More servings" }).click();
    await expect(planned).toContainText(kcal(2.5));
  });

  await test.step("a recipe without calories adds its other values and is noted", async () => {
    const mondayTotal = async () =>
      (await api<PlannedTotals>("GET", `/api/v1/plans/${monday}/my-totals`)).days.find((day) => day.date === monday)!.total;
    const before = await mondayTotal();
    await createRecipe(api, mystery, { proteinG: 5, carbsG: 12, fatG: 3 });
    await planOn(mondayCard, "Lunch", mystery);
    await expect(planned).toContainText("Unknown nutrition in 1 meal is not counted.");
    await expect(planned).toContainText(kcal(2.5));
    const after = await mondayTotal();
    expect(after.proteinG).toBeCloseTo(before.proteinG + 10, 5);
    expect(after.calories).toBe(before.calories);
  });

  await test.step("a day whose meals have no nutrition at all shows only the note", async () => {
    await createRecipe(api, unknown, {});
    await planOn(tuesdayCard, "Dinner", unknown);
    const tuesday = tuesdayCard.getByRole("group", { name: /^Planned for / });
    await expect(tuesday).toContainText("Unknown nutrition in 1 meal is not counted.");
    await expect(tuesday).not.toContainText("kcal");
  });

  await test.step("each value stays with its unit and label at every phone width", async () => {
    for (let width = 320; width <= 640; width += 8) {
      await page.setViewportSize({ width, height: 900 });
      const broken = await planned.locator(".planned-total span").evaluateAll((parts) =>
        parts
          .filter((part) => new Set([...part.getClientRects()].map((rect) => Math.round(rect.top))).size > 1)
          .map((part) => part.textContent),
      );
      expect(broken, `no value splits from its label at ${width}px`).toEqual([]);
    }
  });
});

test("Everyone hides the planned total in a shared household", async ({ page, browser }) => {
  const stamp = Date.now().toString(36);
  const stew = `Harness totals stew ${stamp}`;

  await register(page, `totals-host-${stamp}`);
  const hostApi = await apiAs(page);
  const guestContext = await browser.newContext({ baseURL: endpoints().web });
  try {
    const guestPage = await guestContext.newPage();
    await register(guestPage, `totals-guest-${stamp}`);
    await test.step("the guest joins the host's household", async () => {
      const { token } = await hostApi<{ token: string }>("POST", "/api/v1/household/invites");
      const guestApi = await apiAs(guestPage);
      await guestApi("POST", "/api/v1/household/join", { token });
    });
  } finally {
    await guestContext.close();
  }

  const { monday } = await browserWeek(page);
  const mondayCard = day(page, 0);
  const planned = mondayCard.getByRole("group", { name: /^Planned for / });

  await test.step("the host plans a dinner both eat: Mine shows the host's half", async () => {
    const { id } = await createRecipe(hostApi, stew, { calories: 400, proteinG: 20, carbsG: 40, fatG: 10 });
    await hostApi("POST", `/api/v1/plans/${monday}/entries`, { date: monday, mealType: "DINNER", recipeId: id, servings: 2 });
    await page.goto("/app/plan");
    await expect(page.getByRole("tab", { name: "Mine" })).toHaveAttribute("aria-selected", "true");
    await expect(planned).toContainText("400 kcal · 20 g protein · 40 g carbs · 10 g fat");
  });

  await test.step("Everyone shows the meal but no planned total", async () => {
    await page.getByRole("tab", { name: "Everyone" }).click();
    await expect(page.getByRole("tab", { name: "Everyone" })).toHaveAttribute("aria-selected", "true");
    await expect(mondayCard.locator(".planned-meal").filter({ hasText: stew })).toBeVisible();
    await expect(page.getByRole("group", { name: /^Planned for / })).toHaveCount(0);
  });

  await test.step("Mine shows it again", async () => {
    await page.getByRole("tab", { name: "Mine" }).click();
    await expect(planned).toContainText("400 kcal");
  });
});

function day(page: Page, index: number): Locator {
  return page.locator(".week .day").nth(index);
}

async function createRecipe(
  api: Awaited<ReturnType<typeof apiAs>>,
  title: string,
  nutrition: Nutrition,
): Promise<{ id: string }> {
  return api<{ id: string }>("POST", "/api/v1/recipes", {
    title,
    description: "Written by the harness's planned-totals scenario.",
    servings: 2,
    tags: [],
    nutrition,
    nutritionSource: "MANUAL",
    ingredients: [{ quantity: 1, unit: "l", name: "Stock" }],
    steps: [{ instruction: "Heat it." }],
  });
}

async function planOn(dayCard: Locator, meal: string, title: string): Promise<void> {
  const page = dayCard.page();
  await dayCard.getByRole("button", { name: new RegExp(`^Add ${meal} for `) }).click();
  const picker = page.getByRole("dialog", { name: "Pick a recipe" });
  await picker.getByRole("searchbox").fill(title);
  await picker.getByRole("button", { name: new RegExp(`^${title}`) }).click();
  await expect(picker).toHaveCount(0);
  await expect(dayCard.locator(".planned-meal").filter({ hasText: title })).toBeVisible();
}
