import { fileURLToPath } from "node:url";

import { apiAs, expect, register, test } from "./fixtures.ts";

const COVER = fileURLToPath(new URL("../../../apps/mobile/flows/assets/recipe-picture.jpg", import.meta.url));

const naturalWidth = (img: unknown) => (img as { naturalWidth: number }).naturalWidth;

test("makes a tab, lands on it by default, and deletes it", async ({ page }) => {
  await register(page, `tabs-${Date.now().toString(36)}`);
  const tabs = page.getByRole("tablist");
  const name = "Soups and shakshuka";
  const gallery = page.getByRole("list", { name: "Tabs" });
  const formError = page.locator("main").getByRole("alert");

  await page.goto("/app/recipes");
  const api = await apiAs(page);
  const own = "Harness tomato soup";
  await api("POST", "/api/v1/recipes", {
    title: own,
    description: "Gathered into a tab by its tag.",
    servings: 2,
    tags: ["Soup"],
    nutrition: {},
    nutritionSource: "MANUAL",
    ingredients: [{ quantity: 4, name: "tomatoes" }],
    steps: [{ instruction: "Simmer and blend." }],
  });

  await test.step("without tabs, Recipes opens on My recipes with the two tabs it always had", async () => {
    await page.reload();
    await expect(tabs.getByRole("tab")).toHaveText([/^My recipes \(1\)$/, /^Library \(\d+\)$/]);
    await expect(tabs.getByRole("tab", { name: /^My recipes/ })).toHaveAttribute("aria-selected", "true");
    await expect(page.getByRole("link", { name: own })).toBeVisible();
  });

  await test.step("make a tab with a pick, a tag, and a cover", async () => {
    await page.getByRole("link", { name: "+ New tab" }).click();
    await expect(page.getByRole("heading", { name: "New tab", level: 1 })).toBeVisible();
    await page.getByLabel("Name").fill(name);
    await page.getByLabel("Choose picture").setInputFiles(COVER);
    await expect(page.getByText("Not saved yet")).toBeVisible();
    await page.getByLabel("New tag").fill(" Soup ");
    await page.getByRole("button", { name: "Add tag" }).click();
    await expect(page.getByRole("group", { name: "Tags" }).getByRole("button", { name: "soup" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    await page.getByLabel("Search recipes to pick").fill("shakshuka");
    await page.getByRole("checkbox", { name: "Shakshuka" }).check();
    await expect(page.getByText("1 picked.")).toBeVisible();
    await page.getByRole("button", { name: "Create tab" }).click();
    await expect(formError.or(tabs.getByRole("tab", { name, selected: true }))).toBeVisible();
    expect(await formError.allInnerTexts(), "the form reported an error").toEqual([]);
  });

  await test.step("the tab lists the pick and both recipes with its tag", async () => {
    for (const title of ["Shakshuka", "Minestrone", own]) {
      await expect(page.getByRole("link", { name: title, exact: true })).toBeVisible();
    }
    await expect(page.locator(".recipe-list > li")).toHaveCount(3);
    await expect(page.getByRole("button", { name: "Open by default" })).toHaveAttribute("aria-pressed", "false");
  });

  await test.step("Recipes now opens on the gallery, with the tab's cover", async () => {
    await page.goto("/app/recipes");
    await expect(tabs.getByRole("tab", { name: "Tabs" })).toHaveAttribute("aria-selected", "true");
    const tile = gallery.getByRole("link", { name: new RegExp(`^${name}`) });
    await expect(tile).toContainText("3 recipes");
    await expect.poll(() => tile.locator("img").evaluate(naturalWidth)).toBeGreaterThan(0);
    await expect(gallery.getByRole("link", { name: /New tab/ })).toBeVisible();
    await tile.click();
    await expect(tabs.getByRole("tab", { name, selected: true })).toBeVisible();
  });

  await test.step("made the default, the tab is where Recipes opens", async () => {
    await page.getByRole("button", { name: "Open by default" }).click();
    await expect(page.getByRole("button", { name: "Open by default" })).toHaveAttribute("aria-pressed", "true");
    await page.goto("/app/recipes");
    await expect(tabs.getByRole("tab", { name, selected: true })).toBeVisible();
    await page.getByRole("tab", { name: "Tabs" }).click();
    await expect(gallery.getByRole("link", { name: new RegExp(`^${name}`) })).toContainText("Default");
  });

  await test.step("deleting the tab brings back My recipes", async () => {
    await page.goto("/app/recipes");
    await page.getByRole("link", { name: "Edit tab" }).click();
    await expect(page.getByLabel("Name")).toHaveValue(name);
    await expect(page.getByLabel("Open Recipes on this tab")).toBeChecked();
    page.once("dialog", (dialog) => void dialog.accept());
    await page.getByRole("button", { name: "Delete tab" }).click();
    await expect(tabs.getByRole("tab", { name: /^My recipes/ })).toHaveAttribute("aria-selected", "true");
    await expect(tabs.getByRole("tab")).toHaveCount(2);
  });
});
