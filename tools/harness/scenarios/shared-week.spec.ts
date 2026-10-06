import type { Page } from "@playwright/test";

import { endpoints } from "../src/config.ts";
import { apiAs, expect, register, test } from "./fixtures.ts";

/**
 * A week planned by a household of two (ADR-0019, "Plan entries have
 * diners"), each member in their own browser: the host plans a dinner
 * (both eat it by default) and a lunch for themselves, the guest plans a
 * lunch for themselves. Mine hides the other person's lunch and Everyone
 * shows it; the guest logs their share of the dinner. Fresh accounts keep
 * `test` and `test2` out of any household.
 */
test("a shared week: who eats what, Mine and Everyone, and logging your share", async ({ page, browser }, testInfo) => {
  test.slow();
  const stamp = Date.now().toString(36);
  const host = `cook-${stamp}`;
  const guest = `diner-${stamp}`;
  const stew = `Harness shared stew ${stamp}`;
  const hostLunch = `Harness host salad ${stamp}`;
  const guestLunch = `Harness guest wrap ${stamp}`;

  await register(page, host);
  const hostApi = await apiAs(page);
  await test.step("the host adds three recipes, the stew at 400 kcal a serving", async () => {
    for (const title of [stew, hostLunch, guestLunch]) {
      await hostApi("POST", "/api/v1/recipes", {
        title,
        description: "Written by the harness's shared-week scenario.",
        servings: 4,
        tags: [],
        nutrition: { calories: 400, proteinG: 20, carbsG: 40, fatG: 10 },
        nutritionSource: "MANUAL",
        ingredients: [{ quantity: 400, unit: "g", name: "Potatoes" }],
        steps: [{ instruction: "Cook it." }],
      });
    }
  });

  const guestContext = await browser.newContext({
    baseURL: endpoints().web,
    recordHar: { path: testInfo.outputPath("guest.har"), content: "embed" },
  });
  const guestPage = await guestContext.newPage();
  try {
    await register(guestPage, guest);
    await test.step("the guest joins the host's household", async () => {
      const { token } = await hostApi<{ token: string }>("POST", "/api/v1/household/invites");
      const guestApi = await apiAs(guestPage);
      await guestApi("POST", "/api/v1/household/join", { token });
    });

    const hostMeal = (title: string) => page.locator(".planned-meal").filter({ hasText: title });
    const guestMeal = (title: string) => guestPage.locator(".planned-meal").filter({ hasText: title });

    await test.step("the host plans Monday's dinner, which both eat and cooks a serving each", async () => {
      await page.goto("/app/plan");
      await expect(page.getByRole("tab", { name: "Mine" })).toHaveAttribute("aria-selected", "true");
      await planMonday(page, "Dinner", stew);
      const dinner = hostMeal(stew);
      await expect(dinner.getByText("2 servings")).toBeVisible();
      await expect(chip(dinner, `${host} (you)`)).toHaveAttribute("aria-pressed", "true");
      await expect(chip(dinner, guest)).toHaveAttribute("aria-pressed", "true");
      await dinner.getByRole("button", { name: "More servings" }).click();
      await expect(dinner.getByText("2.5 servings")).toBeVisible();
      await dinner.getByRole("button", { name: "More servings" }).click();
      await expect(dinner.getByText("3 servings")).toBeVisible();
    });

    await test.step("the host plans a lunch, which only the host eats", async () => {
      await planMonday(page, "Lunch", hostLunch);
      const lunch = hostMeal(hostLunch);
      await expect(lunch.getByText("1 serving", { exact: true })).toBeVisible();
      await expect(chip(lunch, `${host} (you)`)).toHaveAttribute("aria-pressed", "true");
      await expect(chip(lunch, `${host} (you)`)).toBeDisabled();
      await expect(chip(lunch, guest)).toHaveAttribute("aria-pressed", "false");
    });

    await test.step("the guest plans a lunch for themselves", async () => {
      await guestPage.goto("/app/plan");
      await planMonday(guestPage, "Lunch", guestLunch);
      await expect(chip(guestMeal(guestLunch), `${guest} (you)`)).toHaveAttribute("aria-pressed", "true");
      await expect(chip(guestMeal(guestLunch), host)).toHaveAttribute("aria-pressed", "false");
    });

    await test.step("Mine hides the guest's lunch from the host; Everyone shows it, without Log", async () => {
      await page.reload();
      await expect(hostMeal(stew)).toBeVisible();
      await expect(hostMeal(hostLunch)).toBeVisible();
      await expect(hostMeal(guestLunch)).toHaveCount(0);
      await page.getByRole("tab", { name: "Everyone" }).click();
      await expect(hostMeal(guestLunch)).toBeVisible();
      await expect(hostMeal(guestLunch).getByRole("button", { name: "Log" })).toHaveCount(0);
      await expect(hostMeal(stew).getByRole("button", { name: "Log" })).toBeVisible();
    });

    await test.step("the host's choice of Everyone is remembered", async () => {
      await page.reload();
      await expect(page.getByRole("tab", { name: "Everyone" })).toHaveAttribute("aria-selected", "true");
      await expect(hostMeal(guestLunch)).toBeVisible();
    });

    await test.step("the host adds the guest to the host's lunch, then takes them off again", async () => {
      const lunch = hostMeal(hostLunch);
      await chip(lunch, guest).click();
      await expect(chip(lunch, guest)).toHaveAttribute("aria-pressed", "true");
      await expect(chip(lunch, `${host} (you)`)).toBeEnabled();
      await chip(lunch, guest).click();
      await expect(chip(lunch, guest)).toHaveAttribute("aria-pressed", "false");
      await expect(lunch.getByText("1 serving", { exact: true })).toBeVisible();
    });

    await test.step("the guest logs the dinner: their share is half of the 3 servings", async () => {
      await guestPage.reload();
      const dinner = guestMeal(stew);
      await dinner.getByRole("button", { name: "Log" }).click();
      const share = dinner.getByRole("form", { name: `Log your share of ${stew}` });
      await expect(share.getByLabel("Your share")).toHaveValue("1.5");
      await expect(share).toContainText("of 3 servings for 2 people");
      await share.getByRole("button", { name: "Log share" }).click();
      await expect(guestPage.getByRole("status").filter({ hasText: `${stew} logged.` })).toBeVisible();
    });

    await test.step("the guest's log has 1.5 servings of the stew, 600 kcal", async () => {
      await guestPage.goto("/app/log");
      await guestPage.locator(".totals tbody tr").first().click();
      const logged = guestPage.locator(".log-entries li").filter({ hasText: stew });
      await expect(logged).toContainText("(1.5 servings)");
      await expect(logged).toContainText("600 kcal");
      await expect(guestPage.locator(".totals tbody tr").first()).toContainText("600 kcal");
    });
  } finally {
    await guestContext.close();
  }
});

/** Plans a recipe in Monday's slot through the picker. */
async function planMonday(page: Page, meal: string, title: string): Promise<void> {
  const monday = page.locator(".week .day").first();
  await monday.getByRole("button", { name: new RegExp(`^Add ${meal} for `) }).click();
  const picker = page.getByRole("dialog", { name: "Pick a recipe" });
  await picker.getByRole("searchbox").fill(title);
  await picker.getByRole("button", { name: new RegExp(`^${title}`) }).click();
  await expect(picker).toHaveCount(0);
  await expect(page.locator(".planned-meal").filter({ hasText: title })).toBeVisible();
}

function chip(meal: ReturnType<Page["locator"]>, name: string) {
  return meal.getByRole("group", { name: "Who eats it" }).getByRole("button", { name, exact: true });
}
