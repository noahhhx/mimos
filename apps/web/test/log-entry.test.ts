import assert from "node:assert/strict";
import { describe, it } from "node:test";

import type { CatalogIngredient, RecipeSummary } from "@mimos/api-client";

import {
  EMPTY_NUTRITION,
  defaultAmount,
  describeIngredient,
  describeTotal,
  logBody,
  measuredTotal,
  parseServings,
  recipeTotal,
  searchWhat,
  typeWhat,
  unitsFor,
  whatText,
  type AmountFields,
  type LogWhat,
} from "../src/lib/log-entry.ts";

const recipe = (patch: Partial<RecipeSummary> = {}): RecipeSummary => ({
  id: "r-dahl",
  title: "Red Lentil Dahl",
  description: "",
  servings: 4,
  isLibrary: true,
  tags: [],
  nutrition: { calories: 450, proteinG: 20, carbsG: 61, fatG: 12 },
  ...patch,
});

const DAHL: LogWhat = { kind: "recipe", recipe: recipe() };

const ingredient = (patch: Partial<CatalogIngredient> = {}): CatalogIngredient => ({
  slug: "sugar",
  name: "Sugar",
  isShared: true,
  basis: "PER_100_G",
  nutrition: { calories: 400, proteinG: 0, carbsG: 100, fatG: 0 },
  ...patch,
});

const SUGAR = ingredient();
const OLIVE_OIL = ingredient({
  slug: "olive-oil",
  name: "Olive oil",
  basis: "PER_100_ML",
  nutrition: { calories: 900, proteinG: 0, carbsG: 0, fatG: 100 },
});
const BANANA = ingredient({
  slug: "banana",
  name: "Banana",
  basis: "PER_PIECE",
  nutrition: { calories: 105, proteinG: 1.3, carbsG: 27, fatG: 0.4 },
});

const ANY_AMOUNT: AmountFields = { quantity: "1", unit: "g" };

const ingredientBody = (entry: CatalogIngredient, quantity: string, unit: AmountFields["unit"]) =>
  logBody({ kind: "ingredient", entry }, "2026-10-07", "SNACK", "1", EMPTY_NUTRITION, { quantity, unit });

describe("logBody", () => {
  it("logs a recipe by its id and servings, leaving nutrition to the API", () => {
    assert.deepEqual(
      logBody(DAHL, "2026-10-07", "LUNCH", "1.5", { calories: "999", proteinG: "1", carbsG: "", fatG: "" }, ANY_AMOUNT),
      { date: "2026-10-07", mealType: "LUNCH", recipeId: "r-dahl", servings: 1.5 },
    );
  });

  it("logs anything else as one serving of what was typed, with the nutrition given", () => {
    assert.deepEqual(
      logBody(
        typeWhat("  Toast with peanut butter "),
        "2026-10-07",
        "SNACK",
        "3",
        { calories: "310", proteinG: "11.5", carbsG: "", fatG: " " },
        ANY_AMOUNT,
      ),
      {
        date: "2026-10-07",
        mealType: "SNACK",
        description: "Toast with peanut butter",
        servings: 1,
        nutrition: { calories: 310, proteinG: 11.5, carbsG: undefined, fatG: undefined },
      },
    );
  });

  it("logs nothing without a description", () => {
    assert.equal(logBody(typeWhat(""), "2026-10-07", "SNACK", "1", EMPTY_NUTRITION, ANY_AMOUNT), undefined);
    assert.equal(
      logBody(typeWhat("   "), "2026-10-07", "SNACK", "1", { ...EMPTY_NUTRITION, calories: "100" }, ANY_AMOUNT),
      undefined,
    );
  });

  it("logs no recipe without servings the API accepts", () => {
    for (const servings of ["0", "-1", "100.1", "abc", "", " ", "Infinity", "NaN"]) {
      assert.equal(logBody(DAHL, "2026-10-07", "DINNER", servings, EMPTY_NUTRITION, ANY_AMOUNT), undefined, servings);
    }
    assert.equal(logBody(DAHL, "2026-10-07", "DINNER", "100", EMPTY_NUTRITION, ANY_AMOUNT)?.servings, 100);
    assert.equal(logBody(DAHL, "2026-10-07", "DINNER", "0.1", EMPTY_NUTRITION, ANY_AMOUNT)?.servings, 0.1);
  });
});

