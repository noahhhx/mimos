import { expect, test } from "./fixtures.ts";

/**
 * Log a meal from the log page's "Log something else": a seeded library
 * recipe picked from the What search and counted in servings, then a
 * custom item with its calories typed. Both entries are deleted at the
 * end, so reruns start from the same day.
 */
test("logs a recipe in servings, and a custom meal", async ({ loggedInPage: page }) => {
  const stamp = Date.now().toString(36);
  const snack = `Harness snack ${stamp}`;
  const entries = page.locator(".log-entries li");
  // Today may hold this recipe from an earlier run, so this run's entry is counted, not assumed alone.
  const shakshuka = entries.filter({ hasText: "Shakshuka" }).filter({ hasText: "(1.5 servings)" });
  let before = 0;
  let total = 0;

  await test.step("open the log", async () => {
    await page.goto("/app/log");
    await expect(page.getByRole("heading", { name: "Log something else" })).toBeVisible();
    before = await shakshuka.count();
  });

  await test.step("pick Shakshuka from the What search; the nutrition fields give way to servings", async () => {
    await page.getByLabel("What").fill("shaksh");
    const option = page.getByRole("option", { name: /^Shakshuka/ }).filter({ hasText: "Library ·" });
    await expect(option).toContainText(/Library · \d+ kcal per serving/);
    const perServing = Number((await option.textContent())?.match(/(\d+) kcal per serving/)?.[1]);
    await option.click();

    await expect(page.getByLabel("What")).toHaveValue("Shakshuka");
    await expect(page.getByLabel("Calories")).toHaveCount(0);
    await expect(page.getByLabel("Servings")).toHaveValue("1");

    await page.getByLabel("Servings").fill("1.5");
    const preview = page.locator(".log-total");
    await expect(preview).toHaveText(/^Comes to \d+ kcal/);
    total = Number((await preview.textContent())?.match(/^Comes to (\d+) kcal/)?.[1]);
    // The option rounds a serving to whole kcal, so 1.5 of it can be off by one.
    expect(Math.abs(total - 1.5 * perServing)).toBeLessThanOrEqual(1);
  });

  await test.step("log it: the day lists 1.5 servings at the previewed kcal", async () => {
    await page.getByRole("button", { name: "Log meal" }).click();
    await expect(shakshuka).toHaveCount(before + 1);
    await expect(shakshuka.last()).toContainText(`${total} kcal`);
    await expect(page.getByLabel("What")).toHaveValue("");
    await expect(page.getByLabel("Calories")).toBeVisible();
  });

  await test.step("picking a recipe again starts at 1 serving", async () => {
    await page.getByLabel("What").fill("shaksh");
    await page.getByRole("option", { name: /^Shakshuka/ }).filter({ hasText: "Library ·" }).click();
    await page.getByLabel("Servings").fill("2");
    await page.getByLabel("What").fill("shaksh");
    await page.getByRole("option", { name: /^Shakshuka/ }).filter({ hasText: "Library ·" }).click();
    await expect(page.getByLabel("Servings")).toHaveValue("1");
  });

  await test.step("typing over a picked recipe makes it a custom log again", async () => {
    await page.getByLabel("What").fill(snack);
    await expect(page.getByLabel("Servings")).toHaveCount(0);
    await expect(page.getByLabel("Calories")).toBeVisible();
  });

  await test.step("log the custom snack with its calories typed", async () => {
    await page.getByLabel("Calories").fill("210");
    await page.getByRole("button", { name: "Log meal" }).click();
    const logged = entries.filter({ hasText: snack });
    await expect(logged).toHaveCount(1);
    await expect(logged).toContainText("210 kcal");
    await expect(logged).not.toContainText("serving");
  });

  await test.step("delete what this run logged", async () => {
    await entries.filter({ hasText: snack }).getByRole("button", { name: "Delete" }).click();
    await expect(entries.filter({ hasText: snack })).toHaveCount(0);
    await shakshuka.last().getByRole("button", { name: "Delete" }).click();
    await expect(shakshuka).toHaveCount(before);
  });
});
