import { expect, register, test } from "./fixtures.ts";

/**
 * Country of the Week's wheel (ADR-0017), on a fresh account so no other
 * scenario's picks are on its ledger: the plan page's panel starts
 * collapsed, a spin lands on a country, removing it spins again, choosing
 * locks the week and puts its name in the collapsed header, the next week
 * can no longer land on it, and changing the country unlocks the week.
 */
test("spin the wheel, remove a country, choose one, and change it", async ({ page }) => {
  await register(page, `wheel-${Date.now().toString(36)}`);

  const toggle = page.getByRole("button", { name: /^Country of the Week/ });
  const panel = page.locator(".week-panel").filter({ has: toggle });
  const landed = panel.locator(".panel-highlight h3");
  const choose = panel.getByRole("button", { name: /^Choose / });

  /** Waits out the spin: the result's buttons appear once the wheel stops. */
  const landedCountry = async () => {
    await expect(choose).toBeVisible({ timeout: 10_000 });
    return (await landed.innerText()).trim();
  };

  await test.step("turn Country of the Week on", async () => {
    const banner = page.getByRole("banner");
    await banner.getByRole("button", { name: "Profile" }).click();
    await banner.getByRole("link", { name: "Plugins", exact: true }).click();
    await page.getByRole("checkbox", { name: "Country of the Week" }).check();
    await expect(page.getByRole("checkbox", { name: "Country of the Week" })).toBeEnabled();
  });

  await test.step("the plan shows the panel collapsed, with no country", async () => {
    await page.getByRole("navigation", { name: "App" }).getByRole("link", { name: "Plan" }).click();
    await expect(toggle).toBeVisible();
    await expect(toggle).toHaveAttribute("aria-expanded", "false");
    await expect(toggle).toHaveAccessibleName("Country of the Week");
  });

  let removed = "";
  await test.step("a spin lands on a country", async () => {
    await toggle.click();
    await expect(panel).toContainText("197 countries left.");
    await panel.getByRole("button", { name: "Spin" }).click();
    removed = await landedCountry();
    expect(removed).not.toBe("");
  });

  let chosen = "";
  await test.step("removing it spins again, somewhere else", async () => {
    await panel.getByRole("button", { name: "Remove from wheel" }).click();
    // The old result stays up until the new spin replaces it.
    await expect(choose).not.toHaveAccessibleName(`Choose ${removed}`, { timeout: 10_000 });
    chosen = await landedCountry();
    expect(chosen).not.toBe(removed);
  });

  await test.step("choosing locks the week and names it in the header", async () => {
    await choose.click();
    await expect(panel.getByRole("button", { name: "Change country" })).toBeVisible();
    await expect(toggle).toHaveAccessibleName(`Country of the Week ${chosen}`);
    await expect(panel.locator(".week-panel-summary")).toContainText(chosen);
  });

  await test.step("the choice survives a reload, collapsed", async () => {
    await page.reload();
    await expect(toggle).toHaveAttribute("aria-expanded", "false");
    await expect(toggle).toHaveAccessibleName(`Country of the Week ${chosen}`);
  });

  await test.step("next week, both countries are off the wheel", async () => {
    await page.getByRole("button", { name: /Next/ }).click();
    await expect(toggle).toHaveAccessibleName("Country of the Week");
    await toggle.click();
    await expect(panel).toContainText("195 countries left.");
    await expect(panel).toContainText(`after your last pick, ${chosen}.`);
    await page.getByRole("button", { name: /Previous/ }).click();
  });

  await test.step("changing the country unlocks the week", async () => {
    await expect(toggle).toHaveAccessibleName(`Country of the Week ${chosen}`);
    await toggle.click();
    await panel.getByRole("button", { name: "Change country" }).click();
    await expect(panel.getByRole("button", { name: "Spin" })).toBeVisible();
    await expect(panel).toContainText("196 countries left.");
    await expect(toggle).toHaveAccessibleName("Country of the Week");
    // Scoped to <main>: Next.js's route announcer is an alert too.
    await expect(page.locator("main").getByRole("alert")).toHaveCount(0);
  });
});
