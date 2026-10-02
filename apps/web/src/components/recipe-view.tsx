"use client";

import { useState } from "react";

import type { RecipeDetail } from "@mimos/api-client";

import { Quantity } from "@/components/quantity";
import { SplitPage } from "@/components/split-page";
import { hasNutrition } from "@/lib/format";

/**
 * A recipe as you cook it, for library and personal recipes alike. The
 * rail holds what you gather (tickable ingredients, nutrition per serving);
 * the main column holds the method, whose steps are ticked off by tapping
 * them. Ticks are local state only — the flour-on-your-hands view.
 * `children` go under the method.
 */
export function RecipeView({ recipe, children }: { recipe: RecipeDetail; children?: React.ReactNode }) {
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

  const rail = (
    <>
      <p className="muted recipe-meta">
        Serves {recipe.servings}
        {recipe.prepMinutes != null ? ` · ${recipe.prepMinutes} min prep` : ""}
        {recipe.cookMinutes != null ? ` · ${recipe.cookMinutes} min cook` : ""}
      </p>
      <header className="page-header">
        <h1>{recipe.title}</h1>
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

      <section>
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
                <Quantity quantity={ingredient.quantity} unit={ingredient.unit} /> {ingredient.name}
              </label>
            </li>
          ))}
        </ul>
      </section>

      {hasNutrition(recipe.nutrition) && (
        <section className="nutrition">
          <h2>Per serving</h2>
          <dl className="per-serving">
            <div>
              <dt>Calories</dt>
              <dd>{recipe.nutrition.calories != null ? `${Math.round(recipe.nutrition.calories)} kcal` : "—"}</dd>
            </div>
            <div>
              <dt>Protein</dt>
              <dd>{recipe.nutrition.proteinG != null ? `${Math.round(recipe.nutrition.proteinG)} g` : "—"}</dd>
            </div>
            <div>
              <dt>Carbs</dt>
              <dd>{recipe.nutrition.carbsG != null ? `${Math.round(recipe.nutrition.carbsG)} g` : "—"}</dd>
            </div>
            <div>
              <dt>Fat</dt>
              <dd>{recipe.nutrition.fatG != null ? `${Math.round(recipe.nutrition.fatG)} g` : "—"}</dd>
            </div>
          </dl>
        </section>
      )}
    </>
  );

  return (
    <article className="recipe">
      <SplitPage rail={rail}>
        <p className="lede">{recipe.description}</p>
        <section>
          <h2>Method</h2>
          <ol className="steps cook">
            {recipe.steps.map((step, index) => (
              <li key={index} className={doneSteps.has(index) ? "done" : undefined}>
                <label>
                  <input
                    type="checkbox"
                    className="visually-hidden"
                    checked={doneSteps.has(index)}
                    onChange={() => toggle(doneSteps, index, setDoneSteps)}
                  />
                  {step.instruction}
                </label>
              </li>
            ))}
          </ol>
        </section>
        {children}
      </SplitPage>
    </article>
  );
}
