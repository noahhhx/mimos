"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

import {
  createRecipe,
  estimateRecipeNutrition,
  listIngredients,
  replaceRecipe,
  type CatalogIngredient,
  type IngredientLineStatus,
  type Nutrition,
  type RecipeDetail,
} from "@mimos/api-client";

import { PerServing } from "@/components/per-serving";
import { apiClient } from "@/lib/api";
import { lineHint, suggestCatalogSlug } from "@/lib/catalog-match";
import {
  EMPTY_INGREDIENT,
  formValuesOf,
  toEstimateInput,
  toRecipeInput,
  type IngredientRow,
  type RecipeFormValues,
} from "@/lib/recipe-input";

type TextField = Exclude<keyof RecipeFormValues, "ingredients" | "steps" | "nutritionSource">;

/** The calculated nutrition for the form as it stands, with a status per form row that was sent. */
type Estimate = { nutrition: Nutrition; statuses: Map<number, IngredientLineStatus> } | "failed";

const METRIC_UNITS = ["g", "kg", "ml", "l", "tsp", "tbsp"];

function problemDetail(result: { error?: { detail?: string } | unknown }): string {
  const error = result.error as { detail?: string } | undefined;
  return error?.detail ?? "The API rejected the recipe.";
}

/**
 * Create/edit form for personal recipes. Same richness as the library:
 * ingredients, steps, times, tags, and per-serving nutrition, either
 * typed in or calculated from what each ingredient counts as in the
 * shared catalog (ADR-0015), live as the author types. It is a real
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
  const [catalog, setCatalog] = useState<CatalogIngredient[]>([]);
  const [estimate, setEstimate] = useState<Estimate>();
  const calculated = values.nutritionSource === "INGREDIENTS";
  const bySlug = useMemo(() => new Map(catalog.map((entry) => [entry.slug, entry])), [catalog]);
  const estimateRequest = useMemo(() => (calculated ? toEstimateInput(values) : undefined), [calculated, values]);
  const estimateKey = estimateRequest ? JSON.stringify(estimateRequest.input) : undefined;

  useEffect(() => {
    void listIngredients({ client: apiClient }).then((result) => setCatalog(result.data ?? []));
  }, []);

  useEffect(() => {
    if (!estimateRequest) {
      setEstimate(undefined);
      return;
    }
    let current = true;
    const timer = setTimeout(() => {
      void estimateRecipeNutrition({ client: apiClient, body: estimateRequest.input }).then((result) => {
        if (!current) {
          return;
        }
        if (!result.data) {
          setEstimate("failed");
          return;
        }
        const statuses = new Map<number, IngredientLineStatus>();
        result.data.lines.forEach((status, line) => statuses.set(estimateRequest.rows[line], status));
        setEstimate({ nutrition: result.data.nutrition, statuses });
      });
    }, 300);
    return () => {
      current = false;
      clearTimeout(timer);
    };
    // estimateKey stands for estimateRequest: refetch when what is sent changes, not on every keystroke elsewhere.
  }, [estimateKey]);

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

      <h2>Ingredients</h2>
      <p className="muted">
        Leave the amount blank for ingredients you don&apos;t measure, like salt to taste. To count an ingredient
        toward nutrition, pick what it counts as and measure it in grams, millilitres, spoons, or pieces.
      </p>
      <datalist id="metric-units">
        {METRIC_UNITS.map((unit) => (
          <option key={unit} value={unit} />
        ))}
      </datalist>
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
              list="metric-units"
              maxLength={30}
              aria-label={`Ingredient ${index + 1} unit`}
            />
          </label>
          <label>
            Name
            <input
              value={row.name}
              onChange={(e) => renameIngredient(index, e.target.value)}
              placeholder="flour"
              maxLength={200}
              aria-label={`Ingredient ${index + 1} name`}
            />
          </label>
          <label>
            Counts as
            <select
              value={row.catalogSlug ?? ""}
              onChange={(e) => updateIngredient(index, { catalogSlug: e.target.value })}
              aria-label={`Ingredient ${index + 1} counts as`}
            >
              <option value="">Not counted</option>
              {catalog.map((entry) => (
                <option key={entry.slug} value={entry.slug}>
                  {entry.name}
                </option>
              ))}
            </select>
          </label>
          <button type="button" className="button secondary" onClick={() => removeRow("ingredient", index)}>
            Remove
          </button>
          {calculated && estimate !== "failed" && (
            <IngredientHint
              hint={lineHint(estimate?.statuses.get(index), row.catalogSlug ? bySlug.get(row.catalogSlug) : undefined)}
            />
          )}
        </div>
      ))}
      <button
        type="button"
        className="button secondary"
        onClick={() => set({ ingredients: [...values.ingredients, { ...EMPTY_INGREDIENT }] })}
      >
        + Ingredient
      </button>

      <h2>Nutrition per serving</h2>
      <div className="choices" role="radiogroup" aria-label="Nutrition">
        <label>
          <input
            type="radio"
            name="nutritionSource"
            checked={calculated}
            onChange={() => set({ nutritionSource: "INGREDIENTS" })}
          />
          Calculate from ingredients
        </label>
        <label>
          <input
            type="radio"
            name="nutritionSource"
            checked={!calculated}
            onChange={() => set({ nutritionSource: "MANUAL" })}
          />
          Enter it myself
        </label>
      </div>
      {calculated ? (
        <CalculatedNutrition estimate={estimate} sent={estimateRequest?.rows.length ?? 0} />
      ) : (
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
      )}

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

  /** A renamed line follows the catalog suggestion, unless its author picked what it counts as. */
  function renameIngredient(index: number, name: string) {
    const row = values.ingredients[index];
    const followsSuggestion =
      row.catalogSlug === undefined || row.catalogSlug === suggestCatalogSlug(row.name, catalog);
    updateIngredient(index, followsSuggestion ? { name, catalogSlug: suggestCatalogSlug(name, catalog) } : { name });
  }

  function removeRow(kind: "ingredient" | "step", index: number) {
    if (kind === "ingredient") {
      set({ ingredients: values.ingredients.filter((_, i) => i !== index) });
    } else {
      set({ steps: values.steps.filter((_, i) => i !== index) });
    }
  }
}

function IngredientHint({ hint }: { hint: string | undefined }) {
  return hint ? <p className="muted ingredient-hint">{hint}</p> : null;
}

function CalculatedNutrition({ estimate, sent }: { estimate: Estimate | undefined; sent: number }) {
  if (estimate === "failed") {
    return <p className="muted">Nutrition could not be calculated right now. It is calculated again when you save.</p>;
  }
  const counted = estimate ? [...estimate.statuses.values()].filter((status) => status === "COUNTED").length : 0;
  return (
    <>
      <PerServing nutrition={estimate?.nutrition ?? {}} />
      <p className="muted">
        {counted === 0
          ? "Nothing counts yet. Pick what each ingredient counts as."
          : `Counting ${counted} of ${sent} ingredients.`}
      </p>
    </>
  );
}
