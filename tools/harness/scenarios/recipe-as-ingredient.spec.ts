import { expect, test } from "./fixtures.ts";

/**
 * Use one of your recipes as an ingredient of another (ADR-0018): a
 * focaccia is picked from the ingredient search of a sandwich, measured in
 * servings, and the sandwich's calculated nutrition counts it. The numbers
 * follow from the seeded ingredients: 500 g all-purpose flour and 50 ml
 * olive oil make 278 kcal a serving for 8; two servings of that and two
 * 22 kcal tomatoes make 300 kcal a serving for 2.
 */
test("uses one of your recipes as an ingredient", async ({ loggedInPage: page }) => {
  // Unique per run: the search must find this run's focaccia, not an earlier one's.
  const stamp = Date.now().toString(36);
  const focaccia = `Harness focaccia ${stamp}`;
  const sandwich = `Harness sandwich ${stamp}`;
  const nutrition = page.locator(".per-serving");
  const row = (n: number) => page.locator(".ingredient-row").nth(n - 1);

  await test.step("write the focaccia", async () => {
    await page.goto("/app/recipes/new");
    await page.getByLabel("Title").fill(focaccia);
    await page.getByLabel("Description").fill("Written by the harness's recipe-as-ingredient scenario.");
    await page.getByLabel("Servings").fill("8");

    await page.getByLabel("Ingredient 1 amount").fill("500");
    await page.getByLabel("Ingredient 1 unit").selectOption("g");
    await page.getByLabel("Ingredient 1 name").fill("all-purpose");
    await page.getByRole("option", { name: /^All-purpose flour\s*364 kcal/ }).click();

    await page.getByRole("button", { name: "+ Ingredient" }).click();
    await page.getByLabel("Ingredient 2 amount").fill("50");
    await page.getByLabel("Ingredient 2 unit").selectOption("ml");
    await page.getByLabel("Ingredient 2 name").fill("olive oil");
    await page.getByRole("option", { name: /^Olive oil\s*813 kcal/ }).click();
    await expect(nutrition).toContainText("278 kcal");

    await page.getByLabel("Step 1").fill("Mix, prove, dimple, and bake.");
    await page.getByRole("button", { name: "Create recipe" }).click();
    await expect(page.getByRole("heading", { name: focaccia, level: 1 })).toBeVisible();
  });

  await test.step("pick the focaccia from a sandwich's ingredient search", async () => {
    await page.goto("/app/recipes/new");
    await page.getByLabel("Title").fill(sandwich);
    await page.getByLabel("Description").fill("Written by the harness's recipe-as-ingredient scenario.");
    await page.getByLabel("Servings").fill("2");

    await page.getByLabel("Ingredient 1 amount").fill("2");
    await page.getByLabel("Ingredient 1 name").fill(`focaccia ${stamp}`);
    const option = page.getByRole("option", { name: new RegExp(`^${focaccia}`) });
    await expect(option).toContainText("Your recipe · 278 kcal per serving");
    await option.click();
    await expect(page.getByLabel("Ingredient 1 name")).toHaveValue(focaccia.toLowerCase());
    await expect(page.getByLabel("Ingredient 1 unit")).toHaveValue("servings");
    await expect(row(1)).toContainText(`Uses your recipe ${focaccia} (makes 8 servings).`);

    await page.getByRole("button", { name: "+ Ingredient" }).click();
    await page.getByLabel("Ingredient 2 amount").fill("2");
    await page.getByLabel("Ingredient 2 name").fill("tomato");
    await page.getByRole("option", { name: /^Tomato\s*22 kcal each/ }).click();
    await expect(nutrition).toContainText("300 kcal");
  });

  await test.step("a recipe counts in servings only", async () => {
    await page.getByLabel("Ingredient 1 unit").selectOption("g");
    await expect(row(1)).toContainText("Not counted. A recipe counts in servings.");
    await page.getByLabel("Ingredient 1 unit").selectOption("servings");
    await expect(row(1)).not.toContainText("Not counted.");
    await expect(nutrition).toContainText("300 kcal");
    await expect(page.getByText("Counting 2 of 2 ingredients.")).toBeVisible();
  });

  await test.step("save, and follow the line to the recipe it uses", async () => {
    await page.getByLabel("Step 1").fill("Split the focaccia and fill it with the tomatoes.");
    await page.getByRole("button", { name: "Create recipe" }).click();
    const recipe = page.locator("article.recipe");
    await expect(recipe.getByRole("heading", { name: sandwich, level: 1 })).toBeVisible();
    await expect(recipe.locator(".per-serving")).toContainText("300 kcal");
    await expect(recipe).toContainText("2 servings");

    await recipe.getByRole("link", { name: focaccia.toLowerCase() }).click();
    await expect(page.locator("article.recipe").getByRole("heading", { name: focaccia, level: 1 })).toBeVisible();
  });
});