describe("parseServings", () => {
  it("reads more than 0, up to 100", () => {
    assert.equal(parseServings("2"), 2);
    assert.equal(parseServings("0.5"), 0.5);
    assert.equal(parseServings("0"), undefined);
    assert.equal(parseServings("101"), undefined);
  });
});

describe("typing and picking", () => {
  it("shows a picked recipe's title", () => {
    assert.equal(whatText(DAHL), "Red Lentil Dahl");
  });

  it("unpicks the recipe when its title is typed over, keeping the text as typed", () => {
    assert.deepEqual(typeWhat("Red Lentil Dah"), { kind: "custom", description: "Red Lentil Dah" });
    // A trailing space must survive, or no second word can be typed.
    assert.equal(whatText(typeWhat("Red Lentil ")), "Red Lentil ");
  });
});

describe("recipeTotal and describeTotal", () => {
  it("scales a serving by the servings logged, to 0.1 as the API stores it", () => {
    assert.deepEqual(recipeTotal(recipe(), 1.5), { calories: 675, proteinG: 30, carbsG: 91.5, fatG: 18 });
    assert.deepEqual(recipeTotal(recipe(), 0.3), { calories: 135, proteinG: 6, carbsG: 18.3, fatG: 3.6 });
  });

  it("says what the servings come to, in whole numbers", () => {
    assert.equal(describeTotal(recipeTotal(recipe(), 1.5)), "Comes to 675 kcal, 30 g protein, 92 g carbs, 18 g fat.");
    assert.equal(
      describeTotal(recipeTotal(recipe({ nutrition: { calories: 333.33 } }), 1)),
      "Comes to 333 kcal.",
    );
  });

  it("knows nothing about a recipe whose nutrition is unknown", () => {
    assert.equal(recipeTotal(recipe({ nutrition: {} }), 2), undefined);
    assert.equal(describeTotal(undefined), "Nutrition unknown, so it adds nothing.");
  });
});

describe("logging an ingredient", () => {
  it("logs one serving named with its amount, carrying the nutrition the form previewed", () => {
    assert.deepEqual(ingredientBody(SUGAR, "15", "g"), {
      date: "2026-10-07",
      mealType: "SNACK",
      description: "Sugar, 15 g",
      servings: 1,
      nutrition: { calories: 60, proteinG: 0, carbsG: 15, fatG: 0 },
    });
    assert.equal(ingredientBody(OLIVE_OIL, "1", "tbsp")?.description, "Olive oil, 1 tbsp");
    assert.equal(ingredientBody(BANANA, "2", "")?.description, "Banana, 2 pieces");
    assert.equal(ingredientBody(BANANA, "1", "")?.description, "Banana, 1 piece");
    assert.equal(ingredientBody(BANANA, "0.5", "")?.description, "Banana, 0.5 pieces");
  });

  it("logs nothing until the amount is more than 0", () => {
    for (const quantity of ["", " ", "0", "-5", "abc", "Infinity", "NaN"]) {
      assert.equal(ingredientBody(SUGAR, quantity, "g"), undefined, quantity);
    }
  });

  it("logs nothing in a unit the ingredient is not counted in", () => {
    assert.equal(ingredientBody(SUGAR, "1", "tbsp"), undefined);
    assert.equal(ingredientBody(BANANA, "100", "g"), undefined);
  });

  it("keeps a long name's description within the 200 characters the API takes, amount included", () => {
    const description = ingredientBody(ingredient({ name: "x".repeat(200) }), "250", "g")?.description ?? "";
    assert.equal(description.length, 200);
    assert.ok(description.endsWith("x, 250 g"), description);
  });
});

