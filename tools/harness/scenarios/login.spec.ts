import { expect, test } from "./fixtures.ts";

/** Smoke for the login fixture: sign in through Keycloak and see the profile the API returned. */
test("signs in and shows the profile", async ({ loggedInPage: page, user }) => {
  const toggle = page.getByRole("banner").getByRole("button", { name: "Profile" });
  await toggle.click();
  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  // The profile's display name is the Keycloak username.
  await expect(page.locator(".profile-name")).toHaveText(user);
  await expect(page.getByText(/^Member since /)).toBeVisible();
  // Scoped to the page: Next.js's route announcer is an alert too.
  await expect(page.locator("header, main").getByRole("alert")).toHaveCount(0);

  await page.keyboard.press("Escape");
  await expect(toggle).toHaveAttribute("aria-expanded", "false");
  await expect(toggle).toBeFocused();

  await toggle.click();
  await page.getByRole("link", { name: "Your data" }).click();
  await expect(page.getByRole("heading", { name: "Your data", level: 1 })).toBeVisible();
  await expect(toggle).toHaveAttribute("aria-expanded", "false");
});
