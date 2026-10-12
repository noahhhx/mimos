import { crc32, deflateSync } from "node:zlib";

import { expect, test } from "./fixtures.ts";

/** A real PNG of one color, which any browser decodes. */
function png(width: number, height: number, [red, green, blue]: [number, number, number]): Buffer {
  const chunk = (type: string, data: Buffer): Buffer => {
    const typed = Buffer.concat([Buffer.from(type, "latin1"), data]);
    const framed = Buffer.alloc(8 + typed.length);
    framed.writeUInt32BE(data.length, 0);
    typed.copy(framed, 4);
    framed.writeUInt32BE(crc32(typed), 4 + typed.length);
    return framed;
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // bits per channel
  header[9] = 2; // truecolor, no alpha
  const line = Buffer.alloc(1 + width * 3);
  for (let x = 0; x < width; x++) {
    line.set([red, green, blue], 1 + x * 3);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(Buffer.concat(Array.from({ length: height }, () => line)))),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

const naturalWidth = (img: unknown) => (img as { naturalWidth: number }).naturalWidth;

/**
 * Add two pictures to a new recipe, see them load in one row above the
 * description that scrolls sideways on a phone without the page doing so,
 * then cancel a removal and save one (ADR-0023). Each picture is shrunk
 * and re-encoded as JPEG in the browser before it is sent.
 */
test("adds pictures to a recipe, then removes one", async ({ loggedInPage: page }) => {
  // Unique per run, so reruns against the same database never collide.
  const title = `Harness pictures ${new Date().toISOString()}`;
  const recipe = page.locator("article.recipe");
  const row = recipe.locator(".picture-row");
  const formError = page.locator("main").getByRole("alert");
  // The API serves a picture as the type it read from the stored bytes.
  const servedTypes = new Set<string>();
  page.on("response", (response) => {
    if (/\/api\/v1\/recipes\/[^/]+\/pictures\/[^/]+$/.test(response.url()) && response.status() === 200) {
      servedTypes.add(response.headers()["content-type"] ?? "none");
    }
  });

  await test.step("create a recipe with two pictures", async () => {
    await page.goto("/app/recipes/new");
    const pictures = [
      { name: "terracotta.png", mimeType: "image/png", buffer: png(2400, 1800, [192, 122, 46]) },
      { name: "olive.png", mimeType: "image/png", buffer: png(800, 600, [107, 122, 58]) },
    ];
    await page.getByLabel("Title").fill(title);
    await page.getByLabel("Description").fill("Written by the harness's recipe-pictures scenario.");
    await page.getByLabel("Ingredient 1 name").fill("paprika");
    await page.getByLabel("Step 1").fill("Arrange on the plate.");
    await page.getByLabel("Add pictures").setInputFiles(pictures);
    await expect(page.getByText("Not saved yet")).toHaveCount(2);
    await page.getByRole("button", { name: "Create recipe" }).click();
    await expect(formError.or(recipe.getByRole("heading", { name: title, level: 1 }))).toBeVisible();
    expect(await formError.allInnerTexts(), "the form reported an error").toEqual([]);
  });

  await test.step("both pictures load in one row before the description", async () => {
    await expect(row.locator("img")).toHaveCount(2);
    for (const image of await row.locator("img").all()) {
      await expect.poll(() => image.evaluate(naturalWidth)).toBeGreaterThan(0);
    }
    const mainColumn = await recipe
      .locator(".split-main > *")
      .evaluateAll((children) => children.map((child) => (child as { className: string }).className));
    expect(mainColumn.slice(0, 2), "the pictures come before the description").toEqual(["picture-row", "lede"]);
    expect([...servedTypes], "each PNG was re-encoded as JPEG before upload").toEqual(["image/jpeg"]);
    const largest = await row.locator("img").first().evaluate(naturalWidth);
    expect(largest, "the 2400px picture was shrunk to 1600px").toBe(1600);
  });

  await test.step("on a phone the row scrolls sideways and the page does not", async () => {
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(row.locator("img")).toHaveCount(2);
    const widths = await page.locator("html").evaluate((html) => ({
      page: html.scrollWidth,
      viewport: html.clientWidth,
    }));
    expect(widths.page, "the page is no wider than the screen").toBeLessThanOrEqual(widths.viewport);
    const scrolls = await row.evaluate((element) => element.scrollWidth > element.clientWidth);
    expect(scrolls, "the row scrolls inside its own box").toBe(true);
    await page.setViewportSize({ width: 1280, height: 800 });
  });

  await test.step("cancelling an edit keeps both pictures", async () => {
    await page.getByRole("button", { name: "Edit" }).click();
    await page.getByRole("button", { name: "Remove picture 1" }).click();
    await expect(page.getByRole("button", { name: /^Remove picture/ })).toHaveCount(1);
    await page.getByRole("button", { name: "Cancel" }).click();
    await expect(row.locator("img")).toHaveCount(2);
  });

  await test.step("removing one and saving keeps the other, after a reload too", async () => {
    await page.getByRole("button", { name: "Edit" }).click();
    await page.getByRole("button", { name: "Remove picture 1" }).click();
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(formError.or(recipe.getByRole("heading", { name: title, level: 1 }))).toBeVisible();
    expect(await formError.allInnerTexts(), "the form reported an error").toEqual([]);
    await expect(row.locator("img")).toHaveCount(1);
    await page.reload();
    await expect(row.locator("img")).toHaveCount(1);
    await expect.poll(() => row.locator("img").evaluate(naturalWidth)).toBe(800);
  });
});
