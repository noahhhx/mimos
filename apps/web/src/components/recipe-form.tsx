"use client";

import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

import {
  createRecipe,
  estimateRecipeNutrition,
  listIngredients,
  listMyRecipes,
  replaceRecipe,
  type CatalogIngredient,
  type IngredientLineStatus,
  type Nutrition,
  type NutritionBasis,
  type RecipeDetail,
  type RecipeSummary,
} from "@mimos/api-client";

import { IngredientPicker } from "@/components/ingredient-picker";
import { NewIngredient } from "@/components/new-ingredient";
import { PerServing } from "@/components/per-serving";
import { apiClient } from "@/lib/api";
import { basisForUnit } from "@/lib/catalog-match";
import {
  describeLink,
  dontCount,
  lineHint,
  linkOf,
  pickEntry,
  pickRecipe,
  retype,
  type LineLink,
} from "@/lib/ingredient-links";
import {
  EMPTY_INGREDIENT,
  formValuesOf,
  toEstimateInput,
  toRecipeInput,
  unitChoices,
  type IngredientRow,
  type RecipeFormValues,
} from "@/lib/recipe-input";

type TextField = Exclude<keyof RecipeFormValues, "ingredients" | "steps" | "nutritionSource">;

/** The calculated nutrition for the form as it stands, with a status per form row that was sent. */
type Estimate = { nutrition: Nutrition; statuses: Map<number, IngredientLineStatus> } | "failed";


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
  const [recipes, setRecipes] = useState<RecipeSummary[]>([]);
  const [estimate, setEstimate] = useState<Estimate>();
  const [adding, setAdding] = useState<{ index: number; name: string; basis: NutritionBasis }>();
  const calculated = values.nutritionSource === "INGREDIENTS";
  const bySlug = useMemo(() => new Map(catalog.map((entry) => [entry.slug, entry])), [catalog]);
  const byRecipeId = useMemo(() => new Map(recipes.map((recipe) => [recipe.id, recipe])), [recipes]);
  const estimateRequest = useMemo(() => (calculated ? toEstimateInput(values) : undefined), [calculated, values]);
  const estimateKey = estimateRequest ? JSON.stringify(estimateRequest.input) : undefined;

  useEffect(() => {
    void listIngredients({ client: apiClient }).then((result) => setCatalog(result.data ?? []));
    // A recipe never offers itself as something it can use.
    void listMyRecipes({ client: apiClient }).then((result) =>
      setRecipes((result.data ?? []).filter((recipe) => recipe.id !== initial?.id)),
    );
  }, [initial?.id]);

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
        Search for each ingredient or one of your recipes, or add your own ingredient when it isn&apos;t there.
        Measure it in grams, millilitres, spoons, or pieces (a recipe in servings) to count it toward nutrition, and
        leave the amount blank for things like salt to taste.
      </p>
      {values.ingredients.map((row, index) => (
        <div className="field-row ingredient-row" key={index}>
          <label className="narrow">
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
          <label className="narrow">
            Unit
            <select
              value={row.unit}
              onChange={(e) => updateIngredient(index, { unit: e.target.value })}
              aria-label={`Ingredient ${index + 1} unit`}
            >
              {unitChoices(row.unit, Boolean(row.recipeId)).map((unit) => (
                <option key={unit} value={unit}>
                  {unit === "" ? "pieces" : unit}
                </option>
              ))}
            </select>
          </label>
          <IngredientPicker
            label={`Ingredient ${index + 1} name`}
            name={row.name}
            catalog={catalog}
            recipes={recipes}
            onType={(name) => replaceRow(index, retype(row, name, catalog, bySlug, byRecipeId))}
            onPick={(entry) => replaceRow(index, pickEntry(row, entry))}
            onPickRecipe={(recipe) => replaceRow(index, pickRecipe(row, recipe))}
            onAdd={(name) => setAdding({ index, name, basis: basisForUnit(row.unit) })}
          />
          <label>
            Note
            <input
              value={row.note ?? ""}
              onChange={(e) => updateIngredient(index, { note: e.target.value })}
              placeholder="minced"
              maxLength={200}
              aria-label={`Ingredient ${index + 1} note`}
            />
          </label>
          <button type="button" className="button secondary" onClick={() => removeRow("ingredient", index)}>
            Remove
          </button>
          <MatchedTo link={linkOf(row, bySlug, byRecipeId)} onUnlink={() => replaceRow(index, dontCount(row))} />
          {calculated && estimate !== "failed" && (
            <IngredientHint hint={lineHint(estimate?.statuses.get(index), linkOf(row, bySlug, byRecipeId))} />
          )}
          {adding?.index === index && (
            <NewIngredient
              initialName={adding.name}
              initialBasis={adding.basis}
              onCreated={(entry) => {
                setCatalog((current) => [...current, entry].sort((a, b) => a.name.localeCompare(b.name)));
                replaceRow(index, pickEntry(row, entry));
                setAdding(undefined);
              }}
              onCancel={() => setAdding(undefined)}
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
    replaceRow(index, { ...values.ingredients[index], ...patch });
  }

  function replaceRow(index: number, next: IngredientRow) {
    set({ ingredients: values.ingredients.map((row, i) => (i === index ? next : row)) });
  }

  function removeRow(kind: "ingredient" | "step", index: number) {
    if (kind === "ingredient") {
      set({ ingredients: values.ingredients.filter((_, i) => i !== index) });
    } else {
      set({ steps: values.steps.filter((_, i) => i !== index) });
    }
  }
}

/** What a linked line counts as, with a way to stop counting it. */
function MatchedTo({ link, onUnlink }: { link: LineLink | undefined; onUnlink: () => void }) {
  if (!link) {
    return null;
  }
  return (
    <p className="ingredient-hint muted">
      {describeLink(link)}{" "}
      <button type="button" className="button secondary small" onClick={onUnlink}>
        Don&apos;t count it
      </button>
    </p>
  );
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
