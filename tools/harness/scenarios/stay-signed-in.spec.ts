import type { Page } from "@playwright/test";

import { endpoints } from "../src/config.ts";
import { expect, test } from "./fixtures.ts";

/**
 * Signing in once keeps a person signed in, like most sites: in a new tab,
 * after the browser is closed and reopened, and after days away (the
 * reported bug: every visit to a home server asked to sign in again).
 */

async function expectSignedIn(page: Page): Promise<void> {
  await page.goto(`${endpoints().web}/app/plan`);
  const signInPrompt = page.getByText("You need to sign in to plan meals.");
  const week = page.getByRole("button", { name: "← Previous" });
  await expect(week.or(signInPrompt)).toBeVisible();
  await expect(signInPrompt, "the plan page asks to sign in again").toHaveCount(0);
  await expect(page.getByText(/^Could not/)).toHaveCount(0);
}

test("a new tab is signed in", async ({ loggedInPage: page }) => {
  await expectSignedIn(await page.context().newPage());
});

test("a reopened browser is signed in", async ({ loggedInPage: page, browser }) => {
  // A new context with the old one's cookies and localStorage, as a browser keeps them across a restart.
  const reopened = await browser.newContext({ storageState: await page.context().storageState() });
  try {
    await expectSignedIn(await reopened.newPage());
  } finally {
    await reopened.close();
  }
});

test("the session outlasts a month away", async ({ loggedInPage: page }) => {
  const refreshToken = await page.evaluate(() => {
    const key = Object.keys(localStorage).find((name) => name.startsWith("oidc.user:"));
    return key ? (JSON.parse(localStorage.getItem(key)!) as { refresh_token?: string }).refresh_token : undefined;
  });
  expect(refreshToken, "the app keeps a refresh token in localStorage").toBeTruthy();
  const claims = JSON.parse(Buffer.from(refreshToken!.split(".")[1] ?? "", "base64url").toString()) as { iat: number; exp: number };
  expect(claims.exp - claims.iat, "seconds the refresh token lives unused").toBeGreaterThanOrEqual(30 * 24 * 3600);
});