describe("measuredTotal", () => {
  it("counts each unit by its size against the basis", () => {
    assert.deepEqual(measuredTotal(SUGAR, 15, "g"), { calories: 60, proteinG: 0, carbsG: 15, fatG: 0 });
    assert.deepEqual(measuredTotal(SUGAR, 0.5, "kg"), { calories: 2000, proteinG: 0, carbsG: 500, fatG: 0 });
    assert.deepEqual(measuredTotal(OLIVE_OIL, 1, "tbsp"), { calories: 135, proteinG: 0, carbsG: 0, fatG: 15 });
    assert.deepEqual(measuredTotal(OLIVE_OIL, 2, "tsp"), { calories: 90, proteinG: 0, carbsG: 0, fatG: 10 });
    assert.deepEqual(measuredTotal(OLIVE_OIL, 250, "ml"), { calories: 2250, proteinG: 0, carbsG: 0, fatG: 250 });
    assert.deepEqual(measuredTotal(OLIVE_OIL, 0.1, "l"), { calories: 900, proteinG: 0, carbsG: 0, fatG: 100 });
    assert.deepEqual(measuredTotal(BANANA, 2, ""), { calories: 210, proteinG: 2.6, carbsG: 54, fatG: 0.8 });
  });

  it("rounds to 0.1", () => {
    assert.equal(measuredTotal(ingredient({ nutrition: { calories: 333 } }), 1, "g")?.calories, 3.3);
    assert.equal(measuredTotal(BANANA, 1.5, "")?.proteinG, 2);
  });

  it("leaves a value the ingredient lacks absent, not zero", () => {
    assert.deepEqual(measuredTotal(ingredient({ nutrition: { calories: 400, carbsG: 100 } }), 15, "g"), {
      calories: 60,
      proteinG: undefined,
      carbsG: 15,
      fatG: undefined,
    });
    assert.equal(measuredTotal(ingredient({ nutrition: {} }), 15, "g"), undefined);
    assert.deepEqual(ingredientBody(ingredient({ nutrition: {} }), "15", "g")?.nutrition, {});
  });

  it("counts nothing in a unit of another basis", () => {
    assert.equal(measuredTotal(SUGAR, 1, "ml"), undefined);
    assert.equal(measuredTotal(OLIVE_OIL, 1, "g"), undefined);
    assert.equal(measuredTotal(BANANA, 1, "kg"), undefined);
  });

  it("previews as a recipe's total does", () => {
    assert.equal(
      describeTotal(measuredTotal(OLIVE_OIL, 1, "tbsp")),
      "Comes to 135 kcal, 0 g protein, 0 g carbs, 15 g fat.",
    );
  });
});

describe("units and amounts", () => {
  it("offers only the units of the ingredient's basis", () => {
    assert.deepEqual(unitsFor("PER_100_G"), ["g", "kg"]);
    assert.deepEqual(unitsFor("PER_100_ML"), ["ml", "l", "tsp", "tbsp"]);
    assert.deepEqual(unitsFor("PER_PIECE"), [""]);
  });

  it("starts at 100 g, 100 ml, or 1 piece", () => {
    assert.deepEqual(defaultAmount("PER_100_G"), { quantity: "100", unit: "g" });
    assert.deepEqual(defaultAmount("PER_100_ML"), { quantity: "100", unit: "ml" });
    assert.deepEqual(defaultAmount("PER_PIECE"), { quantity: "1", unit: "" });
  });
});

describe("searchWhat", () => {
  const BREAD = recipe({ id: "r-bread", title: "Banana bread" });

  it("ranks recipes and ingredients together, names that start with the text first", () => {
    const options = searchWhat("banana", [BREAD, recipe()], [SUGAR, BANANA]);
    assert.deepEqual(options.map(whatText), ["Banana", "Banana bread"]);
    assert.deepEqual(options.map((option) => option.kind), ["ingredient", "recipe"]);
  });

  it("offers at most the limit, and nothing before anything is typed", () => {
    const many = Array.from({ length: 10 }, (_, index) => ingredient({ slug: `s${index}`, name: `Sugar ${index}` }));
    assert.equal(searchWhat("sugar", [], many).length, 8);
    assert.deepEqual(searchWhat("", [recipe()], [SUGAR]), []);
  });

  it("shows a picked ingredient's name", () => {
    assert.equal(whatText({ kind: "ingredient", entry: SUGAR }), "Sugar");
  });

  it("says what an ingredient option is, and whose", () => {
    assert.equal(describeIngredient(SUGAR), "Ingredient · 400 kcal per 100 g");
    assert.equal(describeIngredient(ingredient({ isShared: false })), "Your ingredient · 400 kcal per 100 g");
    assert.equal(describeIngredient(BANANA), "Ingredient · 105 kcal each");
  });
});
