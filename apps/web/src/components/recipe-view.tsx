"use client";

import { useState } from "react";

import type { RecipeDetail } from "@mimos/api-client";

import { formatQuantity } from "@/lib/format";

/**
 * A recipe as you cook it: tickable ingredients and steps (local state only
 * — the flour-on-your-hands view), with nutrition per serving.
 */
export function RecipeView({ recipe }: { recipe: RecipeDetail }) {
  const [doneIngredients, setDoneIngredients] = useState<Set<number>>(new Set());
  const [doneSteps, setDoneSteps] = useState<Set<number>>(new Set());

  const toggle = (set: Set<number>, index: number, apply: (next: Set<number>) => void) => {
    const next = new Set(set);
    if (next.has(index)) {
      next.delete(index);
    } else {
      next.add(index);
    }
    apply(next);
  };

  return (
    <article className="recipe">
      <header className="page-header">
        <h1>{recipe.title}</h1>
        <p className="lede">{recipe.description}</p>
        <p className="muted">
          Serves {recipe.servings}
          {recipe.prepMinutes != null ? ` · ${recipe.prepMinutes} min prep` : ""}
          {recipe.cookMinutes != null ? ` · ${recipe.cookMinutes} min cook` : ""}
        </p>
        {recipe.tags.length > 0 && (
          <p className="tags">
            {recipe.tags.map((tag) => (
              <span key={tag} className="tag">
                {tag}
              </span>
            ))}
          </p>
        )}
      </header>

      {recipe.nutrition && (recipe.nutrition.calories != null || recipe.nutrition.proteinG != null) && (
        <div className="card nutrition">
          <h2>Per serving</h2>
          <dl className="profile">
            <dt>Calories</dt>
            <dd>{recipe.nutrition.calories != null ? `${Math.round(recipe.nutrition.calories)} kcal` : "—"}</dd>
            <dt>Protein</dt>
            <dd>{recipe.nutrition.proteinG != null ? `${Math.round(recipe.nutrition.proteinG)} g` : "—"}</dd>
            <dt>Carbs</dt>
            <dd>{recipe.nutrition.carbsG != null ? `${Math.round(recipe.nutrition.carbsG)} g` : "—"}</dd>
            <dt>Fat</dt>
            <dd>{recipe.nutrition.fatG != null ? `${Math.round(recipe.nutrition.fatG)} g` : "—"}</dd>
          </dl>
        </div>
      )}

      <div className="recipe-columns">
        <section className="card">
          <h2>Ingredients</h2>
          <ul className="ingredients cook">
            {recipe.ingredients.map((ingredient, index) => (
              <li key={index}>
                <label className={doneIngredients.has(index) ? "done" : undefined}>
                  <input
                    type="checkbox"
                    checked={doneIngredients.has(index)}
                    onChange={() => toggle(doneIngredients, index, setDoneIngredients)}
                  />{" "}
                  <span className="quantity">{formatQuantity(ingredient.quantity, ingredient.unit)}</span>{" "}
                  {ingredient.name}
                </label>
              </li>
            ))}
          </ul>
        </section>

        <section className="card">
          <h2>Steps</h2>
          <ol className="steps cook">
            {recipe.steps.map((step, index) => (
              <li key={index} className={doneSteps.has(index) ? "done" : undefined}>
                <label>
                  <input
                    type="checkbox"
                    checked={doneSteps.has(index)}
                    onChange={() => toggle(doneSteps, index, setDoneSteps)}
                  />{" "}
                  {step.instruction}
                </label>
              </li>
            ))}
          </ol>
        </section>
      </div>
    </article>
  );
}
