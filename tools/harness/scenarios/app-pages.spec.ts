import { expect, test } from "./fixtures.ts";

/**
 * Every signed-in page loads its data without an error alert. Catches API
 * calls the pages make on load being rejected (the "The API rejected the
 * request." family of bugs).
 */
const PAGES = [
  { path: "/app", heading: /^Good (morning|afternoon|evening)\.$/ },
  { path: "/app/recipes", heading: "Recipes" },
  { path: "/app/plan", heading: /plan/i },
  { path: "/app/shopping-list", heading: /shopping list/i },
  { path: "/app/log", heading: /log/i },
];

test("signed-in pages load without errors", async ({ loggedInPage: page }) => {
  for (const { path, heading } of PAGES) {
    await test.step(`open ${path}`, async () => {
      await page.goto(path);
      await expect(page.getByRole("heading", { name: heading, level: 1 })).toBeVisible();
      // Let the page's on-load fetches settle before checking for an alert.
      await page.waitForLoadState("networkidle");
      // Scoped to <main>: Next.js's route announcer is an alert too.
      await expect(page.locator("main").getByRole("alert")).toHaveCount(0);
    });
  }
});
