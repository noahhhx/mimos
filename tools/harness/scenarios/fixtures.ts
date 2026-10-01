import { appendFileSync } from "node:fs";

import { expect, test as base, type Page } from "@playwright/test";

import { DEFAULT_USER, USERS } from "../src/config.ts";
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
      await expect(page.getByRole("heading", { name: "Your kitchen" })).toBeVisible();
    });
    await use(page);
  },
});

export { expect };
