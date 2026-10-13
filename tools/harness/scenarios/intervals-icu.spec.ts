import type { Page } from "@playwright/test";

import { apiAs, browserWeek, expect, register, test } from "./fixtures.ts";

/** The fake intervals.icu (tools/harness/fakes/intervals-icu), published on loopback by compose.fakes.yml. */
const FAKE = `http://127.0.0.1:${process.env.INTERVALS_ICU_FAKE_PORT ?? "8090"}`;
const GOOD_KEY = "fake-intervals-icu-key";

type Session = { id: string; date: string; source: string; kind: string; durationMinutes: number };
type Day = {
  date: string;
  target?: { calories: number; basis: { sessions: { sessionId: string; kcal: number; method: string }[] } };
  sessions: Session[];
};

test("a person connects intervals.icu and their planned workouts reach the plan", async ({ page }) => {
  await register(page, `icu-${Date.now().toString(36)}`);
  expect((await page.request.post(`${FAKE}/_fake/reset`)).ok(), "the fake resets").toBe(true);
  const banner = page.getByRole("banner");
  const nav = page.getByRole("navigation", { name: "App" });
  const section = page.getByRole("region", { name: "intervals.icu" });
  const openFuel = async () => {
    await banner.getByRole("button", { name: "Profile" }).click();
    await banner.getByRole("link", { name: "Fuel", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Fuel", level: 1 })).toBeVisible();
  };

  await test.step("targets on, with a profile weight intervals.icu does not have", async () => {
    await openFuel();
    await page.getByRole("checkbox", { name: "Show fuel targets" }).check();
    await page.getByRole("spinbutton", { name: "Weight (kg)" }).fill("70");
    await page.getByRole("radio", { name: "Male", exact: true }).check();
    await page.getByRole("spinbutton", { name: "Body fat (%)" }).fill("15");
    await page.getByRole("spinbutton", { name: "FTP (watts)" }).fill("280");
    await page.getByRole("button", { name: "Save" }).click();
    await expect(page.getByText("Saved.")).toBeVisible();
    await expect(section.getByRole("status")).toHaveText("Not connected.");
  });

  await test.step("a key intervals.icu refuses is a plain error and connects nothing", async () => {
    await section.getByRole("textbox", { name: "Athlete ID" }).fill("i1");
    await section.getByLabel("API key").fill("not-the-key");
    const answer = page.waitForResponse(
      (response) => response.url().endsWith("/api/v1/me/intervals-icu") && response.request().method() === "PUT",
    );
    await section.getByRole("button", { name: "Connect" }).click();
    expect((await answer).status()).toBe(400);
    await expect(section.getByRole("alert")).toContainText("did not accept this athlete id and API key");
    await expect(section.getByRole("status")).toHaveText("Not connected.");
  });

  await test.step("the right key connects and syncs at once", async () => {
    await section.getByLabel("API key").fill(GOOD_KEY);
    await section.getByRole("button", { name: "Connect" }).click();
    await expect(section.getByRole("status")).toContainText(/^Connected as i1\. Last synced .+\.$/);
    await expect(section.getByLabel("API key"), "the key is never shown again").toHaveCount(0);
    await expect(section.getByRole("button", { name: "Refresh" })).toBeVisible();
  });

  await test.step("intervals.icu's weight is offered, and taken only when asked", async () => {
    await expect(page.getByText("intervals.icu has 70.8 kg.")).toBeVisible();
    await expect(page.getByText(/intervals\.icu has 282 W\./)).toBeVisible();
    await page.getByRole("button", { name: "Use 70.8 kg from intervals.icu" }).click();
    await expect(page.getByRole("spinbutton", { name: "Weight (kg)" })).toHaveValue("70.8");
    await expect(page.getByText("intervals.icu has 70.8 kg.")).toHaveCount(0);
  });

  const api = await apiAs(page);
  const synced = await syncedDay(page, api);

  await test.step("the plan shows the synced workout with its source, and its target uses the workout's work", async () => {
    const workout = synced.sessions.find((session) => session.source === "INTERVALS_ICU")!;
    const energy = synced.target!.basis.sessions.find((session) => session.sessionId === workout.id)!;
    expect(energy.method, "the fake's sweet spot ride carries joules").toBe("PLANNED_WORK");
    expect(energy.kcal, "1,360,800 J is 1361 kJ, taken as kcal").toBe(1361);

    await nav.getByRole("link", { name: "Plan" }).click();
    const card = await dayCard(page, synced.date);
    const training = card.getByRole("group", { name: /^Training for / });
    await expect(training.locator(".training-parts")).toHaveText("Ride · 90 min · Hard · Planned · intervals.icu");
    await expect(training.getByRole("button", { name: /^Remove / }), "synced sessions have no remove control").toHaveCount(0);
    await expect(card.getByRole("group", { name: /^Fuel for / })).toContainText(`${synced.target!.calories} kcal`);
  });

  await test.step("disconnecting removes the synced training", async () => {
    await openFuel();
    await section.getByRole("button", { name: "Disconnect" }).click();
    await section.getByRole("button", { name: "Yes, disconnect" }).click();
    await expect(section.getByRole("status")).toHaveText("Not connected.");
    const after = await api<{ days: Day[] }>("GET", `/api/v1/me/fuel?from=${synced.date}&to=${synced.date}`);
    expect(after.days[0]!.sessions.filter((session) => session.source === "INTERVALS_ICU")).toHaveLength(0);
  });
});

/** The day of this week, by the browser's date, that holds the fake's 90-minute planned ride. */
async function syncedDay(page: Page, api: Awaited<ReturnType<typeof apiAs>>): Promise<Day> {
  const { monday } = await browserWeek(page);
  const sunday = new Date(`${monday}T00:00:00Z`);
  sunday.setUTCDate(sunday.getUTCDate() + 6);
  const { days } = await api<{ days: Day[] }>("GET", `/api/v1/me/fuel?from=${monday}&to=${sunday.toISOString().slice(0, 10)}`);
  const day = days.find((d) => d.sessions.some((s) => s.source === "INTERVALS_ICU" && s.durationMinutes === 90));
  expect(day, "the fake's ride today is in this week").toBeTruthy();
  return day!;
}

async function dayCard(page: Page, date: string) {
  const { monday } = await browserWeek(page);
  const index = Math.round((Date.parse(`${date}T00:00:00Z`) - Date.parse(`${monday}T00:00:00Z`)) / 86_400_000);
  return page.locator(".week .day").nth(index);
}
