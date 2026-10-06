import { apiAs, expect, register, test } from "./fixtures.ts";

/**
 * The Kitchen home (docs/design/index.md, "Kitchen home"), on a fresh
 * account so no other run's plans or wheel picks are in the way: with
 * tonight open and every earlier evening of the week planned, the Country
 * of the Week card (turned on first, since plugins are opt-in, ADR-0013;
 * and with Italy chosen for the week, since its card follows the week's
 * country, ADR-0017) offers tonight; adding it puts the recipe under
 * Tonight and on today's row of the week.
 */

test("tonight, the week, and a plugin's thought for an open evening", async ({ page }) => {
  await register(page, `kitchen-${Date.now().toString(36)}`);
  const api = await apiAs(page);
  // The page's "today" is the browser's local date, so the scenario asks the browser.
  const { today, monday } = await page.evaluate(() => {
    const iso = (date: Date) =>
      `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    const now = new Date();
    const start = new Date(now);
    start.setDate(now.getDate() - ((now.getDay() + 6) % 7));
    return { today: iso(now), monday: iso(start) };
  });
  const planPath = `/api/v1/plans/${monday}`;

  await test.step("turn Country of the Week on", async () => {
    await api("PUT", "/api/v1/me/plugins/country-week", { enabled: true });
  });

  await test.step("give the week a country", async () => {
    const chosen = await api<{ summary?: { label: string } }>("POST", `${planPath}/panels/country-week/actions`, {
      id: "choose",
      value: "IT",
    });
    expect(chosen.summary?.label, "Italy can be chosen for a new user's week").toBe("Italy");
  });

  await test.step("plan every earlier evening, leaving tonight open", async () => {
    const library = await api<{ id: string; title: string }[]>("GET", "/api/v1/recipes/library");
    const filler = library.find((recipe) => recipe.title === "Sheet-Pan Chicken and Potatoes") ?? library[0];
    expect(filler, "the library is seeded").toBeTruthy();
    for (let date = monday; date < today; date = nextDay(date)) {
      await api("POST", `${planPath}/entries`, { date, mealType: "DINNER", recipeId: filler!.id, servings: 2 });
    }
  });

  const tonight = page.getByRole("region", { name: "Tonight" });
  const week = page.getByRole("region", { name: "This week" });
  const thought = page.locator("section.thought");

  await test.step("tonight is open, and the plugin offers it", async () => {
    await page.goto("/app");
    await expect(tonight.getByRole("heading", { name: "Nothing planned for tonight" })).toBeVisible();
    await expect(tonight.getByRole("link", { name: "Plan tonight" })).toBeVisible();
    await expect(week.locator("li.today")).toContainText("Nothing yet");
    await expect(thought.getByRole("heading", { name: /^A thought for / })).toBeVisible();
    await expect(thought).toContainText("from Country of the Week");
  });

  let title = "";
  await test.step("add the thought to tonight", async () => {
    title = (await thought.locator("p").filter({ hasText: "would fill the open evening" }).innerText()).replace(
      / would fill the open evening\.$/,
      "",
    );
    await thought.getByRole("button", { name: /^Add to / }).click();
    await expect(thought.getByRole("status")).toContainText(`Added ${title}`);
  });

  await test.step("it is tonight's dinner, marked in the week", async () => {
    await expect(tonight.getByRole("heading", { name: title, level: 3 })).toBeVisible();
    await expect(tonight.getByRole("link", { name: "Start cooking" })).toBeVisible();
    await expect(week.locator("li.today").getByRole("link", { name: title })).toBeVisible();
    // Scoped to <main>: Next.js's route announcer is an alert too.
    await expect(page.locator("main").getByRole("alert")).toHaveCount(0);
  });

  await test.step("it survives a reload", async () => {
    await page.reload();
    await expect(tonight.getByRole("heading", { name: title, level: 3 })).toBeVisible();
  });
});

function nextDay(isoDate: string): string {
  const day = new Date(`${isoDate}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + 1);
  return day.toISOString().slice(0, 10);
}
