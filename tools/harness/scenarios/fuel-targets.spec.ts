import type { Page } from "@playwright/test";

import { endpoints } from "../src/config.ts";
import { apiAs, browserWeek, expect, register, test } from "./fixtures.ts";

const golden = { enabled: true, weightKg: 70, sex: "MALE", bodyFatPercent: 15, ftpWatts: 280, goal: "FUEL", proteinGPerKg: 1.6 };

test("the plan shows each day's fuel once targets are on", async ({ page }) => {
  await register(page, `fuel-plan-${Date.now().toString(36)}`);
  const nav = page.getByRole("navigation", { name: "App" });
  const mondayFuel = page.locator(".week .day").first().getByRole("group", { name: /^Fuel for / });

  await test.step("targets off: no day shows its fuel", async () => {
    await nav.getByRole("link", { name: "Plan" }).click();
    await expect(page.locator(".week .day")).toHaveCount(7);
    await expect(page.getByText("Fuel for the day")).toHaveCount(0);
    await expect(page.getByRole("group", { name: /^Training for / })).toHaveCount(0);
  });

  await test.step("the golden profile is saved from the Fuel page", async () => {
    const banner = page.getByRole("banner");
    await banner.getByRole("button", { name: "Profile" }).click();
    await banner.getByRole("link", { name: "Fuel", exact: true }).click();
    await page.getByRole("checkbox", { name: "Show fuel targets" }).check();
    await page.getByRole("spinbutton", { name: "Weight (kg)" }).fill("70");
    await page.getByRole("radio", { name: "Male", exact: true }).check();
    await page.getByRole("spinbutton", { name: "Body fat (%)" }).fill("15");
    await page.getByRole("spinbutton", { name: "FTP (watts)" }).fill("280");
    await page.getByRole("radio", { name: "Fuel my training" }).check();
    await page.getByRole("button", { name: "Save" }).click();
    await expect(page.getByRole("status")).toHaveText("Saved.");
  });

  await test.step("Monday reads Fuel for the day: 2678 kcal and 350 g carbs", async () => {
    await nav.getByRole("link", { name: "Plan" }).click();
    await expect(page.getByRole("tablist", { name: "Whose meals" }), "a household of one has no toggle").toHaveCount(0);
    await expect(page.getByRole("group", { name: /^Fuel for / })).toHaveCount(7);
    await expect(mondayFuel).toContainText("Fuel for the day");
    await expect(mondayFuel).toContainText("2678 kcal");
    await expect(mondayFuel).toContainText("350 g carbs");
  });

  await test.step("every day's line matches the API", async () => {
    const api = await apiAs(page);
    await expectLinesMatchApi(page, api);
  });

  const api = await apiAs(page);
  const tuesdayCard = page.locator(".week .day").nth(1);
  const tuesdayFuel = tuesdayCard.getByRole("group", { name: /^Fuel for / }).locator(".fuel-target");
  const tuesdayTraining = tuesdayCard.getByRole("group", { name: /^Training for / });
  const tuesday = await tuesdayDay(page, api);
  const restDayText = targetText(tuesday.target!);
  expect(tuesday.target!.basis.loadBand, "a day without training is LIGHT").toBe("LIGHT");

  await test.step("a 0- or 1441-minute session is refused under the field and nothing is added", async () => {
    await tuesdayTraining.getByRole("button", { name: /^Add training for / }).click();
    const form = tuesdayTraining.getByRole("form", { name: /^Add training for / });
    for (const minutes of ["0", "1441"]) {
      await form.getByRole("spinbutton", { name: "Minutes" }).fill(minutes);
      await form.getByRole("button", { name: "Add", exact: true }).click();
      await expect(form.getByText("Enter a whole number of minutes from 1 to 1440.")).toBeVisible();
      await expect(form.getByRole("spinbutton", { name: "Minutes" })).toHaveAttribute("aria-invalid", "true");
    }
    expect((await tuesdayDay(page, api)).sessions, "nothing was added").toHaveLength(0);
    await expect(tuesdayFuel).toHaveText(restDayText);
  });

  await test.step("a 90-minute endurance ride raises Tuesday's target by the ride's energy, without a reload", async () => {
    const form = tuesdayTraining.getByRole("form", { name: /^Add training for / });
    await form.getByRole("combobox", { name: "Sport" }).selectOption({ label: "Ride" });
    await form.getByRole("spinbutton", { name: "Minutes" }).fill("90");
    await form.getByRole("combobox", { name: "Intensity" }).selectOption({ label: "Endurance" });
    await form.getByRole("button", { name: "Add", exact: true }).click();
    await expect(form).toHaveCount(0);
    await expect(tuesdayTraining.locator(".training-parts")).toHaveText("Ride · 90 min · Endurance · Planned");

    const ridden = await tuesdayDay(page, api);
    const { calories, carbsG, basis } = ridden.target!;
    expect(basis.sessions).toHaveLength(1);
    expect(calories - tuesday.target!.calories, "calories rise by the ride's exercise energy").toBe(
      basis.sessions[0]!.kcal,
    );
    expect(basis.loadBand, "90 minutes is the HIGH band").toBe("HIGH");
    if (!basis.carbsLeftBand) {
      expect(carbsG, "carbs sit in HIGH's 6 to 10 g/kg").toBeGreaterThanOrEqual(6 * golden.weightKg);
      expect(carbsG).toBeLessThanOrEqual(10 * golden.weightKg);
    }
    await expect(tuesdayFuel).toHaveText(targetText(ridden.target!));
  });

  await test.step("removing the ride brings Tuesday back to its earlier target", async () => {
    await tuesdayTraining.getByRole("button", { name: "Remove Ride, 90 min" }).click();
    await expect(tuesdayTraining.locator(".training-session")).toHaveCount(0);
    await expect(tuesdayFuel).toHaveText(restDayText);
    expect((await tuesdayDay(page, api)).target).toEqual(tuesday.target);
  });

  await test.step("the Log page shows each day's fuel under what was logged", async () => {
    const { monday } = await browserWeek(page);
    const meal = (calories: number, proteinG: number, carbsG: number, fatG: number) => ({
      date: monday,
      mealType: "LUNCH",
      description: `Logged ${calories}`,
      nutrition: { calories, proteinG, carbsG, fatG },
    });
    await api("POST", "/api/v1/logs", meal(600, 30, 80, 20));
    await api("POST", "/api/v1/logs", meal(450, 25, 50, 15));

    await nav.getByRole("link", { name: "Log" }).click();
    const rows = page.getByRole("table").locator("tbody tr");
    await expect(rows, "each day has its logged row and its fuel row").toHaveCount(14);
    const days = await weekDays(page, api);
    for (const [index, day] of days.entries()) {
      const { calories, proteinG, carbsG, fatG } = day.target!;
      await expect(rows.nth(2 * index + 1).getByRole("cell")).toHaveText([
        "Fuel for the day",
        `${calories} kcal`,
        `${proteinG} g`,
        `${carbsG} g`,
        `${fatG} g`,
      ]);
    }
    await expect(rows.nth(0).getByRole("cell"), "Monday's two meals").toHaveText([
      /\S/,
      "1050 kcal",
      "55 g",
      "130 g",
      "35 g",
    ]);
    await expect(rows.nth(2).getByRole("cell"), "a day with nothing logged keeps its dashes").toHaveText([
      /\S/,
      "–",
      "–",
      "–",
      "–",
    ]);
  });
});

