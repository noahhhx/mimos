import type { NutritionEstimateInput, NutritionSource, RecipeDetail, RecipeInput } from "@mimos/api-client";

/**
 * The recipe form's values as typed (every field a string), and their
 * translation into the API's `RecipeInput`. The browser enforces each
 * input's own constraints (required, min/max, whole numbers); this checks
 * what spans fields — ingredient and step rows — so the form can name the
 * row at fault instead of passing on the API's whole-recipe message.
 */

/** `catalogSlug` is the catalog entry the line counts as for calculated nutrition (ADR-0015). */
export type IngredientRow = { quantity: string; unit: string; name: string; catalogSlug?: string };

export type RecipeFormValues = {
  title: string;
  description: string;
  servings: string;
  prepMinutes: string;
  cookMinutes: string;
  tags: string;
  nutritionSource: NutritionSource;
  calories: string;
  proteinG: string;
  carbsG: string;
  fatG: string;
  ingredients: IngredientRow[];
  steps: string[];
};

export const EMPTY_INGREDIENT: IngredientRow = { quantity: "", unit: "", name: "" };

export function formValuesOf(recipe?: RecipeDetail): RecipeFormValues {
  const text = (value: number | null | undefined) => (value == null ? "" : String(value));
  return {
    title: recipe?.title ?? "",
    description: recipe?.description ?? "",
    servings: recipe ? String(recipe.servings) : "4",
    prepMinutes: text(recipe?.prepMinutes),
    cookMinutes: text(recipe?.cookMinutes),
    tags: recipe?.tags.join(", ") ?? "",
    nutritionSource: recipe?.nutritionSource ?? "INGREDIENTS",
    calories: text(recipe?.nutrition?.calories),
    proteinG: text(recipe?.nutrition?.proteinG),
    carbsG: text(recipe?.nutrition?.carbsG),
    fatG: text(recipe?.nutrition?.fatG),
    ingredients: recipe?.ingredients.length
      ? recipe.ingredients.map((i) => ({
          quantity: text(i.quantity),
          unit: i.unit ?? "",
          name: i.name,
          ...(i.catalogSlug ? { catalogSlug: i.catalogSlug } : {}),
        }))
      : [{ ...EMPTY_INGREDIENT }],
    steps: recipe?.steps.length ? recipe.steps.map((s) => s.instruction) : [""],
  };
}

/** The `RecipeInput` for these values, or every problem with them, one per row at fault. */
export function toRecipeInput(values: RecipeFormValues): { input: RecipeInput } | { errors: string[] } {
  const errors: string[] = [];
  if (values.title.trim() === "") {
    errors.push("Give the recipe a title.");
  }
  if (values.description.trim() === "") {
    errors.push("Give the recipe a description.");
  }

  const ingredients: RecipeInput["ingredients"] = [];
  values.ingredients.forEach((row, index) => {
    const name = row.name.trim();
    const unit = row.unit.trim();
    const amount = row.quantity.trim();
    if (name === "" && unit === "" && amount === "") {
      return; // An untouched row, not an ingredient.
    }
    const label = `Ingredient ${index + 1}`;
    if (name === "") {
      errors.push(`${label} needs a name.`);
    }
    const quantity = amount === "" ? undefined : Number(amount);
    if (quantity !== undefined && !(Number.isFinite(quantity) && quantity > 0)) {
      errors.push(`${label}'s amount must be more than 0, or leave it blank for "to taste".`);
    }
    ingredients.push({
      quantity,
      unit: unit === "" ? undefined : unit,
      name,
      ...(row.catalogSlug ? { catalogSlug: row.catalogSlug } : {}),
    });
  });
  if (ingredients.length === 0) {
    errors.push("Add at least one ingredient.");
  }

  const steps = values.steps
    .map((step) => step.trim())
    .filter((step) => step !== "")
    .map((instruction) => ({ instruction }));
  if (steps.length === 0) {
    errors.push("Add at least one step.");
  }

  if (errors.length > 0) {
    return { errors };
  }
  return {
    input: {
      title: values.title.trim(),
      description: values.description.trim(),
      servings: Number(values.servings),
      prepMinutes: optionalNumber(values.prepMinutes),
      cookMinutes: optionalNumber(values.cookMinutes),
      tags: [
        ...new Set(
          values.tags
            .split(",")
            .map((tag) => tag.trim().toLowerCase())
            .filter((tag) => tag !== ""),
        ),
      ],
      nutrition: {
        calories: optionalNumber(values.calories),
        proteinG: optionalNumber(values.proteinG),
        carbsG: optionalNumber(values.carbsG),
        fatG: optionalNumber(values.fatG),
      },
      nutritionSource: values.nutritionSource,
      ingredients,
      steps,
    },
  };
}

/**
 * What the nutrition estimate needs from the form, and which form row each
 * line came from; nothing until servings is a whole number from 1 to 50.
 * Rows without a name and amounts that are not positive numbers are left
 * out, so a half-typed row never makes the estimate fail.
 */
export function toEstimateInput(values: RecipeFormValues): { input: NutritionEstimateInput; rows: number[] } | undefined {
  const servings = Number(values.servings);
  if (!Number.isInteger(servings) || servings < 1 || servings > 50) {
    return undefined;
  }
  const rows: number[] = [];
  const ingredients: NutritionEstimateInput["ingredients"] = [];
  values.ingredients.forEach((row, index) => {
    const name = row.name.trim();
    const amount = row.quantity.trim();
    const quantity = amount === "" ? undefined : Number(amount);
    if (name === "" || (quantity !== undefined && !(Number.isFinite(quantity) && quantity > 0))) {
      return;
    }
    rows.push(index);
    ingredients.push({
      quantity,
      unit: row.unit.trim() === "" ? undefined : row.unit.trim(),
      name,
      ...(row.catalogSlug ? { catalogSlug: row.catalogSlug } : {}),
    });
  });
  return { input: { servings, ingredients }, rows };
}

function optionalNumber(value: string): number | undefined {
  const trimmed = value.trim();
  if (trimmed === "") {
    return undefined;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}
