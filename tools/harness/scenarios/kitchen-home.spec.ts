import type { Page } from "@playwright/test";

import { endpoints } from "../src/config.ts";
import { expect, test } from "./fixtures.ts";

/**
 * The Kitchen home (docs/design/index.md, "Kitchen home"): with tonight open and
 * every earlier evening of the week planned, the Country of the Week card
 * (in the default compose stack, turned on for the user first: plugins
 * are opt-in, ADR-0013) offers tonight; adding it puts the recipe under
 * Tonight and on today's row of the week. Whatever the scenario planned is
 * removed again, and the plugin is left as it was found, so reruns start
 * from the same state.
 */

interface PlanEntry {
  id: string;
  date: string;
  mealType: string;
  recipeId: string;
  recipeTitle: string;
}

/** The API, called as the signed-in user with the token oidc-client-ts keeps in sessionStorage. */
async function apiAs(page: Page) {
  const token = await page.evaluate(() => {
    const key = Object.keys(sessionStorage).find((name) => name.startsWith("oidc.user:"));
    return key ? (JSON.parse(sessionStorage.getItem(key) ?? "{}") as { access_token?: string }).access_token : undefined;
  });
  expect(token, "the signed-in page holds an access token").toBeTruthy();
  const call = async <T>(method: string, path: string, data?: unknown): Promise<T> => {
    const response = await page.request.fetch(`${endpoints().api}${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}` },
      ...(data === undefined ? {} : { data }),
    });
    expect(response.ok(), `${method} ${path} answered ${response.status()}`).toBe(true);
    return (response.status() === 204 ? undefined : await response.json()) as T;
  };
  return call;
}

test("tonight, the week, and a plugin's thought for an open evening", async ({ loggedInPage: page }) => {
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
  const before = new Set((await api<{ entries: PlanEntry[] }>("GET", planPath)).entries.map((entry) => entry.id));
  const plugins = await api<{ id: string; enabled: boolean }[]>("GET", "/api/v1/me/plugins");
  const wasEnabled = plugins.find((plugin) => plugin.id === "country-week")?.enabled ?? false;

  try {
    await test.step("turn Country of the Week on", async () => {
      await api("PUT", "/api/v1/me/plugins/country-week", { enabled: true });
    });

    await test.step("plan every earlier open evening, leaving tonight open", async () => {
      const { entries } = await api<{ entries: PlanEntry[] }>("GET", planPath);
      const dinners = new Set(entries.filter((entry) => entry.mealType === "DINNER").map((entry) => entry.date));
      expect(dinners.has(today), `tonight (${today}) must start unplanned; remove its dinner and rerun`).toBe(false);
      const library = await api<{ id: string; title: string }[]>("GET", "/api/v1/recipes/library");
      const filler = library.find((recipe) => recipe.title === "Sheet-Pan Chicken and Potatoes") ?? library[0];
      expect(filler, "the library is seeded").toBeTruthy();
      for (let date = monday; date < today; date = nextDay(date)) {
        if (!dinners.has(date)) {
          await api("POST", `${planPath}/entries`, { date, mealType: "DINNER", recipeId: filler!.id, servings: 2 });
        }
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
  } finally {
    const { entries } = await api<{ entries: PlanEntry[] }>("GET", planPath);
    for (const entry of entries.filter((planned) => !before.has(planned.id))) {
      await api("DELETE", `${planPath}/entries/${entry.id}`);
    }
    if (!wasEnabled) {
      await api("PUT", "/api/v1/me/plugins/country-week", { enabled: false });
    }
  }
});

function nextDay(isoDate: string): string {
  const day = new Date(`${isoDate}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + 1);
  return day.toISOString().slice(0, 10);
}
