import type { Page, TestInfo } from "@playwright/test";

import { expect, test } from "./fixtures.ts";

/**
 * The Keycloak pages wear the Mimos login theme (deploy/keycloak/themes,
 * ADR-0010) and follow the app's light or dark choice (ADR-0021): Evening
 * Kitchen colors, the app's fonts, the brand, a theme toggle. Each page gets
 * a screenshot at phone and desktop width in the test's output folder.
 */

const PHONE = { width: 390, height: 844 };
const DESKTOP = { width: 1280, height: 900 };

const LIGHT_BG = "rgb(244, 238, 229)";
const DARK_BG = "rgb(25, 21, 18)";
const TOGGLE = { name: "Toggle light and dark mode" };

async function shoot(page: Page, testInfo: TestInfo, name: string): Promise<void> {
  for (const [label, size] of [["phone", PHONE], ["desktop", DESKTOP]] as const) {
    await page.setViewportSize(size);
    await page.screenshot({ path: testInfo.outputPath(`${name}-${label}.png`), fullPage: true });
  }
}

test("the sign-in, error, and register pages use the Mimos theme", async ({ page }, testInfo) => {
  await test.step("open the Keycloak sign-in page from the app", async () => {
    await page.goto("/app");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.locator("#kc-login")).toBeVisible();
  });

  await test.step("the theme's stylesheet, colors, and fonts are applied", async () => {
    await expect(page).toHaveTitle("Sign in to Mimos");
    await expect(page.locator("#kc-header-wrapper")).toHaveText("Mimos");
    await expect(page.locator("body")).toHaveCSS("background-color", LIGHT_BG);
    await expect(page.locator("#kc-login")).toHaveCSS("background-color", "rgb(148, 86, 15)");
    await expect(page.locator("#kc-login")).toHaveCSS("border-radius", "0px");
    await expect(page.locator("#kc-page-title")).toHaveCSS("font-family", /^Newsreader/);
    const loaded = await page.evaluate(async () => {
      // The harness compiles without the DOM lib; this runs in the page.
      type FontFace = { family: string; status: string };
      const { fonts } = (globalThis as unknown as { document: { fonts: Iterable<FontFace> & { ready: Promise<unknown> } } })
        .document;
      await fonts.ready;
      return [...fonts].filter((face) => face.status === "loaded").map((face) => face.family.replace(/"/g, ""));
    });
    expect(loaded).toEqual(expect.arrayContaining(["Newsreader", "Public Sans"]));
    await shoot(page, testInfo, "sign-in");
  });

  await test.step("a wrong password shows the error in --danger", async () => {
    await page.locator("#username").fill("test");
    await page.locator("#password").fill("not-the-password");
    await page.locator("#kc-login").click();
    const error = page.locator("#input-error-username");
    await expect(error).toBeVisible();
    await expect(error).toHaveCSS("color", "rgb(168, 74, 37)");
    await shoot(page, testInfo, "sign-in-error");
  });

  await test.step("the register page shares the theme", async () => {
    await page.getByRole("link", { name: "Register" }).click();
    await expect(page.locator("#kc-page-title")).toHaveText("Register");
    await expect(page.locator("#kc-page-title")).toHaveCSS("font-family", /^Newsreader/);
    await expect(page.locator("body")).toHaveCSS("background-color", LIGHT_BG);
    await shoot(page, testInfo, "register");
  });

  await test.step("the Mimos brand links back to the app's home page", async () => {
    await page.locator("#kc-header-wrapper").getByRole("link", { name: "Mimos" }).click();
    await expect(page).toHaveURL("/");
    await expect(page.getByRole("button", { name: "Create account" }).first()).toBeVisible();
  });
});

test("the Keycloak pages follow the app's dark choice", async ({ page }, testInfo) => {
  await test.step("choose dark in the app, then sign in", async () => {
    await page.goto("/app");
    await page.getByRole("button", TOGGLE).click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.locator("#kc-login")).toBeVisible();
  });

  await test.step("the sign-in page is dark", async () => {
    await expect(page.locator("body")).toHaveCSS("background-color", DARK_BG);
    await expect(page.locator("#kc-login")).toHaveCSS("background-color", "rgb(233, 170, 80)");
    await shoot(page, testInfo, "sign-in-dark");
  });

  await test.step("a wrong password stays dark, with the error in the dark --danger", async () => {
    await page.locator("#username").fill("test");
    await page.locator("#password").fill("not-the-password");
    await page.locator("#kc-login").click();
    const error = page.locator("#input-error-username");
    await expect(error).toBeVisible();
    await expect(error).toHaveCSS("color", "rgb(217, 122, 79)");
    await expect(page.locator("body")).toHaveCSS("background-color", DARK_BG);
    await shoot(page, testInfo, "sign-in-error-dark");
  });

  await test.step("the register page, reached without the app's parameter, stays dark", async () => {
    await page.getByRole("link", { name: "Register" }).click();
    await expect(page.locator("#kc-page-title")).toHaveText("Register");
    expect(new URL(page.url()).searchParams.has("mimos_theme")).toBe(false);
    await expect(page.locator("body")).toHaveCSS("background-color", DARK_BG);
    await shoot(page, testInfo, "register-dark");
  });

  await test.step("register's validation errors stay dark", async () => {
    await page.getByRole("button", { name: "Register" }).click();
    await expect(page.locator("#input-error-username")).toBeVisible();
    await expect(page.locator("body")).toHaveCSS("background-color", DARK_BG);
    await shoot(page, testInfo, "register-error-dark");
  });

  await test.step("Keycloak's own toggle switches to light", async () => {
    await page.getByRole("button", TOGGLE).click();
    await expect(page.locator("body")).toHaveCSS("background-color", LIGHT_BG);
  });

  await test.step("the app's light choice replaces a dark one stored on Keycloak's pages", async () => {
    await page.getByRole("button", TOGGLE).click();
    await expect(page.locator("body")).toHaveCSS("background-color", DARK_BG);
    await page.goto("/app");
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await page.getByRole("button", TOGGLE).click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.locator("#kc-login")).toBeVisible();
    await expect(page.locator("body")).toHaveCSS("background-color", LIGHT_BG);
  });
});

test("Create account in dark opens a dark register page", async ({ page }, testInfo) => {
  await page.goto("/");
  await page.getByRole("button", TOGGLE).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.getByRole("button", { name: "Create account" }).first().click();
  await expect(page.locator("#kc-page-title")).toHaveText("Register");
  await expect(page.locator("body")).toHaveCSS("background-color", DARK_BG);
  await shoot(page, testInfo, "sign-up-dark");
});
