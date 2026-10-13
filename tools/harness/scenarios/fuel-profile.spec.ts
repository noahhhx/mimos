import { apiAs, expect, register, test } from "./fixtures.ts";

test("a person saves their fuel profile", async ({ page }) => {
  await register(page, `fuel-${Date.now().toString(36)}`);

  const enabled = page.getByRole("checkbox", { name: "Show fuel targets" });
  const weight = page.getByRole("spinbutton", { name: "Weight (kg)" });
  const bodyFat = page.getByRole("spinbutton", { name: "Body fat (%)" });
  const ftp = page.getByRole("spinbutton", { name: "FTP (watts)" });
  const protein = page.getByRole("spinbutton", { name: "Protein (g per kg)" });
  const save = page.getByRole("button", { name: "Save" });

  await test.step("Fuel opens from the profile menu with targets off", async () => {
    const banner = page.getByRole("banner");
    await banner.getByRole("button", { name: "Profile" }).click();
    await banner.getByRole("link", { name: "Fuel", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Fuel", level: 1 })).toBeVisible();
    await expect(enabled).not.toBeChecked();
    await expect(weight).toHaveValue("");
    await expect(page.getByRole("radio", { name: "Fuel my training" })).toBeChecked();
    await expect(protein).toHaveValue("1.6");
  });

  await test.step("a saved profile survives a reload and matches the API", async () => {
    await enabled.check();
    await weight.fill("70");
    await page.getByRole("radio", { name: "Male", exact: true }).check();
    await ftp.fill("280");
    await page.getByRole("radio", { name: "Fuel my training" }).check();
    await save.click();
    await expect(page.getByRole("form", { name: "Fuel profile" }).getByRole("status")).toHaveText("Saved.");

    await page.reload();
    await expect(enabled).toBeChecked();
    await expect(weight).toHaveValue("70");
    await expect(page.getByRole("radio", { name: "Male", exact: true })).toBeChecked();
    await expect(bodyFat).toHaveValue("");
    await expect(ftp).toHaveValue("280");
    await expect(page.getByRole("radio", { name: "Fuel my training" })).toBeChecked();
    await expect(protein).toHaveValue("1.6");

    const api = await apiAs(page);
    expect(await api("GET", "/api/v1/me/fuel-profile")).toEqual({
      enabled: true,
      weightKg: 70,
      sex: "MALE",
      ftpWatts: 280,
      goal: "FUEL",
      proteinGPerKg: 1.6,
    });
  });

  await test.step("protein takes each goal's default, and an edit to it is kept", async () => {
    await page.getByRole("radio", { name: "Lose weight faster" }).check();
    await expect(protein).toHaveValue("2.2");
    await protein.fill("2.4");
    await save.click();
    await expect(page.getByRole("form", { name: "Fuel profile" }).getByRole("status")).toHaveText("Saved.");
    await page.reload();
    await expect(page.getByRole("radio", { name: "Lose weight faster" })).toBeChecked();
    await expect(protein).toHaveValue("2.4");
  });

  await test.step("each value out of bounds gets its own error and nothing is saved", async () => {
    await weight.fill("10");
    await bodyFat.fill("80");
    await protein.fill("4");
    const answer = page.waitForResponse(
      (response) => response.url().endsWith("/api/v1/me/fuel-profile") && response.request().method() === "PUT",
    );
    await save.click();
    const response = await answer;
    expect(response.status()).toBe(400);
    expect(response.headers()["content-type"]).toContain("application/problem+json");
    await expect(page.getByText("Enter a weight from 30 to 250 kg.")).toBeVisible();
    await expect(page.getByText("Enter body fat from 3 to 60%, or leave it empty.")).toBeVisible();
    await expect(page.getByText("Enter protein from 1.2 to 3.0 g per kg.")).toBeVisible();
    await expect(weight).toHaveAttribute("aria-invalid", "true");
    await expect(ftp).not.toHaveAttribute("aria-invalid", "true");

    await page.reload();
    await expect(weight).toHaveValue("70");
    await expect(bodyFat).toHaveValue("");
    await expect(protein).toHaveValue("2.4");
  });

  await test.step("the plan and the log both show each day's fuel", async () => {
    const nav = page.getByRole("navigation", { name: "App" });
    await nav.getByRole("link", { name: "Plan" }).click();
    await expect(page.getByRole("group", { name: /^Fuel for / })).toHaveCount(7);
    await nav.getByRole("link", { name: "Log" }).click();
    await expect(page.getByRole("heading", { name: "Log", level: 1 })).toBeVisible();
    await expect(page.locator("table.totals tr.fuel-row")).toHaveCount(7);
  });
});
