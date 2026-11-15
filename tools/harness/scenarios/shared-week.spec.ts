import type { Locator } from "@playwright/test";

import { endpoints } from "../src/config.ts";
import { apiAs, browserWeek, expect, planLedger, register, test } from "./fixtures.ts";

/**
 * A week planned by a household of two (ADR-0019, "Plan entries have
 * diners"), each member in their own browser: the host plans a dinner
 * (both eat it by default) and a lunch for themselves, the guest plans a
 * lunch for themselves. Mine hides the other person's lunch and Everyone
 * shows it; the guest logs their share of the dinner. Then the household
 * shares one Country of the Week and one shopping list (ROADMAP step 17's
 * "done when"). Fresh accounts keep `test` and `test2` out of any
 * household.
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

    const hostPlan = planLedger(page);
    const guestPlan = planLedger(guestPage);

    await test.step("the host plans Monday's dinner, which both eat and cooks a serving each", async () => {
      await page.goto("/app/plan");
      await expect(page.getByRole("tab", { name: "Mine" })).toHaveAttribute("aria-selected", "true");
      await hostPlan.plan(0, "Dinner", stew);
      await expect(hostPlan.meal(stew)).toContainText("2 servings");
      const dinner = await hostPlan.openMeal(stew);
      await expect(chip(dinner, `${host} (you)`)).toHaveAttribute("aria-pressed", "true");
      await expect(chip(dinner, guest)).toHaveAttribute("aria-pressed", "true");
      await dinner.getByRole("button", { name: "More servings" }).click();
      await expect(dinner.getByText("2.5 servings")).toBeVisible();
      await dinner.getByRole("button", { name: "More servings" }).click();
      await expect(dinner.getByText("3 servings")).toBeVisible();
      await expect(hostPlan.meal(stew), "the cell follows").toContainText("3 servings");
    });

    await test.step("the host plans a lunch, which only the host eats", async () => {
      await hostPlan.plan(0, "Lunch", hostLunch);
      await expect(hostPlan.meal(hostLunch)).toContainText("1 serving");
      const lunch = await hostPlan.openMeal(hostLunch);
      await expect(lunch.getByText("1 serving", { exact: true })).toBeVisible();
      await expect(chip(lunch, `${host} (you)`)).toHaveAttribute("aria-pressed", "true");
      await expect(chip(lunch, `${host} (you)`)).toBeDisabled();
      await expect(chip(lunch, guest)).toHaveAttribute("aria-pressed", "false");
    });

    await test.step("the guest plans a lunch for themselves", async () => {
      await guestPage.goto("/app/plan");
      await guestPlan.plan(0, "Lunch", guestLunch);
      const lunch = await guestPlan.openMeal(guestLunch);
      await expect(chip(lunch, `${guest} (you)`)).toHaveAttribute("aria-pressed", "true");
      await expect(chip(lunch, host)).toHaveAttribute("aria-pressed", "false");
    });

    await test.step("Mine hides the guest's lunch from the host; Everyone shows it, without Log", async () => {
      await page.reload();
      await expect(hostPlan.meal(stew)).toBeVisible();
      await expect(hostPlan.meal(hostLunch)).toBeVisible();
      await expect(hostPlan.meal(guestLunch)).toHaveCount(0);
      await page.getByRole("tab", { name: "Everyone" }).click();
      await expect(hostPlan.meal(guestLunch)).toBeVisible();
      const theirs = await hostPlan.openMeal(guestLunch);
      await expect(theirs.getByRole("button", { name: "Log" })).toHaveCount(0);
      const ours = await hostPlan.openMeal(stew);
      await expect(ours.getByRole("button", { name: "Log" })).toBeVisible();
    });

    await test.step("the host's choice of Everyone is remembered", async () => {
      await page.reload();
      await expect(page.getByRole("tab", { name: "Everyone" })).toHaveAttribute("aria-selected", "true");
      await expect(hostPlan.meal(guestLunch)).toBeVisible();
    });

    await test.step("the host adds the guest to the host's lunch, then takes them off again", async () => {
      const lunch = await hostPlan.openMeal(hostLunch);
      await chip(lunch, guest).click();
      await expect(chip(lunch, guest)).toHaveAttribute("aria-pressed", "true");
      await expect(chip(lunch, `${host} (you)`)).toBeEnabled();
      await chip(lunch, guest).click();
      await expect(chip(lunch, guest)).toHaveAttribute("aria-pressed", "false");
      await expect(lunch.getByText("1 serving", { exact: true })).toBeVisible();
    });

    await test.step("the guest logs the dinner: their share is half of the 3 servings", async () => {
      await guestPage.reload();
      const dinner = await guestPlan.openMeal(stew);
      await dinner.getByRole("button", { name: "Log" }).click();
      const share = dinner.getByRole("form", { name: `Log your share of ${stew}` });
      await expect(share.getByLabel("Your share")).toHaveValue("1.5");
      await expect(share).toContainText("of 3 servings for 2 people");
      await share.getByRole("button", { name: "Log share" }).click();
      await expect(dinner.getByRole("status")).toHaveText(`${stew} logged.`);
    });

    await test.step("the guest's log has 1.5 servings of the stew, 600 kcal", async () => {
      await guestPage.goto("/app/log");
      await guestPage.locator(".totals tbody tr").first().click();
      const logged = guestPage.locator(".log-entries li").filter({ hasText: stew });
      await expect(logged).toContainText("(1.5 servings)");
      await expect(logged).toContainText("600 kcal");
      await expect(guestPage.locator(".totals tbody tr").first()).toContainText("600 kcal");
    });

    const { monday } = await browserWeek(page);
    const guestApi = await apiAs(guestPage);

    await test.step("the host turns Country of the Week on and chooses Italy; the guest's week is Italy too", async () => {
      await hostApi("PUT", "/api/v1/me/plugins/country-week", { enabled: true });
      await hostApi("POST", `/api/v1/plans/${monday}/panels/country-week/actions`, { id: "choose", value: "IT" });
      await guestPage.goto("/app/plan");
      await expect(guestPage.getByRole("button", { name: /^Country of the Week/ })).toContainText("Italy", {
        timeout: 10_000,
      });
    });

    await test.step("the host makes the week's shopping list; the guest ticks off the potatoes on it, for both", async () => {
      await hostApi("POST", `/api/v1/plans/${monday}/shopping-list`);
      await guestPage.goto("/app/shopping-list");
      const potatoes = guestPage.getByRole("checkbox", { name: /Potatoes/i });
      await potatoes.check();
      await expect(potatoes).toBeChecked();
      type List = { items: { name: string; checked: boolean }[] };
      const checked = async (api: typeof hostApi) =>
        (await api<List>("GET", `/api/v1/plans/${monday}/shopping-list`)).items.find((item) => /potatoes/i.test(item.name))
          ?.checked;
      await expect.poll(() => checked(hostApi)).toBe(true);
      expect(await checked(guestApi)).toBe(true);
    });
  } finally {
    await guestContext.close();
  }
});

function chip(meal: Locator, name: string) {
  return meal.getByRole("group", { name: "Who eats it" }).getByRole("button", { name, exact: true });
}
