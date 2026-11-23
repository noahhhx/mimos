import { apiAs, expect, test } from "./fixtures.ts";

/**
 * Rate a personal recipe from its page, see the rating persist across a
 * reload and in the recipe list, overwrite it, and clear it by clicking the
 * current star (ADR-0035). Library recipes show no stars to set.
 */
test("rates a recipe, keeps it across reloads, and clears it", async ({ loggedInPage: page }) => {
  const title = `Harness rating ${new Date().toISOString()}`;
  await page.goto("/app/recipes");
  const api = await apiAs(page);
  const created = await api<{ id: string }>("POST", "/api/v1/recipes", {
    title,
    description: "Rated by the harness's recipe-rating scenario.",
    servings: 2,
    tags: [],
    nutrition: {},
    nutritionSource: "MANUAL",
    ingredients: [{ quantity: 1, name: "beans" }],
    steps: [{ instruction: "Simmer the beans." }],
  });
  const star = (n: number) => page.getByRole("button", { name: `Rate ${n} of 5 stars` });

  await test.step("an unrated recipe has five unpressed stars", async () => {
    await page.goto(`/app/recipes/${created.id}`);
    for (let n = 1; n <= 5; n++) {
      await expect(star(n)).toHaveAttribute("aria-pressed", "false");
    }
  });

  await test.step("clicking a star rates it, and the rating survives a reload", async () => {
    await star(4).click();
    await expect(star(4)).toHaveAttribute("aria-pressed", "true");
    await page.reload();
    await expect(star(4)).toHaveAttribute("aria-pressed", "true");
    await expect(star(2)).toHaveAttribute("aria-pressed", "false");
  });

  await test.step("the recipe list shows the stars read-only", async () => {
    await page.goto("/app/recipes");
    await expect(
      page.getByRole("listitem").filter({ hasText: title }).getByRole("img", { name: "Rated 4 of 5 stars" }),
    ).toBeVisible();
  });

  await test.step("another star overwrites, and the current star clears", async () => {
    await page.goto(`/app/recipes/${created.id}`);
    await star(2).click();
    await expect(star(2)).toHaveAttribute("aria-pressed", "true");
    await expect(star(4)).toHaveAttribute("aria-pressed", "false");
    await star(2).click();
    await expect(star(2)).toHaveAttribute("aria-pressed", "false");
    await page.reload();
    await expect(star(2)).toHaveAttribute("aria-pressed", "false");
  });
});
