import { expect, test } from "./fixtures.ts";

/** Smoke for the login fixture: sign in through Keycloak and see the profile the API returned. */
test("signs in and shows the profile", async ({ loggedInPage: page, user }) => {
  await expect(page.getByRole("heading", { name: "Profile" })).toBeVisible();
  // The profile's display name is the Keycloak username.
  await expect(page.locator("dl.profile").getByRole("definition").first()).toHaveText(user);
  // Scoped to <main>: Next.js's route announcer is an alert too.
  await expect(page.locator("main").getByRole("alert")).toHaveCount(0);
});
