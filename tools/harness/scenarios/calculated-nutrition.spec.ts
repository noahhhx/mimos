import { expect, test } from "./fixtures.ts";

/**
 * Write a recipe whose nutrition is calculated from its ingredients
 * (ADR-0015, ADR-0016): an ingredient is found by searching, a typed name
 * links itself when it names a known ingredient, one that is not there is
 * added as the user's own from inside the form, and the saved recipe shows
 * the same figures. The numbers follow from the seeded ingredients: 200 g
 * dried pasta, 2 tbsp olive oil, and 2 garlic cloves make 993.9 kcal; with
 * one 60 kcal fruit of the user's own, that is 527 kcal a serving for 2.
 */
test("calculates a recipe's nutrition from its ingredients", async ({ loggedInPage: page }) => {
  const title = `Harness aglio ${new Date().toISOString()}`;
  // Unique per run: the user's own ingredients outlive the recipe.
  const fruit = `harness fruit ${Date.now().toString(36)}`;
  const nutrition = page.locator(".per-serving");
  const row = (n: number) => page.locator(".ingredient-row").nth(n - 1);

  await test.step("open the form, calculating by default", async () => {
    await page.goto("/app/recipes/new");
    await expect(page.getByLabel("Calculate from ingredients")).toBeChecked();
    await expect(page.getByText("Nothing counts yet.")).toBeVisible();
    await page.getByLabel("Title").fill(title);
    await page.getByLabel("Description").fill("Written by the harness's calculated-nutrition scenario.");
    await page.getByLabel("Servings").fill("2");
  });

  await test.step("a typed name links itself; a search finds the rest", async () => {
    await page.getByLabel("Ingredient 1 amount").fill("200");
    await page.getByLabel("Ingredient 1 unit").fill("g");
    await page.getByLabel("Ingredient 1 name").fill("dried pasta");
    await page.getByLabel("Ingredient 1 note").fill("spaghetti");
    await expect(row(1)).toContainText("Matched to Dried pasta (371 kcal per 100 g).");

    await page.getByRole("button", { name: "+ Ingredient" }).click();
    await page.getByLabel("Ingredient 2 amount").fill("2");
    await page.getByLabel("Ingredient 2 unit").fill("tbsp");
    await page.getByLabel("Ingredient 2 name").fill("oli");
    await page.getByRole("option", { name: /^Olive oil/ }).click();
    await expect(page.getByLabel("Ingredient 2 name")).toHaveValue("olive oil");
    await expect(row(2)).toContainText("Matched to Olive oil");

    await page.getByRole("button", { name: "+ Ingredient" }).click();
    await page.getByLabel("Ingredient 3 amount").fill("2");
    await page.getByLabel("Ingredient 3 name").fill("garlic cloves");
    await page.getByLabel("Ingredient 3 note").fill("sliced");
    await expect(row(3)).toContainText("Matched to Garlic clove");

    await page.getByRole("button", { name: "+ Ingredient" }).click();
    await page.getByLabel("Ingredient 4 amount").fill("1");
    await page.getByLabel("Ingredient 4 unit").fill("cup");
    await page.getByLabel("Ingredient 4 name").fill("olive oil");
    await page.getByLabel("Ingredient 4 note").fill("for frying");
    await expect(page.getByText("Not counted. Olive oil counts in ml, l, tsp or tbsp.")).toBeVisible();
    await expect(nutrition).toContainText("497 kcal");
  });

  await test.step("add an ingredient that is not there", async () => {
    await page.getByRole("button", { name: "+ Ingredient" }).click();
    await page.getByLabel("Ingredient 5 amount").fill("1");
    await page.getByLabel("Ingredient 5 name").fill(fruit);
    await page.getByRole("option", { name: `+ Add “${fruit}” as a new ingredient` }).click();
    const panel = page.getByRole("group", { name: "New ingredient" });
    await expect(panel.getByLabel("Nutrition per")).toHaveValue("PER_PIECE");
    await panel.getByLabel("Calories").fill("60");
    await panel.getByLabel("Protein g").fill("1.2");
    await panel.getByLabel("Carbs g").fill("13");
    await panel.getByLabel("Fat g").fill("0.4");
    await panel.getByRole("button", { name: "Save ingredient" }).click();
    await expect(panel).toBeHidden();
    await expect(row(5)).toContainText("(60 kcal each)");
    await expect(nutrition).toContainText("527 kcal");
    await expect(page.getByText("Counting 4 of 5 ingredients.")).toBeVisible();
  });

  await test.step("save and see the same figures on the recipe", async () => {
    await page.getByLabel("Step 1").fill("Cook the pasta; warm the garlic in the oil; toss with the fruit.");
    await page.getByRole("button", { name: "Create recipe" }).click();
    const recipe = page.locator("article.recipe");
    await expect(recipe.getByRole("heading", { name: title, level: 1 })).toBeVisible();
    await expect(recipe.locator(".per-serving")).toContainText("527 kcal");
    await expect(recipe.getByText("Calculated from the ingredients.")).toBeVisible();
    await expect(recipe).toContainText("garlic cloves, sliced");
  });
});
