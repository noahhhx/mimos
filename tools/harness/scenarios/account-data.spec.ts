import { readFile } from "node:fs/promises";

import type { Page } from "@playwright/test";

import { expect, test } from "./fixtures.ts";

/**
 * Account export and import (ADR-0011) through the browser: one fresh
 * account writes a recipe and downloads its export, which the account
 * cannot import over its own data; a second fresh account imports the
 * file and finds the recipe. Both accounts are registered through
 * Keycloak's sign-up form, so the scenario never depends on what other
 * scenarios left in the shared test users.
 */
test("exports an account and imports it into a fresh one", async ({ page }, testInfo) => {
  const run = Date.now().toString(36);
  const title = `Harness export ${run}`;

  await register(page, `export-${run}`);

  await test.step("write a recipe", async () => {
    await page.getByRole("navigation", { name: "App" }).getByRole("link", { name: "Recipes" }).click();
    await page.getByRole("link", { name: "+ New recipe" }).click();
    await page.getByLabel("Title").fill(title);
    await page.getByLabel("Description").fill("Written by the harness's account-data scenario.");
    await page.getByLabel("Servings").fill("2");
    await page.getByLabel("Ingredient 1 amount").fill("1");
    await page.getByLabel("Ingredient 1 name").fill("loaf");
    await page.getByLabel("Step 1").fill("Slice it.");
    await page.getByRole("button", { name: "Create recipe" }).click();
    await expect(page.getByRole("heading", { name: title, level: 1 })).toBeVisible();
  });

  const file = testInfo.outputPath("export.json");
  await test.step("download the export", async () => {
    await openYourData(page);
    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "Download export" }).click();
    await (await download).saveAs(file);
    const exported = JSON.parse(await readFile(file, "utf8")) as { format: string; recipes: { title: string }[] };
    expect(exported.format).toBe("mimos.export");
    expect(exported.recipes.map((recipe) => recipe.title)).toEqual([title]);
  });

  await test.step("an account with data refuses the import", async () => {
    await page.getByLabel("Export file").setInputFiles(file);
    await page.getByRole("button", { name: "Import" }).click();
    await expect(page.locator("main").getByRole("alert")).toContainText("needs an empty account");
  });

  await test.step("sign out", async () => {
    await page.getByRole("banner").getByRole("button", { name: "Profile" }).click();
    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page).toHaveURL(/\/$/);
  });

  await register(page, `import-${run}`);

  await test.step("import into the fresh account", async () => {
    await openYourData(page);
    await page.getByLabel("Export file").setInputFiles(file);
    await page.getByRole("button", { name: "Import" }).click();
    await expect(page.getByRole("status")).toHaveText(
      "Imported 1 recipe, no planned meals, no shopping lists and no logged meals.",
    );
  });

  await test.step("the recipe is there", async () => {
    await page.getByRole("navigation", { name: "App" }).getByRole("link", { name: "Recipes" }).click();
    await expect(page.getByRole("link", { name: title })).toBeVisible();
  });
});

/** Signs up a new realm user through Keycloak's registration form and lands on the Kitchen. */
async function register(page: Page, username: string): Promise<void> {
  await test.step(`register ${username}`, async () => {
    await page.goto("/app");
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.getByRole("link", { name: "Register" }).click();
    await page.locator("#username").fill(username);
    await page.locator("#email").fill(`${username}@example.com`);
    await page.locator("#firstName").fill("Harness");
    await page.locator("#lastName").fill("Cook");
    await page.locator("#password").fill("mimos-test");
    await page.locator("#password-confirm").fill("mimos-test");
    await page.getByRole("button", { name: "Register" }).click();
    await expect(page).toHaveURL(/\/app$/);
    await expect(page.getByRole("heading", { name: "This week" })).toBeVisible();
  });
}

async function openYourData(page: Page): Promise<void> {
  await page.getByRole("banner").getByRole("button", { name: "Profile" }).click();
  await page.getByRole("link", { name: "Your data" }).click();
  await expect(page.getByRole("heading", { name: "Your data", level: 1 })).toBeVisible();
}
