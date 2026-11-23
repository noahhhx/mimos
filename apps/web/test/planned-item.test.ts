import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import type { CatalogIngredient, FoodProduct } from "@mimos/api-client";

import {
  amountText,
  entryLabel,
  entryName,
  existingIngredient,
  foodLogBody,
  plannedWhat,
  productIngredient,
} from "../src/lib/planned-item.ts";

const stew = { recipeId: "r1", recipeTitle: "Lentil stew", servings: 4 };
const banana = { food: { catalogSlug: "banana", name: "Banana", unit: undefined }, servings: 1 };
const bagel = { food: { catalogSlug: "bagel-x", name: "Bagel", unit: "g" }, servings: 85 };

const bananaEntry: CatalogIngredient = {
  slug: "banana",
  name: "Banana",
  isShared: true,
  basis: "PER_PIECE",
  nutrition: { calories: 105, proteinG: 1.3, carbsG: 27, fatG: 0.4 },
};
const bagelEntry: CatalogIngredient = {
  slug: "bagel-x",
  name: "Bagel",
  isShared: false,
  basis: "PER_100_G",
  nutrition: { calories: 270, proteinG: 10, carbsG: 53, fatG: 1.6 },
};
// The app's commonTest reads the same file (libraries/test-cases/README.md).
const barcodeCases = JSON.parse(
  readFileSync(new URL("../../../libraries/test-cases/barcode.json", import.meta.url), "utf8"),
) as {
  product: FoodProduct;
  productIngredient: ReturnType<typeof productIngredient>;
  nameLimit: { length: number; limit: number };
  existingIngredientCases: { about: string; isShared: boolean; calories: number; found: boolean }[];
};

describe("entryLabel", () => {
  it("is a recipe's title", () => {
    assert.equal(entryLabel(stew), "Lentil stew");
  });

  it("is a piece's name and count, with no unit", () => {
    assert.equal(entryLabel(banana), "Banana, 1");
    assert.equal(entryLabel({ ...banana, servings: 2 }), "Banana, 2");
  });

  it("is a measured food's name, amount, and unit", () => {
    assert.equal(entryLabel(bagel), "Bagel, 85 g");
  });
});

describe("entryName", () => {
  it("names the recipe or the food", () => {
    assert.equal(entryName(stew), "Lentil stew");
    assert.equal(entryName(bagel), "Bagel");
  });
});

describe("plannedWhat", () => {
  it("tells a recipe from a food", () => {
    assert.deepEqual(plannedWhat(stew), { kind: "recipe", recipeId: "r1", title: "Lentil stew" });
    assert.equal(plannedWhat(banana).kind, "food");
  });

  it("refuses an entry that names neither", () => {
    assert.throws(() => plannedWhat({}), /recipe or a food/);
  });
});

describe("productIngredient", () => {
  const { product, productIngredient: expected, nameLimit, existingIngredientCases } = barcodeCases;

  it("makes an ingredient of a product with all four values, named with its brand", () => {
    assert.deepEqual(productIngredient(product), expected);
  });

  it("needs the person for a product missing a value", () => {
    assert.equal(productIngredient({ ...product, nutrition: { ...product.nutrition, fatG: undefined } }), undefined);
  });

  it("keeps a name within the ingredient name limit", () => {
    assert.equal(productIngredient({ ...product, name: "x".repeat(nameLimit.length) })?.name.length, nameLimit.limit);
  });

  for (const each of existingIngredientCases) {
    it(each.about, () => {
      const input = productIngredient(product)!;
      const own: CatalogIngredient = {
        slug: "nutella-k3",
        name: input.name,
        isShared: each.isShared,
        basis: input.basis,
        nutrition: { ...input.nutrition, calories: each.calories },
      };
      assert.equal(existingIngredient(input, [own]), each.found ? own : undefined);
    });
  }
});

describe("user-facing copy", () => {
  it("has no em dash", () => {
    const samples = [
      entryLabel(banana),
      entryLabel(bagel),
      amountText(banana),
      amountText(bagel),
      foodLogBody({ date: "2026-11-16", mealType: "SNACK", food: bagel.food }, [bagelEntry], 85)?.description ?? "",
    ];
    for (const sample of samples) {
      assert.ok(!sample.includes("\u2014"), sample);
    }
  });
});
