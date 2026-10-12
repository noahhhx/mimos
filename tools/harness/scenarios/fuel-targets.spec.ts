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

type Target = { calories: number; proteinG: number; carbsG: number; fatG: number };

async function expectLinesMatchApi(page: Page, api: Awaited<ReturnType<typeof apiAs>>): Promise<void> {
  const week = page.locator(".week .day");
  const { monday } = await browserWeek(page);
  const sunday = new Date(`${monday}T00:00:00Z`);
  sunday.setUTCDate(sunday.getUTCDate() + 6);
  const { days } = await api<{ days: { date: string; target?: Target }[] }>(
    "GET",
    `/api/v1/me/fuel?from=${monday}&to=${sunday.toISOString().slice(0, 10)}`,
  );
  expect(days).toHaveLength(7);
  for (const [index, day] of days.entries()) {
    const { calories, proteinG, carbsG, fatG } = day.target!;
    await expect(week.nth(index).getByRole("group", { name: /^Fuel for / }).locator(".fuel-target")).toHaveText(
      `${calories} kcal · ${proteinG} g protein · ${carbsG} g carbs · ${fatG} g fat`,
    );
  }
}
