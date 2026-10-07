import { appendFileSync } from "node:fs";

import { expect, test as base, type Page } from "@playwright/test";

import { DEFAULT_USER, USERS, endpoints } from "../src/config.ts";
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
  consoleLog: void;
  /**
   * A page on the app shell (`/app`), signed in through the real Keycloak
   * form. oidc-client-ts keeps tokens in sessionStorage, which Playwright's
   * storageState does not persist, so every test signs in afresh.
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
        write({ type: message.type(), text: message.text(), source: url ? `${url}:${lineNumber}` : null, page: message.page()?.url() ?? null });
      });
      context.on("weberror", (webError) => {
        const error = webError.error();
        write({ type: "pageerror", text: error.message, stack: error.stack ?? null, page: webError.page()?.url() ?? null });
      });
      await use();
    },
    { auto: true },
  ],

  loggedInPage: async ({ page, user }, use) => {
    const password = USERS[user];
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

/** The API, called as the signed-in user with the token oidc-client-ts keeps in sessionStorage. */
export async function apiAs(page: Page) {
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

export { expect };
