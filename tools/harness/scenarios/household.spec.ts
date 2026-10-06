import { endpoints } from "../src/config.ts";
import { apiAs, expect, fillRegistration, register, test } from "./fixtures.ts";

/**
 * Sharing a household (ADR-0019) between two fresh accounts, each in its
 * own browser: the host adds a recipe and creates an invite link; the
 * guest opens it signed out, registers, comes back to the join page, reads
 * what joining does, joins, finds the host's recipe marked "Added by" the
 * host, and leaves again. Fresh accounts keep `test` and `test2` out of
 * any household.
 */
test("invite someone, who joins, sees your recipe, and leaves", async ({ page, browser }, testInfo) => {
  test.slow();
  const stamp = Date.now().toString(36);
  const host = `host-${stamp}`;
  const guest = `guest-${stamp}`;
  const recipe = `Harness household stew ${stamp}`;

  await register(page, host);

  await test.step("the host adds a recipe", async () => {
    const api = await apiAs(page);
    await api("POST", "/api/v1/recipes", {
      title: recipe,
      description: "Written by the harness's household scenario.",
      servings: 4,
      tags: [],
      nutrition: {},
      nutritionSource: "MANUAL",
      ingredients: [{ quantity: 400, unit: "g", name: "Potatoes" }],
      steps: [{ instruction: "Simmer until soft." }],
    });
  });

  let link = "";
  await test.step("the host creates an invite link from the profile menu's Household page", async () => {
    await page.getByRole("button", { name: "Profile" }).click();
    await page.getByRole("link", { name: "Household" }).click();
    await expect(page.getByRole("heading", { name: "Household", level: 1 })).toBeVisible();
    await expect(page.locator(".member-list li")).toHaveText([`${host}You`]);
    await page.getByRole("button", { name: "Create invite link" }).click();
    link = await page.getByLabel("Invite link").inputValue();
    expect(link).toMatch(/\/app\/join\/[A-Za-z0-9_-]{43}$/);
    await expect(page.getByText(/^Works once, until /)).toBeVisible();
  });

  const guestContext = await browser.newContext({
    baseURL: endpoints().web,
    recordHar: { path: testInfo.outputPath("guest.har"), content: "embed" },
  });
  const guestPage = await guestContext.newPage();
  try {
    await test.step("the guest opens the link signed out, registers, and comes back to it", async () => {
      await guestPage.goto(link);
      await expect(guestPage.getByRole("heading", { name: "You have an invite" })).toBeVisible();
      await guestPage.getByRole("button", { name: "Sign in" }).click();
      await fillRegistration(guestPage, guest);
      await expect(guestPage).toHaveURL(link);
    });

    await test.step("the join page says what joining does", async () => {
      await expect(guestPage.getByRole("heading", { name: `Join ${host}'s household?` })).toBeVisible();
      await expect(guestPage.getByText("Your recipes and your own ingredients come with you")).toBeVisible();
      await expect(guestPage.getByText("Your meal plans, shopping lists and plugin settings are deleted.")).toBeVisible();
      await expect(guestPage.getByText("Your food log stays yours.")).toBeVisible();
    });

    await test.step("the guest joins", async () => {
      await guestPage.getByRole("button", { name: "Join household" }).click();
      await expect(guestPage).toHaveURL(/\/app\/household$/);
      await expect(guestPage.locator(".member-list li")).toHaveText([`${guest}You`, host]);
    });

    await test.step("the host's recipe is the guest's to cook, marked with who added it", async () => {
      await guestPage.goto("/app/recipes");
      const row = guestPage.locator(".recipe-list li").filter({ hasText: recipe });
      await expect(row).toContainText(`Added by ${host}`);
      await row.getByRole("link", { name: recipe }).click();
      await expect(guestPage.getByRole("heading", { name: recipe, level: 1 })).toBeVisible();
      await expect(guestPage.locator(".toolbar")).toContainText(`Added by ${host}`);
    });

    await test.step("the host sees the guest in the household", async () => {
      await page.reload();
      await expect(page.locator(".member-list li")).toHaveText([guest, `${host}You`]);
    });

    await test.step("the guest leaves, after reading what stays", async () => {
      await guestPage.goto("/app/household");
      await guestPage.getByRole("button", { name: "Leave household" }).click();
      const confirm = guestPage.getByRole("group", { name: "Leave this household?" });
      await expect(confirm).toContainText("The household keeps its recipes, ingredients, meal plans and shopping lists");
      await expect(confirm).toContainText("You keep your food log");
      await confirm.getByRole("button", { name: "Leave household" }).click();
      await expect(guestPage.locator(".member-list li")).toHaveText([`${guest}You`]);
      await expect(guestPage.getByText("It is just you for now.")).toBeVisible();
      await expect(guestPage.getByRole("button", { name: "Leave household" })).toHaveCount(0);
    });

    await test.step("the recipe stayed with the host's household", async () => {
      await guestPage.goto("/app/recipes");
      await expect(guestPage.getByRole("tab", { name: "My recipes (0)" })).toBeVisible();
      await expect(guestPage.getByText(recipe)).toHaveCount(0);
      // Scoped to <main>: Next.js's route announcer is an alert too.
      await expect(guestPage.locator("main").getByRole("alert")).toHaveCount(0);
    });
  } finally {
    await guestContext.close();
  }
});
