import { expect, register, test } from "./fixtures.ts";

/**
 * Plugins are opt-in per user (ADR-0013): a fresh account sees no
 * plugin panels until it turns Country of the Week (in the default compose
 * stack) on from the Plugins page, reached through the profile menu. The
 * plan page then shows its panel (ADR-0017; its suggestion card waits for
 * a chosen country, which `country-wheel` covers), and drops it when the
 * plugin is turned off. The account is registered through Keycloak's sign-up form, so the
 * scenario never depends on what other scenarios chose for the shared
 * test users.
 */
test("a plugin suggests only after the user turns it on", async ({ page }) => {
  await register(page, `plugins-${Date.now().toString(36)}`);

  const panels = page.getByRole("group", { name: "Plugins for this week" });
  const plugin = page.getByRole("checkbox", { name: "Country of the Week" });

  const openPlan = async () => {
    await page.getByRole("navigation", { name: "App" }).getByRole("link", { name: "Plan" }).click();
    await expect(page.getByRole("heading", { name: "Plan", level: 1 })).toBeVisible();
    // The week grid renders once the plan and its suggestions have loaded.
    await expect(page.locator(".week")).toBeVisible();
  };

  const openPlugins = async () => {
    const banner = page.getByRole("banner");
    await banner.getByRole("button", { name: "Profile" }).click();
    await banner.getByRole("link", { name: "Plugins", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Plugins", level: 1 })).toBeVisible();
  };

  await test.step("a fresh account has no plugin panels", async () => {
    await openPlan();
    await expect(panels).toHaveCount(0);
  });

  await test.step("the Plugins page explains plugins and lists this one, off", async () => {
    await openPlugins();
    await expect(page.getByRole("heading", { name: "What plugins are" })).toBeVisible();
    await expect(plugin).not.toBeChecked();
  });

  await test.step("turning it on brings its panel to the plan", async () => {
    await plugin.check();
    await expect(plugin).toBeChecked();
    await expect(plugin).toBeEnabled(); // saved
    await openPlan();
    await expect(panels.getByRole("button", { name: "Country of the Week" })).toBeVisible();
  });

  await test.step("the choice is kept", async () => {
    await page.reload();
    await expect(panels.getByRole("button", { name: "Country of the Week" })).toBeVisible();
    await openPlugins();
    await expect(plugin).toBeChecked();
  });

  await test.step("turning it off takes the panel away", async () => {
    await plugin.uncheck();
    await expect(plugin).not.toBeChecked();
    await expect(plugin).toBeEnabled(); // saved
    // Scoped to <main>: Next.js's route announcer is an alert too.
    await expect(page.locator("main").getByRole("alert")).toHaveCount(0);
    await openPlan();
    await expect(panels).toHaveCount(0);
  });
});
