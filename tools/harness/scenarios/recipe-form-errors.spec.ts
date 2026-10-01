import { expect, test } from "./fixtures.ts";

/**
 * What the recipe form does with input it cannot save: the browser blocks
 * what one input can check (no request is sent), the form names the row at
 * fault for the rest, and Enter submits a valid form. Regression test for
 * the form's old behavior: fractional servings silently truncated, a missing
 * description or a nameless ingredient only reported by the API's
 * whole-recipe message.
 */
test("blocks invalid input and names the row at fault", async ({ loggedInPage: page }) => {
  const title = `Harness errors ${new Date().toISOString()}`;
  const formError = page.locator("main").getByRole("alert");
  const posts: string[] = [];
  page.on("request", (request) => {
    if (request.method() === "POST" && request.url().endsWith("/api/v1/recipes")) {
      posts.push(request.url());
    }
  });
  const isInvalid = (label: string) =>
    page
      .getByLabel(label, { exact: true })
      .evaluate((input) => !(input as unknown as { validity: { valid: boolean } }).validity.valid);

  await test.step("fill in a valid recipe", async () => {
    await page.goto("/app/recipes/new");
    await page.getByLabel("Title").fill(title);
    await page.getByLabel("Description").fill("Written by the harness's recipe-form-errors scenario.");
    await page.getByLabel("Ingredient 1 amount").fill("1");
    await page.getByLabel("Ingredient 1 name").fill("rice");
    await page.getByLabel("Step 1").fill("Boil the rice.");
  });

  await test.step("the browser blocks fractional servings and a blank description", async () => {
    await page.getByLabel("Servings").fill("2.5");
    await page.getByRole("button", { name: "Create recipe" }).click();
    expect(await isInvalid("Servings"), "2.5 servings is invalid").toBe(true);
    await page.getByLabel("Servings").fill("2");

    await page.getByLabel("Description").fill("");
    await page.getByRole("button", { name: "Create recipe" }).click();
    expect(await isInvalid("Description"), "a blank description is invalid").toBe(true);
    await page.getByLabel("Description").fill("Written by the harness's recipe-form-errors scenario.");

    expect(posts, "nothing reached the API").toEqual([]);
  });

  await test.step("the form names a nameless ingredient and a zero amount", async () => {
    await page.getByRole("button", { name: "+ Ingredient" }).click();
    await page.getByLabel("Ingredient 2 amount").fill("2");
    await page.getByLabel("Ingredient 2 unit").fill("cups");
    await page.getByRole("button", { name: "+ Ingredient" }).click();
    await page.getByLabel("Ingredient 3 amount").fill("0");
    await page.getByLabel("Ingredient 3 name").fill("salt");
    await page.getByRole("button", { name: "Create recipe" }).click();
    await expect(formError).toContainText("Ingredient 2 needs a name.");
    await expect(formError).toContainText("Ingredient 3's amount must be more than 0");
    expect(posts, "nothing reached the API").toEqual([]);
  });

  await test.step("Enter submits once the rows are fixed", async () => {
    await page.getByLabel("Ingredient 2 name").fill("water");
    await page.getByLabel("Ingredient 3 amount").fill("");
    await page.getByLabel("Title").press("Enter");
    await expect(page.getByRole("heading", { name: title, level: 1 })).toBeVisible();
    await expect(page).toHaveURL(/\/app\/recipes\/[0-9a-f-]{36}$/);
    expect(posts).toHaveLength(1);
  });
});
