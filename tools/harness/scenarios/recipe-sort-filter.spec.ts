import { expect, test, apiAs } from "./fixtures.ts";

/**
 * The recipe book can be sorted and filtered. This scenario creates a
 * few recipes through the API, checks the default alphabetical order,
 * switches to newest first, and filters by a tag.
 */
test("sorts and filters the recipe book", async ({ loggedInPage: page }) => {
  const api = await apiAs(page);
  const ids: string[] = [];

  const recipes = [
    { title: "Sort Filter Apple", tags: ["sort-filter", "dinner"] },
    { title: "Sort Filter Banana", tags: ["sort-filter", "dessert"] },
    { title: "Sort Filter Carrot", tags: ["sort-filter", "dinner", "dessert"] },
  ];

  await test.step("create recipes", async () => {
    for (const recipe of recipes) {
      const created = await api<{ id: string }>("POST", "/api/v1/recipes", {
        title: recipe.title,
        description: "Created by the recipe-sort-filter harness scenario.",
        servings: 2,
        tags: recipe.tags,
        nutrition: {},
        nutritionSource: "MANUAL",
        ingredients: [{ name: "water", quantity: 100, unit: "g" }],
        steps: [{ instruction: "Mix." }],
      });
      ids.push(created.id);
    }
  });

  try {
    await test.step("open the recipe book and isolate the test recipes", async () => {
      await page.goto("/app/recipes");
      await expect(page.getByRole("heading", { name: "Recipes" })).toBeVisible();
      await page.getByRole("button", { name: "sort-filter" }).click();
      await expect(page.locator(".recipe-list h2")).toHaveText([
        "Sort Filter Apple",
        "Sort Filter Banana",
        "Sort Filter Carrot",
      ]);
    });

    await test.step("switch to newest first", async () => {
      await page.getByLabel("Sort by").selectOption("newest");
      await expect(page.locator(".recipe-list h2")).toHaveText([
        "Sort Filter Carrot",
        "Sort Filter Banana",
        "Sort Filter Apple",
      ]);
    });

    await test.step("filter by a second tag", async () => {
      await page.getByRole("button", { name: "dinner" }).click();
      await expect(page.locator(".recipe-list h2")).toHaveText(["Sort Filter Carrot", "Sort Filter Apple"]);
    });

    await test.step("clear the tag filter", async () => {
      await page.getByRole("button", { name: "Clear tags" }).click();
      const titles = page.locator(".recipe-list h2");
      await expect(titles.nth(0)).toHaveText("Sort Filter Carrot");
      await expect(titles.nth(1)).toHaveText("Sort Filter Banana");
      await expect(titles.nth(2)).toHaveText("Sort Filter Apple");
    });
  } finally {
    await test.step("delete created recipes", async () => {
      for (const id of ids) {
        await api("DELETE", `/api/v1/recipes/${id}`);
      }
    });
  }
});
