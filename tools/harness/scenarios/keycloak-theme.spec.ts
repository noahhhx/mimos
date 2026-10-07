import { expect, test } from "./fixtures.ts";

/**
 * The Keycloak pages wear the Mimos login theme (deploy/keycloak/themes,
 * ADR-0010): Evening Kitchen colors, the app's fonts, the brand. Each page
 * gets a screenshot at phone and desktop width in the test's output folder.
 */

const PHONE = { width: 390, height: 844 };
const DESKTOP = { width: 1280, height: 900 };

test("the sign-in, error, and register pages use the Mimos theme", async ({ page }, testInfo) => {
  const shoot = async (name: string): Promise<void> => {
    for (const [label, size] of [["phone", PHONE], ["desktop", DESKTOP]] as const) {
      await page.setViewportSize(size);
      await page.screenshot({ path: testInfo.outputPath(`${name}-${label}.png`), fullPage: true });
    }
  };

  await test.step("open the Keycloak sign-in page from the app", async () => {
    await page.goto("/app");
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.locator("#kc-login")).toBeVisible();
  });

  await test.step("the theme's stylesheet, colors, and fonts are applied", async () => {
    await expect(page).toHaveTitle("Sign in to Mimos");
    await expect(page.locator("#kc-header-wrapper")).toHaveText("Mimos");
    await expect(page.locator("body")).toHaveCSS("background-color", "rgb(244, 238, 229)");
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
    await shoot("sign-in");
  });

  await test.step("a wrong password shows the error in --danger", async () => {
    await page.locator("#username").fill("test");
    await page.locator("#password").fill("not-the-password");
    await page.locator("#kc-login").click();
    const error = page.locator("#input-error-username");
    await expect(error).toBeVisible();
    await expect(error).toHaveCSS("color", "rgb(168, 74, 37)");
    await shoot("sign-in-error");
  });

  await test.step("the register page shares the theme", async () => {
    await page.getByRole("link", { name: "Register" }).click();
    await expect(page.locator("#kc-page-title")).toHaveText("Register");
    await expect(page.locator("#kc-page-title")).toHaveCSS("font-family", /^Newsreader/);
    await shoot("register");
  });

  await test.step("the Mimos brand links back to the app's home page", async () => {
    await page.locator("#kc-header-wrapper").getByRole("link", { name: "Mimos" }).click();
    await expect(page).toHaveURL("/");
    await expect(page.getByRole("button", { name: "Create account" }).first()).toBeVisible();
  });
});
