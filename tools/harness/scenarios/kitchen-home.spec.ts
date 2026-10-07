import { endpoints } from "../src/config.ts";
import { apiAs, browserWeek, expect, register, test } from "./fixtures.ts";

/**
 * The Kitchen home (docs/design/index.md, "Kitchen home"), on a fresh
 * account so no other run's plans or wheel picks are in the way: with
 * tonight open and every earlier evening of the week planned, the Country
 * of the Week card (turned on first, since plugins are opt-in, ADR-0013;
 * and with Italy chosen for the week, since its card follows the week's
 * country, ADR-0017) offers tonight; adding it puts the recipe under
 * Tonight and on today's row of the week. In a shared household
 * (ADR-0019), each member's Tonight and week show only the meals they eat.
 */

test("tonight, the week, and a plugin's thought for an open evening", async ({ page }) => {
  await register(page, `kitchen-${Date.now().toString(36)}`);
  const api = await apiAs(page);
  // The page's "today" is the browser's local date, so the scenario asks the browser.
  const { today, monday } = await browserWeek(page);
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

test("in a shared household, Tonight and the week are the meals you eat", async ({ page, browser }, testInfo) => {
  test.slow();
  const stamp = Date.now().toString(36);
  const host = `kitchen-host-${stamp}`;
  const guest = `kitchen-guest-${stamp}`;
  const hostDinner = `Harness host's dinner ${stamp}`;
  const guestLunch = `Harness guest's lunch ${stamp}`;
  const ourDinner = `Harness shared dinner ${stamp}`;

  await register(page, host);
  const hostApi = await apiAs(page);
  const guestContext = await browser.newContext({
    baseURL: endpoints().web,
    recordHar: { path: testInfo.outputPath("guest.har"), content: "embed" },
  });
  const guestPage = await guestContext.newPage();
  try {
    await register(guestPage, guest);
    const guestApi = await apiAs(guestPage);
    await test.step("the guest joins the host's household", async () => {
      const { token } = await hostApi<{ token: string }>("POST", "/api/v1/household/invites");
      await guestApi("POST", "/api/v1/household/join", { token });
    });

    const { today, monday } = await browserWeek(page);
    // Another day of this week for the shared dinner, so today's row keeps only the host's.
    const otherDay = today === monday ? nextDay(monday) : monday;
    await test.step("today: a dinner only the host eats and a lunch only the guest eats; a dinner both eat on another day", async () => {
      const hostId = (await hostApi<{ id: string }>("GET", "/api/v1/me")).id;
      const guestId = (await guestApi<{ id: string }>("GET", "/api/v1/me")).id;
      const meals = [
        { title: hostDinner, date: today, mealType: "DINNER", diners: [hostId] },
        { title: guestLunch, date: today, mealType: "LUNCH", diners: [guestId] },
        { title: ourDinner, date: otherDay, mealType: "DINNER", diners: [hostId, guestId] },
      ];
      for (const { title, date, mealType, diners } of meals) {
        const recipe = await hostApi<{ id: string }>("POST", "/api/v1/recipes", {
          title,
          description: "Written by the harness's kitchen-home scenario.",
          servings: 2,
          tags: [],
          nutrition: { calories: 400, proteinG: 20, carbsG: 40, fatG: 10 },
          nutritionSource: "MANUAL",
          ingredients: [{ quantity: 400, unit: "g", name: "Potatoes" }],
          steps: [{ instruction: "Cook it." }],
        });
        await hostApi("POST", `/api/v1/plans/${monday}/entries`, { date, mealType, recipeId: recipe.id, servings: diners.length, diners });
      }
    });

    await test.step("the host's Tonight is their dinner; the guest's lunch is nowhere on the page", async () => {
      await page.goto("/app");
      const tonight = page.getByRole("region", { name: "Tonight" });
      const week = page.getByRole("region", { name: "This week" });
      await expect(tonight.getByRole("heading", { name: hostDinner, level: 3 })).toBeVisible();
      await expect(week.locator("li.today").getByRole("link", { name: hostDinner })).toBeVisible();
      await expect(week.getByRole("link", { name: ourDinner })).toBeVisible();
      await expect(page.locator("main")).not.toContainText(guestLunch);
    });

    await test.step("the guest's Today is their lunch, and the host's dinner is not on their week", async () => {
      await guestPage.goto("/app");
      const today = guestPage.getByRole("region", { name: "Today" });
      const week = guestPage.getByRole("region", { name: "This week" });
      await expect(today.getByRole("heading", { name: guestLunch, level: 3 })).toBeVisible();
      await expect(week.locator("li.today")).toContainText("Nothing yet");
      await expect(week.getByRole("link", { name: ourDinner })).toBeVisible();
      await expect(guestPage.locator("main")).not.toContainText(hostDinner);
    });
  } finally {
    await guestContext.close();
  }
});

function nextDay(isoDate: string): string {
  const day = new Date(`${isoDate}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + 1);
  return day.toISOString().slice(0, 10);
}