test("Everyone hides fuel in a shared household, and Mine shows only the viewer's", async ({ page, browser }) => {
  const stamp = Date.now().toString(36);
  await register(page, `fuel-host-${stamp}`);
  const hostApi = await apiAs(page);
  await hostApi("PUT", "/api/v1/me/fuel-profile", golden);

  const guestContext = await browser.newContext({ baseURL: endpoints().web });
  try {
    const guestPage = await guestContext.newPage();
    await register(guestPage, `fuel-guest-${stamp}`);
    const guestApi = await apiAs(guestPage);
    await test.step("a guest with their own targets joins the host's household", async () => {
      await guestApi("PUT", "/api/v1/me/fuel-profile", { ...golden, weightKg: 55, sex: "FEMALE", bodyFatPercent: 25 });
      const { token } = await hostApi<{ token: string }>("POST", "/api/v1/household/invites");
      await guestApi("POST", "/api/v1/household/join", { token });
    });
  } finally {
    await guestContext.close();
  }

  await test.step("Mine shows the host's fuel", async () => {
    await page.goto("/app/plan");
    await expect(page.getByRole("tab", { name: "Mine" })).toHaveAttribute("aria-selected", "true");
    await expect(page.getByRole("group", { name: /^Fuel for / })).toHaveCount(7);
    await expectLinesMatchApi(page, hostApi);
  });

  await test.step("Everyone shows no fuel", async () => {
    await page.getByRole("tab", { name: "Everyone" }).click();
    await expect(page.getByRole("tab", { name: "Everyone" })).toHaveAttribute("aria-selected", "true");
    await expect(page.locator(".week .day")).toHaveCount(7);
    await expect(page.getByText("Fuel for the day")).toHaveCount(0);
  });

  await test.step("Mine shows it again", async () => {
    await page.getByRole("tab", { name: "Mine" }).click();
    await expect(page.getByRole("group", { name: /^Fuel for / })).toHaveCount(7);
  });
});

type Target = {
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  basis: { loadBand: string; carbsLeftBand: boolean; sessions: { kcal: number }[] };
};
type Day = { date: string; target?: Target; sessions: unknown[] };

function targetText({ calories, proteinG, carbsG, fatG }: Target): string {
  return `${calories} kcal · ${proteinG} g protein · ${carbsG} g carbs · ${fatG} g fat`;
}

async function tuesdayDay(page: Page, api: Awaited<ReturnType<typeof apiAs>>): Promise<Day> {
  const { monday } = await browserWeek(page);
  const tuesday = new Date(`${monday}T00:00:00Z`);
  tuesday.setUTCDate(tuesday.getUTCDate() + 1);
  const date = tuesday.toISOString().slice(0, 10);
  const { days } = await api<{ days: Day[] }>("GET", `/api/v1/me/fuel?from=${date}&to=${date}`);
  return days[0]!;
}

async function weekDays(page: Page, api: Awaited<ReturnType<typeof apiAs>>): Promise<Day[]> {
  const { monday } = await browserWeek(page);
  const sunday = new Date(`${monday}T00:00:00Z`);
  sunday.setUTCDate(sunday.getUTCDate() + 6);
  const { days } = await api<{ days: Day[] }>(
    "GET",
    `/api/v1/me/fuel?from=${monday}&to=${sunday.toISOString().slice(0, 10)}`,
  );
  expect(days).toHaveLength(7);
  return days;
}

async function expectLinesMatchApi(page: Page, api: Awaited<ReturnType<typeof apiAs>>): Promise<void> {
  const week = page.locator(".week .day");
  const days = await weekDays(page, api);
  for (const [index, day] of days.entries()) {
    await expect(week.nth(index).getByRole("group", { name: /^Fuel for / }).locator(".fuel-target")).toHaveText(
      targetText(day.target!),
    );
  }
}
