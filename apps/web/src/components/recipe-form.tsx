"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { createRecipe, replaceRecipe, type RecipeDetail } from "@mimos/api-client";

import { apiClient } from "@/lib/api";
import {
  EMPTY_INGREDIENT,
  formValuesOf,
  toRecipeInput,
  type IngredientRow,
  type RecipeFormValues,
} from "@/lib/recipe-input";

type TextField = Exclude<keyof RecipeFormValues, "ingredients" | "steps">;

function problemDetail(result: { error?: { detail?: string } | unknown }): string {
  const error = result.error as { detail?: string } | undefined;
  return error?.detail ?? "The API rejected the recipe.";
}

/**
 * Create/edit form for personal recipes. Same richness as the library:
 * ingredients, steps, times, tags, and per-serving nutrition. It is a real
 * form, so the browser checks each input's constraints and Enter submits.
 * Without `onSaved`, a save navigates to the recipe's page; an editor
 * already on that page passes `onSaved` instead, since navigating to the
 * current URL would leave it in edit mode.
 */
export function RecipeForm({
  initial,
  onSaved,
  onCancel,
}: {
  initial?: RecipeDetail;
  onSaved?: (recipe: RecipeDetail) => void;
  onCancel?: () => void;
}) {
  const router = useRouter();
  const [values, setValues] = useState<RecipeFormValues>(() => formValuesOf(initial));
  const [errors, setErrors] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  const set = (patch: Partial<RecipeFormValues>) => setValues((current) => ({ ...current, ...patch }));
  const field = (name: TextField) => ({
    value: values[name],
    onChange: (e: { target: { value: string } }) => set({ [name]: e.target.value }),
  });

  const save = async () => {
    const checked = toRecipeInput(values);
    if ("errors" in checked) {
      setErrors(checked.errors);
      return;
    }
    setSaving(true);
    setErrors([]);
    const result = initial
      ? await replaceRecipe({ client: apiClient, path: { recipeId: initial.id }, body: checked.input })
      : await createRecipe({ client: apiClient, body: checked.input });
    setSaving(false);
    if (result.error || !result.data) {
      setErrors([problemDetail(result)]);
      return;
    }
    if (onSaved) {
      onSaved(result.data);
    } else {
      router.push(`/app/recipes/${result.data.id}`);
    }
  };

  return (
    <form
      className="card form"
      onSubmit={(e) => {
        e.preventDefault();
        void save();
      }}
    >
      {errors.length > 0 && (
        <div className="card error" role="alert">
          {errors.map((error) => (
            <p key={error}>{error}</p>
          ))}
        </div>
      )}

      <label>
        Title
        <input {...field("title")} required maxLength={200} />
      </label>
      <label>
        Description
        <textarea {...field("description")} rows={2} required maxLength={2000} />
      </label>
      <div className="field-row">
        <label>
          Servings
          <input type="number" min={1} max={50} step={1} {...field("servings")} required />
        </label>
        <label>
          Prep minutes
          <input type="number" min={0} max={1440} step={1} {...field("prepMinutes")} />
        </label>
        <label>
          Cook minutes
          <input type="number" min={0} max={1440} step={1} {...field("cookMinutes")} />
        </label>
      </div>
      <label>
        Tags (comma-separated)
        <input {...field("tags")} placeholder="dinner, vegetarian" />
      </label>

      <h2>Nutrition per serving (optional)</h2>
      <div className="field-row">
        <label>
          Calories
          <input type="number" min={0} step="any" {...field("calories")} />
        </label>
        <label>
          Protein g
          <input type="number" min={0} step="any" {...field("proteinG")} />
        </label>
        <label>
          Carbs g
          <input type="number" min={0} step="any" {...field("carbsG")} />
        </label>
        <label>
          Fat g
          <input type="number" min={0} step="any" {...field("fatG")} />
        </label>
      </div>

      <h2>Ingredients</h2>
      <p className="muted">Leave the amount blank for ingredients you don&apos;t measure, like salt to taste.</p>
      {values.ingredients.map((row, index) => (
        <div className="field-row ingredient-row" key={index}>
          <label>
            Amount
            <input
              type="number"
              min={0}
              step="any"
              value={row.quantity}
              onChange={(e) => updateIngredient(index, { quantity: e.target.value })}
              placeholder="to taste"
              aria-label={`Ingredient ${index + 1} amount`}
            />
          </label>
          <label>
            Unit
            <input
              value={row.unit}
              onChange={(e) => updateIngredient(index, { unit: e.target.value })}
              placeholder="cups"
              maxLength={30}
              aria-label={`Ingredient ${index + 1} unit`}
            />
          </label>
          <label>
            Name
            <input
              value={row.name}
              onChange={(e) => updateIngredient(index, { name: e.target.value })}
              placeholder="flour"
              maxLength={200}
              aria-label={`Ingredient ${index + 1} name`}
            />
          </label>
          <button type="button" className="button secondary" onClick={() => removeRow("ingredient", index)}>
            Remove
          </button>
        </div>
      ))}
      <button
        type="button"
        className="button secondary"
        onClick={() => set({ ingredients: [...values.ingredients, { ...EMPTY_INGREDIENT }] })}
      >
        + Ingredient
      </button>

      <h2>Steps</h2>
      {values.steps.map((step, index) => (
        <div className="field-row step-row" key={index}>
          <label>
            Step {index + 1}
            <textarea
              value={step}
              onChange={(e) => set({ steps: values.steps.map((s, i) => (i === index ? e.target.value : s)) })}
              rows={2}
              maxLength={4000}
            />
          </label>
          <button type="button" className="button secondary" onClick={() => removeRow("step", index)}>
            Remove
          </button>
        </div>
      ))}
      <button type="button" className="button secondary" onClick={() => set({ steps: [...values.steps, ""] })}>
        + Step
      </button>

      <p className="actions">
        <button type="submit" className="button" disabled={saving}>
          {saving ? "Saving…" : initial ? "Save changes" : "Create recipe"}
        </button>
        {onCancel && (
          <button type="button" className="button secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </button>
        )}
      </p>
    </form>
  );

  function updateIngredient(index: number, patch: Partial<IngredientRow>) {
    set({ ingredients: values.ingredients.map((row, i) => (i === index ? { ...row, ...patch } : row)) });
  }

  function removeRow(kind: "ingredient" | "step", index: number) {
    if (kind === "ingredient") {
      set({ ingredients: values.ingredients.filter((_, i) => i !== index) });
    } else {
      set({ steps: values.steps.filter((_, i) => i !== index) });
    }
  }
}
