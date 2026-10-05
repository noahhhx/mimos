import { useState } from "react";

import { createIngredient, type CatalogIngredient, type NutritionBasis } from "@mimos/api-client";

import { apiClient } from "@/lib/api";

const BASES: { value: NutritionBasis; label: string }[] = [
  { value: "PER_100_G", label: "100 g" },
  { value: "PER_100_ML", label: "100 ml" },
  { value: "PER_PIECE", label: "One piece" },
];

const FIELDS = [
  { key: "calories", label: "Calories" },
  { key: "proteinG", label: "Protein g" },
  { key: "carbsG", label: "Carbs g" },
  { key: "fatG", label: "Fat g" },
] as const;

type Values = Record<(typeof FIELDS)[number]["key"], string>;

/**
 * Adds an ingredient of the user's own (ADR-0016) from inside the recipe
 * form: its name, what its nutrition is for, and the four values from its
 * label. It sits inside the recipe's form, so Enter saves the ingredient
 * rather than submitting the recipe.
 */
export function NewIngredient({
  initialName,
  initialBasis,
  onCreated,
  onCancel,
}: {
  initialName: string;
  initialBasis: NutritionBasis;
  onCreated: (entry: CatalogIngredient) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(initialName);
  const [basis, setBasis] = useState<NutritionBasis>(initialBasis);
  const [values, setValues] = useState<Values>({ calories: "", proteinG: "", carbsG: "", fatG: "" });
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);

  const save = async () => {
    const numbers = Object.fromEntries(FIELDS.map(({ key }) => [key, Number(values[key])]));
    if (name.trim() === "") {
      setError("Give the ingredient a name.");
      return;
    }
    if (FIELDS.some(({ key }) => values[key].trim() === "" || !(numbers[key] >= 0))) {
      setError("Fill in all four values, using 0 when there is none.");
      return;
    }
    setSaving(true);
    const result = await createIngredient({
      client: apiClient,
      body: { name: name.trim(), basis, nutrition: numbers },
    });
    setSaving(false);
    if (!result.data) {
      setError((result.error as { detail?: string } | undefined)?.detail ?? "The ingredient could not be saved.");
      return;
    }
    onCreated(result.data);
  };

  return (
    <div
      className="new-ingredient"
      role="group"
      aria-label="New ingredient"
      onKeyDown={(e) => {
        if (e.key === "Enter" && e.target instanceof HTMLInputElement) {
          e.preventDefault();
          void save();
        } else if (e.key === "Escape") {
          onCancel();
        }
      }}
    >
      <p className="muted">
        A new ingredient of your own. Copy its nutrition from the label; only you will see it.
      </p>
      {error && (
        <p className="error-text" role="alert">
          {error}
        </p>
      )}
      <div className="field-row">
        <label>
          Name
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} autoFocus />
        </label>
        <label>
          Nutrition per
          <select value={basis} onChange={(e) => setBasis(e.target.value as NutritionBasis)}>
            {BASES.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="field-row">
        {FIELDS.map(({ key, label }) => (
          <label key={key}>
            {label}
            <input
              type="number"
              min={0}
              step="any"
              value={values[key]}
              onChange={(e) => setValues((current) => ({ ...current, [key]: e.target.value }))}
            />
          </label>
        ))}
      </div>
      <p className="actions">
        <button type="button" className="button" onClick={() => void save()} disabled={saving}>
          {saving ? "Saving…" : "Save ingredient"}
        </button>
        <button type="button" className="button secondary" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
      </p>
    </div>
  );
}
