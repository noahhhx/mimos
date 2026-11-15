import type { Locator } from "@playwright/test";

import { endpoints } from "../src/config.ts";
import { apiAs, browserWeek, expect, planLedger, register, test } from "./fixtures.ts";

type Nutrition = { calories?: number; proteinG?: number; carbsG?: number; fatG?: number };
type PlannedTotals = {
  startDate: string;
  days: { date: string; total: Nutrition; meals: number; mealsWithoutNutrition: number }[];
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
  const plan = planLedger(page);
  const plannedRow = (inspector: Locator) => inspector.getByRole("row", { name: /^Planned/ }).getByRole("cell");

  await test.step("an empty week has planned nothing yet on any day", async () => {
    const totalsLoaded = page.waitForResponse((response) => response.url().includes(`/me/fuel?from=${monday}&`));
    await page.goto("/app/plan");
    expect((await totalsLoaded).ok(), "the week's totals load").toBe(true);
    for (let day = 0; day < 7; day++) {
      await expect(plan.planned(day)).toHaveAccessibleName(/: nothing yet$/);
    }
  });

  await test.step(`plan ${title} for Monday's dinner: Planned reads twice its calories`, async () => {
    await plan.plan(0, "Dinner", title);
    await expect(plan.meal(title)).toContainText("2 servings");
    await expect(plan.planned(0)).toHaveAccessibleName(new RegExp(`: ${kcal(2)}$`));
    const monday = await plan.openDay(0);
    await expect(plannedRow(monday)).toHaveText([kcal(2).replace(" kcal", ""), /^\d+ g$/, /^\d+ g$/, /^\d+ g$/]);
  });

  await test.step("the API agrees: seven days, and one meal on Monday", async () => {
    const totals = await api<PlannedTotals>("GET", `/api/v1/plans/${monday}/my-totals`);
    expect(totals.days.map((day) => day.date)).toHaveLength(7);
    expect(totals.days[0]).toMatchObject({ date: monday, meals: 1, mealsWithoutNutrition: 0 });
    expect(totals.days.slice(1).every((day) => day.meals === 0)).toBe(true);
  });

  await test.step("one more serving updates the total without a reload", async () => {
    const dinner = await plan.openMeal(title);
    await dinner.getByRole("button", { name: "More servings" }).click();
    await expect(plan.planned(0)).toHaveAccessibleName(new RegExp(`: ${kcal(2.5)}$`));
  });

  await test.step("a recipe without calories adds its other values and is noted", async () => {
    const mondayTotal = async () =>
      (await api<PlannedTotals>("GET", `/api/v1/plans/${monday}/my-totals`)).days.find((day) => day.date === monday)!.total;
    const before = await mondayTotal();
    await createRecipe(api, mystery, { proteinG: 5, carbsG: 12, fatG: 3 });
    await plan.plan(0, "Lunch", mystery);
    await expect(plan.planned(0)).toHaveAccessibleName(new RegExp(`: ${kcal(2.5)}$`));
    const mondayDay = await plan.openDay(0);
    await expect(mondayDay).toContainText("Unknown nutrition in 1 meal is not counted.");
    const after = await mondayTotal();
    expect(after.proteinG).toBeCloseTo(before.proteinG! + 10, 5);
    expect(after.calories).toBe(before.calories);
  });

  await test.step("a day whose only meal lacks calories shows its other values and a dash for calories", async () => {
    await plan.plan(2, "Lunch", mystery);
    await expect(plan.planned(2)).toHaveAccessibleName(/: calories unknown$/);
    const wednesday = await plan.openDay(2);
    await expect(plannedRow(wednesday)).toHaveText(["–", "10 g", "24 g", "6 g"]);
    await expect(wednesday).toContainText("Unknown nutrition in 1 meal is not counted.");
    const totals = await api<PlannedTotals>("GET", `/api/v1/plans/${monday}/my-totals`);
    expect(totals.days[2]?.total, "calories are absent, not zero").toEqual({ proteinG: 10, carbsG: 24, fatG: 6 });
  });

  await test.step("a day whose meals have no nutrition at all shows dashes and the note", async () => {
    await createRecipe(api, unknown, {});
    await plan.plan(1, "Dinner", unknown);
    await expect(plan.planned(1)).toHaveAccessibleName(/: calories unknown$/);
    const tuesday = await plan.openDay(1);
    await expect(plannedRow(tuesday)).toHaveText(["–", "–", "–", "–"]);
    await expect(tuesday).toContainText("Unknown nutrition in 1 meal is not counted.");
    const totals = await api<PlannedTotals>("GET", `/api/v1/plans/${monday}/my-totals`);
    expect(totals.days[1]?.total, "no value is known").toEqual({});
  });

  await test.step("the day's fuel fits the sheet at every phone width", async () => {
    const mondayDay = await plan.openDay(0);
    const cells = mondayDay.locator(".day-fuel tbody td");
    await expect(cells).toHaveCount(4);
    for (let width = 320; width <= 640; width += 8) {
      await page.setViewportSize({ width, height: 900 });
      const fits = await mondayDay.evaluate((sheet) => sheet.scrollWidth <= sheet.clientWidth);
      expect(fits, `the sheet does not scroll sideways at ${width}px`).toBe(true);
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
  const plan = planLedger(page);

  await test.step("the host plans a dinner both eat: Mine shows the host's half", async () => {
    const { id } = await createRecipe(hostApi, stew, { calories: 400, proteinG: 20, carbsG: 40, fatG: 10 });
    await hostApi("POST", `/api/v1/plans/${monday}/entries`, { date: monday, mealType: "DINNER", recipeId: id, servings: 2 });
    await page.goto("/app/plan");
    await expect(page.getByRole("tab", { name: "Mine" })).toHaveAttribute("aria-selected", "true");
    await expect(plan.planned(0)).toHaveAccessibleName(/: 400 kcal$/);
    const mondayDay = await plan.openDay(0);
    await expect(mondayDay.getByRole("row", { name: /^Planned/ }).getByRole("cell")).toHaveText(["400", "20 g", "40 g", "10 g"]);
  });

  await test.step("Everyone shows the meal but no planned total", async () => {
    await page.getByRole("tab", { name: "Everyone" }).click();
    await expect(page.getByRole("tab", { name: "Everyone" })).toHaveAttribute("aria-selected", "true");
    await expect(plan.meal(stew)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Planned for / })).toHaveCount(0);
    await expect(plan.dayHead(0).getByRole("button"), "a day has nothing to open").toHaveCount(0);
  });

  await test.step("Mine shows it again", async () => {
    await page.getByRole("tab", { name: "Mine" }).click();
    await expect(plan.planned(0)).toHaveAccessibleName(/: 400 kcal$/);
  });
});

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
