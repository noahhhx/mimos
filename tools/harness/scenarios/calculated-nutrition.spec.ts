import { expect, test } from "./fixtures.ts";

/**
 * Write a recipe whose nutrition is calculated from the ingredient catalog
 * (ADR-0015): lines link themselves by name, the per-serving figures update
 * as the author types, a line in a unit its entry does not count says so,
 * and the saved recipe shows the same figures. The numbers follow from the
 * seeded catalog: 200 g dried pasta, 2 tbsp olive oil, and 2 garlic cloves
 * make 993.9 kcal, so 497 kcal a serving for 2.
 */
test("calculates a recipe's nutrition from its ingredients", async ({ loggedInPage: page }) => {
  const title = `Harness aglio ${new Date().toISOString()}`;
  const nutrition = page.locator(".per-serving");

  await test.step("open the form, calculating by default", async () => {
    await page.goto("/app/recipes/new");
    await expect(page.getByLabel("Calculate from ingredients")).toBeChecked();
    await expect(page.getByText("Nothing counts yet.")).toBeVisible();
  });

  await test.step("type the ingredients and watch them count", async () => {
    await page.getByLabel("Title").fill(title);
    await page.getByLabel("Description").fill("Written by the harness's calculated-nutrition scenario.");
    await page.getByLabel("Servings").fill("2");
    const lines: [string, string, string][] = [
      ["200", "g", "dried pasta"],
      ["2", "tbsp", "olive oil"],
      ["2", "", "garlic cloves, sliced"],
      ["1", "cup", "olive oil, for frying"],
    ];
    for (const [index, [amount, unit, name]] of lines.entries()) {
      if (index > 0) {
        await page.getByRole("button", { name: "+ Ingredient" }).click();
      }
      await page.getByLabel(`Ingredient ${index + 1} amount`).fill(amount);
      await page.getByLabel(`Ingredient ${index + 1} unit`).fill(unit);
      await page.getByLabel(`Ingredient ${index + 1} name`).fill(name);
    }
    await expect(page.getByLabel("Ingredient 2 counts as")).toHaveValue("olive-oil");
    await expect(page.getByLabel("Ingredient 3 counts as")).toHaveValue("garlic-clove");
    await expect(nutrition).toContainText("497 kcal");
    await expect(page.getByText("Counting 3 of 4 ingredients.")).toBeVisible();
    await expect(page.getByText("Not counted. Olive oil counts in ml, l, tsp or tbsp.")).toBeVisible();
  });

  await test.step("save and see the same figures on the recipe", async () => {
    await page.getByLabel("Step 1").fill("Cook the pasta; warm the garlic in the oil; toss.");
    await page.getByRole("button", { name: "Create recipe" }).click();
    const recipe = page.locator("article.recipe");
    await expect(recipe.getByRole("heading", { name: title, level: 1 })).toBeVisible();
    await expect(recipe.locator(".per-serving")).toContainText("497 kcal");
    await expect(recipe.getByText("Calculated from the ingredients.")).toBeVisible();
  });
});
