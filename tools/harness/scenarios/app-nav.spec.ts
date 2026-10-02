import { expect, test } from "./fixtures.ts";

/**
 * The app nav scrolls sideways on narrow screens, which makes it a vertical
 * scroll container too: any vertical overflow (the active underline once
 * hung 1px below it) shows a scrollbar beside the links.
 */
test("app nav has no vertical overflow", async ({ loggedInPage: page }) => {
  await page.goto("/app");
  const nav = page.getByRole("navigation", { name: "App" });
  await expect(nav).toBeVisible();
  const { scrollHeight, clientHeight } = await nav.evaluate((el) => ({
    scrollHeight: el.scrollHeight,
    clientHeight: el.clientHeight,
  }));
  expect(scrollHeight).toBe(clientHeight);
});
