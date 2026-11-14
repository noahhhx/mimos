import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { CatalogIngredient, FoodProduct } from "@mimos/api-client";

import {
  amountText,
  entryLabel,
  entryName,
  existingIngredient,
  foodLogBody,
  newFoodEntry,
  parseFoodAmount,
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

describe("entryName and amountText", () => {
  it("name the recipe or the food", () => {
    assert.equal(entryName(stew), "Lentil stew");
    assert.equal(entryName(bagel), "Bagel");
  });

  it("count servings, pieces, or a unit", () => {
    assert.equal(amountText(stew), "4 servings");
    assert.equal(amountText({ ...stew, servings: 1 }), "1 serving");
    assert.equal(amountText(banana), "1 piece");
    assert.equal(amountText({ ...banana, servings: 3 }), "3 pieces");
    assert.equal(amountText(bagel), "85 g");
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

describe("newFoodEntry", () => {
  const food = (basis: CatalogIngredient["basis"], servingSize?: number) => ({ slug: "x", basis, servingSize });

  it("starts at 100 g, 100 ml, or 1 piece by basis", () => {
    assert.deepEqual(newFoodEntry(food("PER_100_G"), "SNACK", 1), {
      food: { catalogSlug: "x", unit: "g" },
      servings: 100,
    });
    assert.deepEqual(newFoodEntry(food("PER_100_ML"), "SNACK", 1), {
      food: { catalogSlug: "x", unit: "ml" },
      servings: 100,
    });
    assert.deepEqual(newFoodEntry(food("PER_PIECE"), "SNACK", 1), { food: { catalogSlug: "x" }, servings: 1 });
  });

  it("starts at one serving when the product says what that is", () => {
    assert.equal(newFoodEntry(food("PER_100_G", 28.5), "SNACK", 1).servings, 28.5);
  });

  it("plans one portion for each default diner", () => {
    assert.equal(newFoodEntry(food("PER_100_G"), "DINNER", 3).servings, 300);
    assert.equal(newFoodEntry(food("PER_100_G"), "LUNCH", 3).servings, 100);
    assert.equal(newFoodEntry(food("PER_PIECE"), "DINNER", 2).servings, 2);
  });

  it("plans one portion in a household of one, not a recipe's two servings", () => {
    assert.equal(newFoodEntry(food("PER_100_G"), "DINNER", 1).servings, 100);
  });

  it("keeps a serving's multiple free of float noise", () => {
    assert.equal(newFoodEntry(food("PER_100_G", 0.1), "DINNER", 3).servings, 0.3);
  });
});

describe("parseFoodAmount", () => {
  it("takes more than 0 and at most 10,000", () => {
    assert.equal(parseFoodAmount("85"), 85);
    assert.equal(parseFoodAmount("0.5"), 0.5);
    assert.equal(parseFoodAmount("10000"), 10000);
  });

  it("refuses the rest", () => {
    for (const text of ["", "  ", "0", "-1", "10001", "abc"]) {
      assert.equal(parseFoodAmount(text), undefined, text);
    }
  });
});

describe("foodLogBody", () => {
  const catalog = [bananaEntry, bagelEntry];
  const slot = { date: "2026-11-16", mealType: "SNACK" as const };

  it("logs a shared food's share as an ad-hoc meal with the calculated nutrition", () => {
    assert.deepEqual(foodLogBody({ ...slot, food: bagel.food }, catalog, 42.5), {
      date: "2026-11-16",
      mealType: "SNACK",
      description: "Bagel, 42.5 g",
      servings: 1,
      nutrition: { calories: 114.8, proteinG: 4.3, carbsG: 22.5, fatG: 0.7 },
    });
  });

  it("logs pieces", () => {
    const body = foodLogBody({ ...slot, food: banana.food }, catalog, 1);
    assert.equal(body?.description, "Banana, 1 piece");
    assert.equal(body?.nutrition?.calories, 105);
  });

  it("logs nothing for an ingredient the catalog lacks or a unit that does not fit", () => {
    assert.equal(foodLogBody({ ...slot, food: bagel.food }, [bananaEntry], 85), undefined);
    assert.equal(foodLogBody({ ...slot, food: { ...bagel.food, unit: "ml" } }, catalog, 85), undefined);
  });
});

describe("productIngredient", () => {
  const product: FoodProduct = {
    barcode: "3017620422003",
    name: "Nutella",
    brand: "Ferrero",
    basis: "PER_100_G",
    nutrition: { calories: 539, proteinG: 6.3, carbsG: 57.5, fatG: 30.9 },
  };

  it("makes an ingredient of a product with all four values, named with its brand", () => {
    assert.deepEqual(productIngredient(product), {
      name: "Nutella (Ferrero)",
      basis: "PER_100_G",
      nutrition: product.nutrition,
    });
  });

  it("needs the person for a product missing a value", () => {
    assert.equal(productIngredient({ ...product, nutrition: { ...product.nutrition, fatG: undefined } }), undefined);
  });

  it("keeps a name within the ingredient name limit", () => {
    assert.equal(productIngredient({ ...product, name: "x".repeat(150) })?.name.length, 100);
  });

  it("finds the household's own ingredient it already made, and no other", () => {
    const input = productIngredient(product)!;
    const own = { slug: "nutella-k3", name: "Nutella (Ferrero)", isShared: false, basis: input.basis, nutrition: input.nutrition };
    assert.equal(existingIngredient(input, [own]), own);
    assert.equal(existingIngredient(input, [{ ...own, isShared: true }]), undefined);
    assert.equal(existingIngredient(input, [{ ...own, nutrition: { ...own.nutrition, calories: 540 } }]), undefined);
  });
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
