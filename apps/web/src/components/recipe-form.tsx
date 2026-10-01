"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { createRecipe, replaceRecipe, type RecipeDetail, type RecipeInput } from "@mimos/api-client";

import { apiClient } from "@/lib/api";

type IngredientRow = { quantity: string; unit: string; name: string };

const EMPTY_INGREDIENT: IngredientRow = { quantity: "", unit: "", name: "" };

function problemDetail(result: { error?: { detail?: string } | unknown }): string {
  const error = result.error as { detail?: string } | undefined;
  return error?.detail ?? "The API rejected the recipe.";
}

/**
 * Create/edit form for personal recipes. Same richness as the library:
 * ingredients, steps, times, tags, and per-serving nutrition. Without
 * `onSaved`, a save navigates to the recipe's page; an editor already on
 * that page passes `onSaved` instead, since navigating to the current URL
 * would leave it in edit mode.
 */
export function RecipeForm({
  initial,
  onSaved,
}: {
  initial?: RecipeDetail;
  onSaved?: (recipe: RecipeDetail) => void;
}) {
  const router = useRouter();
  const [title, setTitle] = useState(initial?.title ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [servings, setServings] = useState(initial?.servings?.toString() ?? "4");
  const [prepMinutes, setPrepMinutes] = useState(initial?.prepMinutes?.toString() ?? "");
  const [cookMinutes, setCookMinutes] = useState(initial?.cookMinutes?.toString() ?? "");
  const [tags, setTags] = useState(initial?.tags.join(", ") ?? "");
  const [calories, setCalories] = useState(initial?.nutrition?.calories?.toString() ?? "");
  const [proteinG, setProteinG] = useState(initial?.nutrition?.proteinG?.toString() ?? "");
  const [carbsG, setCarbsG] = useState(initial?.nutrition?.carbsG?.toString() ?? "");
  const [fatG, setFatG] = useState(initial?.nutrition?.fatG?.toString() ?? "");
  const [ingredients, setIngredients] = useState<IngredientRow[]>(
    initial?.ingredients.length
      ? initial.ingredients.map((i) => ({
          quantity: i.quantity?.toString() ?? "",
          unit: i.unit ?? "",
          name: i.name,
        }))
      : [{ ...EMPTY_INGREDIENT }],
  );
  const [steps, setSteps] = useState<string[]>(
    initial?.steps.length ? initial.steps.map((s) => s.instruction) : [""],
  );
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const numberOrNull = (value: string): number | undefined => {
    const trimmed = value.trim();
    if (trimmed === "") {
      return undefined;
    }
    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : undefined;
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    const input: RecipeInput = {
      title: title.trim(),
      description: description.trim(),
      servings: Number(servings),
      prepMinutes: numberOrNull(prepMinutes),
      cookMinutes: numberOrNull(cookMinutes),
      tags: tags
        .split(",")
        .map((tag) => tag.trim().toLowerCase())
        .filter((tag) => tag !== ""),
      nutrition: {
        calories: numberOrNull(calories),
        proteinG: numberOrNull(proteinG),
        carbsG: numberOrNull(carbsG),
        fatG: numberOrNull(fatG),
      },
      ingredients: ingredients
        .filter((row) => row.name.trim() !== "")
        .map((row) => ({
          quantity: row.quantity.trim() === "" ? undefined : Number(row.quantity),
          unit: row.unit.trim() === "" ? undefined : row.unit.trim(),
          name: row.name.trim(),
        })),
      steps: steps
        .map((step) => step.trim())
        .filter((step) => step !== "")
        .map((instruction) => ({ instruction })),
    };

    const result = initial
      ? await replaceRecipe({ client: apiClient, path: { recipeId: initial.id }, body: input })
      : await createRecipe({ client: apiClient, body: input });
    setSaving(false);
    if (result.error || !result.data) {
      setError(problemDetail(result));
      return;
    }
    if (onSaved) {
      onSaved(result.data);
    } else {
      router.push(`/app/recipes/${result.data.id}`);
    }
  };

  return (
    <div className="card form">
      {error && (
        <div className="card error" role="alert">
          <p>{error}</p>
        </div>
      )}

      <label>
        Title
        <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} />
      </label>
      <label>
        Description
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          maxLength={2000}
        />
      </label>
      <div className="field-row">
        <label>
          Servings
          <input
            type="number"
            min={1}
            max={50}
            value={servings}
            onChange={(e) => setServings(e.target.value)}
            required
          />
        </label>
        <label>
          Prep minutes
          <input
            type="number"
            min={0}
            max={1440}
            value={prepMinutes}
            onChange={(e) => setPrepMinutes(e.target.value)}
          />
        </label>
        <label>
          Cook minutes
          <input
            type="number"
            min={0}
            max={1440}
            value={cookMinutes}
            onChange={(e) => setCookMinutes(e.target.value)}
          />
        </label>
      </div>
      <label>
        Tags (comma-separated)
        <input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="dinner, vegetarian" />
      </label>

      <h2>Nutrition per serving (optional)</h2>
      <div className="field-row">
        <label>
          Calories
          <input type="number" min={0} step="any" value={calories} onChange={(e) => setCalories(e.target.value)} />
        </label>
        <label>
          Protein g
          <input type="number" min={0} step="any" value={proteinG} onChange={(e) => setProteinG(e.target.value)} />
        </label>
        <label>
          Carbs g
          <input type="number" min={0} step="any" value={carbsG} onChange={(e) => setCarbsG(e.target.value)} />
        </label>
        <label>
          Fat g
          <input type="number" min={0} step="any" value={fatG} onChange={(e) => setFatG(e.target.value)} />
        </label>
      </div>

      <h2>Ingredients</h2>
      {ingredients.map((row, index) => (
        <div className="field-row ingredient-row" key={index}>
          <label>
            Amount
            <input
              type="number"
              min={0}
              step="any"
              value={row.quantity}
              onChange={(e) => updateIngredient(index, { quantity: e.target.value })}
              aria-label={`Ingredient ${index + 1} amount`}
            />
          </label>
          <label>
            Unit
            <input
              value={row.unit}
              onChange={(e) => updateIngredient(index, { unit: e.target.value })}
              placeholder="cups"
              aria-label={`Ingredient ${index + 1} unit`}
            />
          </label>
          <label>
            Name
            <input
              value={row.name}
              onChange={(e) => updateIngredient(index, { name: e.target.value })}
              placeholder="flour"
              aria-label={`Ingredient ${index + 1} name`}
            />
          </label>
          <button type="button" className="button secondary" onClick={() => removeRow("ingredient", index)}>
            Remove
          </button>
        </div>
      ))}
      <button type="button" className="button secondary" onClick={() => setIngredients([...ingredients, { ...EMPTY_INGREDIENT }])}>
        + Ingredient
      </button>

      <h2>Steps</h2>
      {steps.map((step, index) => (
        <div className="field-row step-row" key={index}>
          <label>
            Step {index + 1}
            <textarea
              value={step}
              onChange={(e) => {
                const next = [...steps];
                next[index] = e.target.value;
                setSteps(next);
              }}
              rows={2}
            />
          </label>
          <button type="button" className="button secondary" onClick={() => removeRow("step", index)}>
            Remove
          </button>
        </div>
      ))}
      <button type="button" className="button secondary" onClick={() => setSteps([...steps, ""])}>
        + Step
      </button>

      <p>
        <button className="button" onClick={() => void save()} disabled={saving || title.trim() === ""}>
          {saving ? "Saving…" : initial ? "Save changes" : "Create recipe"}
        </button>
      </p>
    </div>
  );

  function updateIngredient(index: number, patch: Partial<IngredientRow>) {
    setIngredients(ingredients.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  function removeRow(kind: "ingredient" | "step", index: number) {
    if (kind === "ingredient") {
      setIngredients(ingredients.filter((_, i) => i !== index));
    } else {
      setSteps(steps.filter((_, i) => i !== index));
    }
  }
}
