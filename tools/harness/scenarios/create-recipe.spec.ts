import { expect, test } from "./fixtures.ts";

/**
 * Write a personal recipe through the form and land on its page. Fails with
 * the recipe 415 until harness step 7 fixes it (docs/harness/step-7-recipe-415.md).
 */
test("creates a personal recipe from the form", async ({ loggedInPage: page }) => {
  // Unique per run, so reruns against the same database never collide.
  const title = `Harness soup ${new Date().toISOString()}`;

  await test.step("open the new-recipe form", async () => {
    await page.getByRole("navigation", { name: "App" }).getByRole("link", { name: "Recipes" }).click();
    await page.getByRole("link", { name: "+ New recipe" }).click();
    await expect(page.getByRole("heading", { name: "New recipe" })).toBeVisible();
  });

  await test.step("fill in the recipe", async () => {
    await page.getByLabel("Title").fill(title);
    await page.getByLabel("Description").fill("Written by the harness's create-recipe scenario.");
    await page.getByLabel("Servings").fill("2");
    await page.getByLabel("Prep minutes").fill("10");
    await page.getByLabel("Cook minutes").fill("20");
    await page.getByLabel("Tags (comma-separated)").fill("dinner, harness");
    await page.getByLabel("Ingredient 1 amount").fill("500");
    await page.getByLabel("Ingredient 1 unit").fill("g");
    await page.getByLabel("Ingredient 1 name").fill("tomatoes");
    await page.getByLabel("Step 1").fill("Simmer the tomatoes.");
  });

  await test.step("submit and land on the recipe", async () => {
    await page.getByRole("button", { name: "Create recipe" }).click();
    const formError = page.locator("main").getByRole("alert");
    const recipeHeading = page.getByRole("heading", { name: title, level: 1 });
    await expect(formError.or(recipeHeading)).toBeVisible();
    expect(await formError.allInnerTexts(), "the form reported an error").toEqual([]);
    await expect(page).toHaveURL(/\/app\/recipes\/[0-9a-f-]{36}$/);
  });
});
