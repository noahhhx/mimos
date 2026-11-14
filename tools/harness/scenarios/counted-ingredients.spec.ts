import { apiAs, expect, test } from "./fixtures.ts";

/**
 * A count of pieces reads in the plural unless it is one: the catalog names
 * its per-piece ingredients in the singular ("Egg"), and a recipe shows
 * "2 Eggs" but "1 Lemon".
 */
test("counts pieces in the plural", async ({ loggedInPage: page }) => {
  const api = await apiAs(page);
  const recipe = await api<{ id: string }>("POST", "/api/v1/recipes", {
    title: `Harness counted ingredients ${Date.now().toString(36)}`,
    description: "Written by the harness's counted-ingredients scenario.",
    servings: 2,
    tags: [],
    nutrition: {},
    nutritionSource: "INGREDIENTS",
    ingredients: [
      { quantity: 2, name: "Egg", catalogSlug: "egg" },
      { quantity: 1, name: "Lemon", catalogSlug: "lemon", note: "juiced" },
      { quantity: 200, unit: "g", name: "All-purpose flour" },
    ],
    steps: [{ instruction: "Whisk everything together." }],
  });

  await page.goto(`/app/recipes/${recipe.id}`);
  const lines = page.locator("ul.ingredients li");
  await expect(lines.nth(0)).toHaveText(/^\s*2 Eggs$/);
  await expect(lines.nth(1)).toHaveText(/^\s*1 Lemon, juiced$/);
  await expect(lines.nth(2)).toHaveText(/^\s*200 g All-purpose flour$/);
});
