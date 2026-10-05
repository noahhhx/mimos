import type { Page } from "@playwright/test";

import { expect, test } from "./fixtures.ts";

/**
 * A stored session the API will not accept must sign the app out, not
 * leave every page failing with 401s and "Could not load…" errors
 * (the reported bug: a stale session in a browser tab). An expired token
 * with a live refresh token is renewed instead.
 *
 * Each test edits the oidc-client-ts user in sessionStorage, then reloads
 * the plan page.
 */

/** Rewrites the stored OIDC user's fields. */
async function editStoredUser(page: Page, changes: Record<string, unknown>): Promise<void> {
  await page.evaluate((fields) => {
    const key = Object.keys(sessionStorage).find((name) => name.startsWith("oidc.user:"));
    if (!key) {
      throw new Error("no stored OIDC user");
    }
    sessionStorage.setItem(key, JSON.stringify({ ...JSON.parse(sessionStorage.getItem(key)!), ...fields }));
  }, changes);
}

const anHourAgo = () => Math.floor(Date.now() / 1000) - 3600;

async function expectSignedOut(page: Page): Promise<void> {
  await expect(page.getByText("You need to sign in to plan meals.")).toBeVisible();
  await expect(page.getByRole("main").getByRole("button", { name: "Sign in" })).toBeVisible();
  await expect(page.getByText(/^Could not/)).toHaveCount(0);
}

test("an expired session that cannot be renewed asks to sign in again", async ({ loggedInPage: page }) => {
  await editStoredUser(page, { expires_at: anHourAgo(), refresh_token: "ended-session" });
  await page.goto("/app/plan");
  await expectSignedOut(page);
});

test("a token the API rejects asks to sign in again", async ({ loggedInPage: page }) => {
  await editStoredUser(page, { access_token: "not-a-valid-token" });
  await page.goto("/app/plan");
  await expectSignedOut(page);
});

test("an expired token is renewed with the refresh token", async ({ loggedInPage: page }) => {
  await editStoredUser(page, { expires_at: anHourAgo() });
  const plan = page.waitForResponse((response) => /\/api\/v1\/plans\/[\d-]+$/.test(response.url()));
  await page.goto("/app/plan");
  expect((await plan).status()).toBe(200);
  await expect(page.getByRole("button", { name: "← Previous" })).toBeVisible();
  await expect(page.getByText("You need to sign in to plan meals.")).toHaveCount(0);
  await expect(page.getByText(/^Could not/)).toHaveCount(0);
});
