import { expect, test } from "./fixtures.ts";

/**
 * Create a recipe with a repeated tag, partial nutrition, and an unmeasured
 * ingredient, then cancel an edit and save one, landing back on its view.
 * Regression test for bugs found driving the form: a repeated tag was a 500
 * (duplicate `recipe_tag` row), the nutrition card hid carbs and fat unless
 * calories or protein were set, saving an edit left the page on the form,
 * and an ingredient could not go unmeasured (ADR-0007).
 */
test("creates a recipe, then edits it back to its view", async ({ loggedInPage: page }) => {
  // Unique per run, so reruns against the same database never collide.
  const title = `Harness stew ${new Date().toISOString()}`;
  const recipe = page.locator("article.recipe");
  const formError = page.locator("main").getByRole("alert");

  await test.step("create with a repeated tag, only carbs and fat, and salt to taste", async () => {
    await page.goto("/app/recipes/new");
    await page.getByLabel("Title").fill(title);
    await page.getByLabel("Description").fill("Written by the harness's edit-recipe scenario.");
    await page.getByLabel("Tags (comma-separated)").fill("dinner, Dinner, harness");
    await page.getByLabel("Carbs g").fill("40");
    await page.getByLabel("Fat g").fill("10");
    await page.getByLabel("Ingredient 1 amount").fill("1");
    await page.getByLabel("Ingredient 1 name").fill("beans");
    await page.getByRole("button", { name: "+ Ingredient" }).click();
    await page.getByLabel("Ingredient 2 name").fill("salt, to taste");
    await page.getByLabel("Step 1").fill("Simmer the beans.");
    await page.getByRole("button", { name: "Create recipe" }).click();
    await expect(formError.or(recipe.getByRole("heading", { name: title, level: 1 }))).toBeVisible();
    expect(await formError.allInnerTexts(), "the form reported an error").toEqual([]);
  });

  await test.step("the view shows each tag once and the nutrition card", async () => {
    await expect(recipe.locator(".tag")).toHaveText(["dinner", "harness"]);
    await expect(recipe.getByRole("heading", { name: "Per serving" })).toBeVisible();
    await expect(recipe.locator(".nutrition")).toContainText("40 g");
  });

  await test.step("the unmeasured ingredient shows by name alone", async () => {
    const salt = recipe.locator(".ingredients li").nth(1);
    await expect(salt).toHaveText("salt, to taste");
    await expect(salt.locator(".quantity")).toHaveCount(0);
  });

  await test.step("cancel an edit and keep the recipe as it was", async () => {
    await page.getByRole("button", { name: "Edit" }).click();
    await expect(page.getByLabel("Ingredient 2 amount")).toHaveValue("");
    await page.getByLabel("Title").fill("Never saved");
    await page.getByRole("button", { name: "Cancel" }).click();
    await expect(recipe.getByRole("heading", { name: title, level: 1 })).toBeVisible();
  });

  await test.step("save an edit and land back on the view", async () => {
    await page.getByRole("button", { name: "Edit" }).click();
    await page.getByLabel("Title").fill(`${title} (edited)`);
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(formError.or(recipe.getByRole("heading", { name: `${title} (edited)`, level: 1 }))).toBeVisible();
    expect(await formError.allInnerTexts(), "the form reported an error").toEqual([]);
    await expect(page.getByRole("heading", { name: "Edit recipe" })).toHaveCount(0);
  });

  await test.step("the edit survives a reload", async () => {
    await page.reload();
    await expect(recipe.getByRole("heading", { name: `${title} (edited)`, level: 1 })).toBeVisible();
  });
});
