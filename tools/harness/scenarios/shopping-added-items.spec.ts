import { apiAs, browserWeek, expect, register, test } from "./fixtures.ts";

type ShoppingList = { items: { name: string; added: boolean; checked: boolean }[] };

/**
 * Add things to the shopping list that no planned meal needs (ADR-0027):
 * adding to a week with no list builds it from the plan first, a
 * regeneration keeps the added item and its tick, and only added items
 * can be removed.
 */
test("adds an unplanned item that survives regeneration, then removes it", async ({ page }) => {
  await register(page, `shopping-added-${Date.now().toString(36)}`);
  const api = await apiAs(page);
  const { monday } = await browserWeek(page);
  await api("POST", `/api/v1/plans/${monday}/entries`, {
    date: monday,
    mealType: "SNACK",
    food: { catalogSlug: "banana" },
    servings: 2,
  });

  await page.goto(`/app/shopping-list?week=${monday}`);
  await expect(page.getByRole("button", { name: "Generate from this week's plan" })).toBeVisible();
  const binBags = page.locator(".shopping-items li").filter({ hasText: "bin bags" });

  await test.step("add bin bags to a week with no list: the plan's banana comes too", async () => {
    await page.getByLabel("Item").fill("bin bags");
    await page.getByLabel("Amount").fill("2");
    await expect(page.getByLabel("Unit")).toHaveValue("");
    await page.getByRole("button", { name: "Add", exact: true }).click();
    await expect(binBags).toContainText("2 bin bags");
    await page.getByLabel("Item").fill("milk");
    await page.getByLabel("Amount").fill("2");
    await page.getByLabel("Unit").selectOption("l");
    await page.getByRole("button", { name: "Add", exact: true }).click();
    await expect(page.locator(".shopping-items li").filter({ hasText: "milk" })).toContainText("2 l milk");
    await expect(binBags).toContainText("Added");
    await expect(page.locator(".shopping-items li").filter({ hasText: "Banana" })).toBeVisible();
    await expect(page.getByLabel("Item")).toHaveValue("");
  });

  await test.step("tick it off and regenerate: it stays, ticked", async () => {
    await binBags.getByRole("checkbox").check();
    await page.getByRole("button", { name: "Regenerate" }).click();
    await expect(binBags.getByRole("checkbox")).toBeChecked();
    const list = await api<ShoppingList>("GET", `/api/v1/plans/${monday}/shopping-list`);
    expect(list.items.find((item) => item.name === "bin bags")).toMatchObject({ added: true, checked: true });
  });

  await test.step("only the added item can be removed", async () => {
    await expect(
      page.locator(".shopping-items li").filter({ hasText: "Banana" }).getByRole("button", { name: /^Remove/ }),
    ).toHaveCount(0);
    // The page drops the item at once; wait for the API to have removed it too.
    const removed = page.waitForResponse(
      (response) => response.request().method() === "DELETE" && response.url().includes("/shopping-list/items/"),
    );
    await binBags.getByRole("button", { name: "Remove bin bags" }).click();
    expect((await removed).status()).toBe(204);
    await expect(binBags).toHaveCount(0);
    const list = await api<ShoppingList>("GET", `/api/v1/plans/${monday}/shopping-list`);
    expect(list.items.map((item) => item.name).sort()).toEqual(["Banana", "milk"]);
  });
});
