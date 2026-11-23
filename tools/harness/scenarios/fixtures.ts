import { appendFileSync } from "node:fs";

import { expect, test as base, type Locator, type Page } from "@playwright/test";

import { DEFAULT_USER, USERS, endpoints, passwordOf } from "../src/config.ts";
import { redactText } from "../src/redact.ts";

/**
 * What every scenario gets: a HAR of the browser context and its console
 * output in the test's output folder (automatic), plus `loggedInPage`.
 * Scenarios import `test` and `expect` from here, not from @playwright/test.
 */

export interface HarnessFixtures {
  /** The realm user to sign in as — `harness ui --as`, default `test`. */
  user: string;
  /** Records console messages and uncaught page errors to console.jsonl. */
  // biome-ignore lint/suspicious/noConfusingVoidType: Playwright's type for a fixture with no value, whose use() is called with nothing.
  consoleLog: void;
  /**
   * A page on the app shell (`/app`), signed in through the real Keycloak
   * form. Every test signs in afresh, so none inherits another's session.
   */
  loggedInPage: Page;
}

export const test = base.extend<HarnessFixtures>({
  user: [process.env.HARNESS_USER ?? DEFAULT_USER, { option: true }],

  contextOptions: async ({ contextOptions }, use, testInfo) => {
    await use({ ...contextOptions, recordHar: { path: testInfo.outputPath("network.har"), content: "embed" } });
  },

  consoleLog: [
    async ({ context }, use, testInfo) => {
      const file = testInfo.outputPath("console.jsonl");
      const write = (entry: Record<string, unknown>): void => {
        appendFileSync(file, `${redactText(JSON.stringify({ at: new Date().toISOString(), ...entry }))}\n`);
      };
      context.on("console", (message) => {
        const { url, lineNumber } = message.location();
        write({
          type: message.type(),
          text: message.text(),
          source: url ? `${url}:${lineNumber}` : null,
          page: message.page()?.url() ?? null,
        });
      });
      context.on("weberror", (webError) => {
        const error = webError.error();
        write({
          type: "pageerror",
          text: error.message,
          stack: error.stack ?? null,
          page: webError.page()?.url() ?? null,
        });
      });
      await use();
    },
    { auto: true },
  ],

  loggedInPage: async ({ page, user }, use) => {
    const password = passwordOf(user);
    if (password === undefined) {
      throw new Error(`unknown user ${user} — the realm's test users are: ${Object.keys(USERS).join(", ")}`);
    }
    await test.step(`sign in as ${user}`, async () => {
      await page.goto("/app");
      await page.getByRole("button", { name: "Sign in" }).click();
      // Keycloak's login form (keycloak.v2 theme); ids are stable across themes.
      await page.locator("#username").fill(user);
      await page.locator("#password").fill(password);
      await page.locator("#kc-login").click();
      await expect(page).toHaveURL(/\/app$/);
      // The Kitchen home's week rail: its h1 is a greeting that changes with the hour.
      await expect(page.getByRole("heading", { name: "This week" })).toBeVisible();
    });
    await use(page);
  },
});

/** Signs up a new realm user with the home page's Create account and lands on the Kitchen. */
export async function register(page: Page, username: string): Promise<void> {
  await test.step(`register ${username}`, async () => {
    await page.goto("/");
    await page.getByRole("button", { name: "Create account" }).click();
    await fillRegistration(page, username);
    await expect(page).toHaveURL(/\/app$/);
    await expect(page.getByRole("heading", { name: "This week" })).toBeVisible();
  });
}

/** Fills in Keycloak's registration form and submits it; Keycloak then returns to the app. */
export async function fillRegistration(page: Page, username: string): Promise<void> {
  await expect(page.locator("#kc-page-title")).toHaveText("Register");
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(`${username}@example.com`);
  await page.locator("#firstName").fill("Harness");
  await page.locator("#lastName").fill("Cook");
  await page.locator("#password").fill("mimos-test");
  await page.locator("#password-confirm").fill("mimos-test");
  await page.getByRole("button", { name: "Register" }).click();
}

/** The API, called as the signed-in user with the token oidc-client-ts keeps in localStorage. */
export async function apiAs(page: Page) {
  const token = await page.evaluate(() => {
    const key = Object.keys(localStorage).find((name) => name.startsWith("oidc.user:"));
    return key ? (JSON.parse(localStorage.getItem(key) ?? "{}") as { access_token?: string }).access_token : undefined;
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

/** Today and this week's Monday by the browser's local date, which is what the page uses. */
export async function browserWeek(page: Page): Promise<{ today: string; monday: string }> {
  return page.evaluate(() => {
    const iso = (date: Date) =>
      `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    const now = new Date();
    const start = new Date(now);
    start.setDate(now.getDate() - ((now.getDay() + 6) % 7));
    return { today: iso(now), monday: iso(start) };
  });
}

/**
 * The plan page's ledger: a table of days by meals whose cells show names
 * only, and the inspector beside it that opens on a selected meal or day.
 * Days are counted from Monday (0).
 */
export function planLedger(page: Page) {
  const table = page.getByRole("table", { name: /^Meals for the week of / });
  const inspector = page.getByRole("complementary", { name: "Details" }).locator("section.inspector");
  const escaped = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const meal = (title: string): Locator => table.getByRole("button", { name: new RegExp(`^${escaped(title)}`) });
  const dayHead = (day: number): Locator => table.getByRole("columnheader").nth(day);
  return {
    table,
    /** A planned meal's cell, by the start of its name. */
    meal,
    /** The inspector, open on whatever is selected. */
    inspector,
    /** Selects a meal (unless it is already open) and answers its inspector. */
    async openMeal(title: string): Promise<Locator> {
      const open = page.getByRole("region", { name: title, exact: true });
      if ((await open.count()) === 0) {
        await meal(title).first().click();
      }
      await expect(open).toBeVisible();
      return open;
    },
    /** Selects a day by its column head and answers its inspector. */
    async openDay(day: number): Promise<Locator> {
      const head = dayHead(day).getByRole("button");
      if ((await head.getAttribute("aria-pressed")) !== "true") {
        await head.click();
      }
      await expect(inspector).toBeVisible();
      return inspector;
    },
    dayHead,
    /** A day's Planned cell: its accessible name reads "Planned for <day>: <kcal> of <target> kcal". */
    planned: (day: number): Locator => table.getByRole("button", { name: /^Planned for / }).nth(day),
    /** A day's Training cell, present only while fuel targets are on. */
    training: (day: number): Locator => table.getByRole("button", { name: /^Training for / }).nth(day),
    /** Plans a recipe or food in a day's slot through the picker. */
    async plan(day: number, slot: string, title: string): Promise<void> {
      await table
        .getByRole("button", { name: new RegExp(`^Add ${slot} for `) })
        .nth(day)
        .click();
      const picker = page.getByRole("dialog", { name: "Pick a meal" });
      await picker.getByRole("searchbox").fill(title);
      await picker.getByRole("button", { name: new RegExp(`^${escaped(title)}`) }).click();
      await expect(picker).toHaveCount(0);
      await expect(meal(title).first()).toBeVisible();
    },
  };
}

export { expect };
